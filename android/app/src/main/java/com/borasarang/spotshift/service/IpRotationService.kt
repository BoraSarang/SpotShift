package com.borasarang.spotshift.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.MainActivity
import com.borasarang.spotshift.R
import com.borasarang.spotshift.core.AirplaneController
import com.borasarang.spotshift.core.DataController
import com.borasarang.spotshift.core.HotspotController
import com.borasarang.spotshift.core.IpVerifier
import com.borasarang.spotshift.core.RotationEngine
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.data.RotationRecord
import com.borasarang.spotshift.scheduler.Conditions
import com.borasarang.spotshift.scheduler.RotationScheduler
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * IP 로테이션 포그라운드 서비스.
 * 스케줄러를 보유하며 앱 종료 후에도 상시 알림으로 동작을 유지한다.
 */
class IpRotationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scheduler: RotationScheduler? = null
    private var countdownJob: Job? = null
    private var currentIp: String? = null
    private val started = AtomicBoolean(false)
    // v0.4 — 완료 문구 보존용 (카운트다운이 "실행 중"으로 덮지 않도록)
    private var lastSummary: String? = null
    private var activeEngine: RotationEngine? = null

    override fun onCreate() {
        super.onCreate()
        DebugLogger.feature("IpRotationService", "onCreate")
        createChannels()
        startForeground(NOTIFICATION_ID, buildStatusNotification("SpotShift 준비 중"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.feature("IpRotationService", "onStartCommand")
        when (intent?.action) {
            ACTION_START -> startRotation()
            ACTION_STOP -> stopRotation()
        }
        return START_STICKY
    }

    private fun startRotation() {
        if (started.getAndSet(true)) return
        DebugLogger.feature("IpRotationService", "로테이션 시작")

        val engine = RotationEngine(
            context = this,
            dataController = DataController(),
            airplaneController = AirplaneController(this),
            hotspotController = HotspotController(this),
            ipVerifier = IpVerifier()
        )
        engine.onStateChanged = { state ->
            currentIp = state.currentIp
            updateNotification(
                buildStatusNotification(
                    when (state.phase) {
                        com.borasarang.spotshift.data.RotationPhase.IDLE -> "대기 중"
                        com.borasarang.spotshift.data.RotationPhase.CHECKING_IP -> "현재 IP 확인 중"
                        com.borasarang.spotshift.data.RotationPhase.ROTATING_DATA -> "IP 변경 중 (데이터 재연결)"
                        com.borasarang.spotshift.data.RotationPhase.VERIFYING -> "IP 변경 확인 중"
                        com.borasarang.spotshift.data.RotationPhase.RETRYING -> "재시도 중"
                        com.borasarang.spotshift.data.RotationPhase.FALLBACK_AIRPLANE -> "에어플레인 폴백 시도"
                        com.borasarang.spotshift.data.RotationPhase.SUCCESS -> "IP 변경 완료"
                        com.borasarang.spotshift.data.RotationPhase.FAILED -> "IP 변경 실패"
                    },
                    state.currentIp
                )
            )
        }
        // v0.2 — 요구사항 5: 핫스팟(테더링) 자동 켜기 옵션
        // API 36에서는 프로그램적 핫스팟 ON 불가(TETHER_PRIVILEGED) → 설정 화면 안내
        // (이벤트 알림은 RotationEngine이 발행 — 중복 방지)
        engine.onHotspotOffDetected = {
            DebugLogger.w("[SRV] 핫스팟 꺼짐 감지 — 설정 화면 안내")
            runCatching {
                startActivity(
                    // API 36에서 ACTION_TETHERING_SETTINGS 제거됨 → ACTION_WIRELESS_SETTINGS
                    Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        val scheduler = RotationScheduler(
            context = applicationContext,
            engine = engine,
            conditions = Conditions(applicationContext)
        )
        scheduler.onRotationCompleted = ::onRotationCompleted
        scheduler.start()
        this.scheduler = scheduler
        this.activeEngine = engine
        // v0.4 — 과거 랜덤 ID 이벤트 알림 잔재 1회 정리 (이후 단일 ID로 갱신)
        pruneStaleEventNotifications()
        currentIp = engine.state.currentIp
        updateNotification(buildStatusNotification("로테이션 실행 중", currentIp))
        // v0.2 — 시작 시 현재 IP를 조회해 상태 알림에 표시
        scope.launch {
            currentIp = IpVerifier().fetchPublicIp()
            updateNotification(buildStatusNotification("로테이션 실행 중", currentIp))
        }
        // v0.2 — IP 변경 예상 시간 카운트다운 (30초마다 상태 알림 갱신)
        startCountdownUpdate()
    }

    private fun stopRotation() {
        DebugLogger.feature("IpRotationService", "로테이션 정지")
        started.set(false)
        stopCountdownUpdate()
        scheduler?.destroy()
        scheduler = null
        activeEngine = null
        lastSummary = null
        updateNotification(buildStatusNotification("SpotShift 대기 중"))
    }

    /**
     * v0.4 — 과거 랜덤 ID 이벤트 알림 잔재를 1회 정리한다 (이후 단일 ID 갱신).
     * 자신의 알림만 조회하므로 추가 권한 불필요.
     */
    private fun pruneStaleEventNotifications() {
        runCatching {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            nm.activeNotifications
                .filter { it.notification.channelId == CHANNEL_EVENT }
                .forEach { nm.cancel(it.tag, it.id) }
            DebugLogger.feature("IpRotationService", "이벤트 알림 잔재 정리")
        }
    }

    /**
     * v0.2 — 상태 알림의 "IP 변경 예상 시간"을 실시간 갱신한다.
     * v0.3.1 — currentIp가 null이면(시작 시 IP 조회 실패 등) 주기적으로 재조회한다.
     */
    private fun startCountdownUpdate() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (isActive) {
                // v0.4 — 회전 진행 중에는 phase 콜백 표시를 덮지 않음.
                // 완료 후에는 마지막 문구 유지 + IP 재조회 (수동 변경분 반영).
                val phase = activeEngine?.state?.phase
                val settled = phase == null ||
                    phase == com.borasarang.spotshift.data.RotationPhase.IDLE ||
                    phase == com.borasarang.spotshift.data.RotationPhase.SUCCESS ||
                    phase == com.borasarang.spotshift.data.RotationPhase.FAILED
                if (settled) {
                    currentIp = runCatching { IpVerifier().fetchPublicIp() }.getOrNull()
                        ?: currentIp
                    updateNotification(
                        buildStatusNotification(lastSummary ?: "로테이션 실행 중", currentIp)
                    )
                }
                delay(COUNTDOWN_UPDATE_MILLIS)
            }
        }
    }

    private fun stopCountdownUpdate() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun onRotationCompleted(record: RotationRecord) {
        val summary = if (record.changed) {
            "IP 변경 완료: ${record.oldIp ?: "-"} → ${record.newIp ?: "-"} (${record.method})"
        } else {
            "IP 변경 실패 (${record.errorCode ?: "E-AND-NET-0002"})"
        }
        DebugLogger.i("[SRV] $summary")
        lastSummary = summary
        updateNotification(buildStatusNotification(summary, record.newIp))
    }

    private fun updateNotification(notification: Notification) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildStatusNotification(text: String, ip: String? = null): Notification {
        // v0.2 — IP 변경 예상 시간 표시: "로테이션 실행 중 · 현재 IP xxx · IP 변경 예상 시간 xx:xx"
        val content = buildString {
            append(text)
            ip?.let { append(" · 현재 IP ").append(it) }
            nextChangeRemaining()?.let { append(" · IP 변경 예상 시간 ").append(it) }
        }
        val launchIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(com.borasarang.spotshift.R.drawable.ic_notification)
            .setContentTitle("SpotShift")
            .setContentText(content)
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * v0.2 — 마지막 IP 변경 시각 + 주기 기준으로 다음 변경까지 남은 시간 (시:분) 반환.
     * 주기 미경과가 아니거나 알 수 없으면 null.
     */
    private fun nextChangeRemaining(): String? {
        val prefs = Prefs(this)
        val config = runCatching { kotlinx.coroutines.runBlocking { prefs.getConfig() } }
            .getOrNull() ?: return null
        if (!config.enabled) return null
        val lastAt = runCatching { kotlinx.coroutines.runBlocking { prefs.getLastRotationAt() } }
            .getOrNull() ?: return null
        if (lastAt <= 0L) return null
        val nextAt = lastAt + config.intervalMinutes * 60_000L
        val remainingMs = (nextAt - System.currentTimeMillis()).coerceAtLeast(0)
        val totalMin = remainingMs / 60_000
        val hours = totalMin / 60
        val mins = totalMin % 60
        return "%02d:%02d".format(hours, mins)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        // 채널 삭제 금지: 포그라운드 서비스 실행 중 deleteNotificationChannel 호출 시
        // SecurityException(Not allowed to delete channel with a foreground service)으로
        // 서비스 onCreate 크래시 반복 발생. 기존 채널이 없으면 생성만 한다.
        if (nm.getNotificationChannel(CHANNEL_STATUS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_STATUS,
                    "SpotShift 실행 상태",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "IP 로테이션 진행 상태를 표시합니다."
                    // v0.3 — 동작 상태 알림은 배지 카운트에서 제외 (아이콘 배지 "1" 방지)
                    setShowBadge(false)
                }
            )
        }
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EVENT,
                "SpotShift 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "IP 변경 시작/완료 이벤트를 알립니다." }
        )
    }

    override fun onDestroy() {
        DebugLogger.feature("IpRotationService", "onDestroy")
        stopCountdownUpdate()
        scheduler?.destroy()
        started.set(false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_STATUS = "spotshift_status"
        const val CHANNEL_EVENT = "spotshift_event"
        private const val NOTIFICATION_ID = 1001
        private const val COUNTDOWN_UPDATE_MILLIS = 30_000L
        const val ACTION_START = "com.borasarang.spotshift.action.START"
        const val ACTION_STOP = "com.borasarang.spotshift.action.STOP"

        val shizukuReady: Boolean
            get() = ShizukuManager.isReady
    }
}
