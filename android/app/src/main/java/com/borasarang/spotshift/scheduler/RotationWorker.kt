package com.borasarang.spotshift.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.MainActivity
import com.borasarang.spotshift.R
import com.borasarang.spotshift.core.AirplaneController
import com.borasarang.spotshift.core.DataController
import com.borasarang.spotshift.core.HotspotController
import com.borasarang.spotshift.core.IpVerifier
import com.borasarang.spotshift.core.RotationEngine
import com.borasarang.spotshift.core.SpeedChecker
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.data.RotationConfig
import com.borasarang.spotshift.data.RotationPhase
import com.borasarang.spotshift.data.RotationRecord

/**
 * v0.5 — 주기 IP 로테이션 워커. 구 IpRotationService + RotationScheduler 대체.
 *
 * 두 모드:
 * - tick=true: 가벼운 주기 틱 (포그라운드 불필요). enabled 확인 후 expedited 1회 실행에 위임.
 * - tick=false: 실제 로테이션 (expedited). setForeground로 실행 중 승격, 할당량 초과 시 폴백 후 계속 진행.
 *
 * 수동 변경(HomeViewModel.manualRotate)은 포그라운드 인프로세스로 유지 — 진행 UI와 분리.
 */
class RotationWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val config = prefs.getConfig()
        if (!config.enabled) {
            DebugLogger.d("[SCH] 워커 실행됐으나 토글 OFF — 종료")
            return Result.success()
        }
        if (inputData.getBoolean(KEY_TICK, false)) {
            prefs.updateScheduleTick(System.currentTimeMillis())
            RotationSchedule.enqueueOnce(applicationContext)
            DebugLogger.feature("RotationWorker", "tick → 1회 실행 예약")
            return Result.success()
        }
        return runRotation(config)
    }

    private suspend fun runRotation(config: RotationConfig): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val conditions = Conditions(ctx)
        val speedChecker = SpeedChecker()
        val engine = RotationEngine(
            context = ctx,
            dataController = DataController(),
            airplaneController = AirplaneController(ctx),
            hotspotController = HotspotController(ctx),
            ipVerifier = IpVerifier()
        )
        engine.onStateChanged = { state ->
            updateForegroundNotification(phaseText(state.phase), state.currentIp)
        }
        // v0.2 — 핫스팟 꺼짐 시 설정 화면 안내 (백그라운드라 알림으로 전달)
        engine.onHotspotOffDetected = { notifyHotspotSettings(ctx) }

        // expedited 할당량 초과(RUN_AS_NON_EXPEDITED 폴백) 시 승격 실패 가능 — 그래도 계속 진행
        runCatching { setForeground(foregroundInfo("로테이션 실행 중", null)) }
            .onFailure { e ->
                DebugLogger.e("Worker 포그라운드 승격 실패 — 계속 진행", "E-AND-SRV-0001", e as? Exception)
            }
        DebugLogger.feature("RotationWorker", "run 시작")

        // 유일한 관문은 셀룰러 모드. 스킵해도 로그 + 기록에 남긴다.
        val gate = conditions.evaluate(config)
        if (!gate.passed) {
            DebugLogger.i("[SCH] 조건 미충족으로 스킵: ${gate.skippedReason}")
            prefs.addRecord(
                RotationRecord(
                    changed = false,
                    method = RotationRecord.METHOD_NONE,
                    note = "스킵: ${gate.skippedReason}",
                    errorCode = "E-AND-SCH-0001"
                )
            )
            return Result.success()
        }
        runSpeedGatedRotation(config, engine, speedChecker, prefs, conditions)
        return Result.success()
    }

    /**
     * 변경 후 속도 검증: 무조건 변경하고, 측정 속도가 기준 미만이면 재변경한다.
     * 기준 달성 또는 최대 반복 도달 시 종료 (건너뛰기 없음). 구 RotationScheduler 동일 로직.
     */
    private suspend fun runSpeedGatedRotation(
        config: RotationConfig,
        engine: RotationEngine,
        speedChecker: SpeedChecker,
        prefs: Prefs,
        conditions: Conditions
    ) {
        val maxAttempts = config.speedMaxRechecks.coerceAtLeast(1)
        val threshold = config.speedThresholdMbps
        // 체온계: 실행 전 환경 (배터리·LTE 신호) — 막지 않고 기록에만 남긴다.
        val env = conditions.snapshot().label()?.let { " ($it)" } ?: ""
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            val record = doRotate(config, engine, prefs)
            engine.notifySpeedChecking()
            val speed = speedChecker.measure()
            if (!speed.success) {
                prefs.addRecord(
                    RotationRecord(
                        changed = false,
                        method = RotationRecord.METHOD_SPEED_CHECK,
                        note = "측정 실패 — 검증 없이 종료(E-AND-NET-0004)$env"
                    )
                )
                DebugLogger.e("[SCH] 속도 측정 실패 — 검증 없이 종료", "E-AND-NET-0004")
                return
            }
            val mbps = speed.mbps ?: 0.0
            prefs.updateLastSpeed(mbps.toFloat())
            val label = "%.2f".format(mbps)
            if (mbps >= threshold) {
                prefs.addRecord(
                    RotationRecord(
                        changed = false,
                        method = RotationRecord.METHOD_SPEED_CHECK,
                        note = "측정 ${label}Mbps ≥ 기준 ${threshold}Mbps — 달성$env"
                    )
                )
                DebugLogger.i("[SCH] 속도 달성(${label}Mbps ≥ ${threshold}Mbps) — 종료")
                return
            }
            if (attempt >= maxAttempts) {
                prefs.addRecord(
                    RotationRecord(
                        changed = false,
                        method = RotationRecord.METHOD_SPEED_CHECK,
                        note = "측정 ${label}Mbps < 기준 ${threshold}Mbps — 기준 미달 (IP 변경은 완료)$env"
                    )
                )
                DebugLogger.i("[SCH] 최대 반복 도달(${attempt}/${maxAttempts}) — 기준 미달로 종료 (IP 변경은 완료)")
                return
            }
            prefs.addRecord(
                RotationRecord(
                    changed = false,
                    method = RotationRecord.METHOD_SPEED_CHECK,
                    note = "측정 ${label}Mbps < 기준 ${threshold}Mbps — 재변경 ${attempt}/${maxAttempts}$env"
                )
            )
            DebugLogger.i("[SCH] 저속(${label}Mbps < ${threshold}Mbps) — 재변경 ${attempt + 1}/${maxAttempts}")
        }
    }

    private suspend fun doRotate(
        config: RotationConfig,
        engine: RotationEngine,
        prefs: Prefs
    ): RotationRecord {
        val record = engine.rotate(config)
        prefs.addRecord(record)
        if (record.changed && record.newIp != null) {
            prefs.updateRotationMeta(System.currentTimeMillis(), record.newIp)
        }
        val summary = if (record.changed) {
            "IP 변경 완료: ${record.oldIp ?: "-"} → ${record.newIp ?: "-"} (${record.method})"
        } else {
            "IP 변경 실패 (${record.errorCode ?: "E-AND-NET-0002"})"
        }
        DebugLogger.i("[SCH] $summary")
        updateForegroundNotification(summary, record.newIp)
        return record
    }

    private fun phaseText(phase: RotationPhase): String = when (phase) {
        RotationPhase.IDLE -> "대기 중"
        RotationPhase.CHECKING_IP -> "현재 IP 확인 중"
        RotationPhase.ROTATING_DATA -> "모바일 데이터 재연결 중"
        RotationPhase.VERIFYING -> "IP 변경 확인 중"
        RotationPhase.RETRYING -> "재시도 중"
        RotationPhase.FALLBACK_AIRPLANE -> "에어플레인 폴백 시도"
        RotationPhase.SPEED_CHECKING -> "속도 측정 중"
        RotationPhase.SUCCESS -> "IP 변경 완료"
        RotationPhase.FAILED -> "IP 변경 실패"
    }

    private fun ensureStatusChannel(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_STATUS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_STATUS,
                    "SpotShift 실행 상태",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "IP 로테이션 진행 상태를 표시합니다."
                    setShowBadge(false)
                }
            )
        }
        return nm
    }

    private fun foregroundInfo(text: String, ip: String?): ForegroundInfo {
        val ctx = applicationContext
        ensureStatusChannel(ctx)
        val launchIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val content = buildString {
            append(text)
            ip?.let { append(" · 현재 IP ").append(it) }
        }
        val notification = NotificationCompat.Builder(ctx, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SpotShift")
            .setContentText(content)
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // v0.5 — API 34+는 type=none 금지. manifest 주입 타입과 동일하게 명시 (29 미만은 2-arg).
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification(text: String, ip: String?) {
        runCatching {
            ensureStatusChannel(applicationContext)
                .notify(NOTIFICATION_ID, foregroundInfo(text, ip).notification)
        }
    }

    private fun notifyHotspotSettings(ctx: Context) {
        DebugLogger.w("[SCH] 핫스팟 꺼짐 감지 — 설정 화면 안내 알림")
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_EVENT,
                    "SpotShift 알림",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "IP 변경 시작/완료 이벤트를 알립니다." }
            )
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            nm.notify(
                HOTSPOT_NOTIFICATION_ID,
                NotificationCompat.Builder(ctx, CHANNEL_EVENT)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("SpotShift")
                    .setContentText("핫스팟이 꺼져 있습니다 — 설정에서 다시 켜주세요")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    companion object {
        const val KEY_TICK = "spotshift_tick"
        private const val CHANNEL_STATUS = "spotshift_status"
        private const val CHANNEL_EVENT = "spotshift_event"
        private const val NOTIFICATION_ID = 1001
        private const val HOTSPOT_NOTIFICATION_ID = 1004
    }
}
