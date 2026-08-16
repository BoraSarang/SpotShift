package com.borasarang.spotshift.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.data.RotationConfig
import com.borasarang.spotshift.data.RotationPhase
import com.borasarang.spotshift.data.RotationRecord
import com.borasarang.spotshift.data.RotationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * IP 변경 시나리오 오케스트레이터.
 * 조건 통과 → 모바일 데이터 재연결 우선(핫스팟 무중단) → IP 검증 → 재시도
 * → 에어플레인 폴백 → 기록.
 *
 * API 36 제약 (S22 실검증, 2026-08-16):
 * - TetheringManager 리플렉션/`cmd wifi start-softap` 모두 TETHER_PRIVILEGED로 차단
 *   → 핫스팟 재시작 폴백 제거, 에어플레인 폴백은 핫스팟 수동 복원 안내 포함
 *
 * v0.2 — 알림 발행은 이 엔진이 단일 책임 (서비스/ViewModel 중복 발행 방지).
 */
class RotationEngine(
    private val context: Context,
    private val dataController: DataController,
    private val airplaneController: AirplaneController,
    private val hotspotController: HotspotController,
    private val ipVerifier: IpVerifier
) {

    private var _state = RotationState()
    val state: RotationState get() = _state
    private val mutex = Mutex()

    var onStateChanged: ((RotationState) -> Unit)? = null
    var onRotationStarted: (() -> Unit)? = null
    var onRotationCompletedNotify: ((RotationRecord) -> Unit)? = null

    /**
     * v0.2 — 요구사항 5: 회전 종료 후 핫스팟이 꺼져 있고 자동 켜기 옵션이 켜져 있으면 호출.
     */
    var onHotspotOffDetected: (() -> Unit)? = null

    private fun update(transform: RotationState.() -> RotationState) {
        _state = _state.transform()
        DebugLogger.d("[STATE] phase=${_state.phase} attempt=${_state.attempt} thread=${Thread.currentThread().name}")
        onStateChanged?.invoke(_state)
    }

    suspend fun rotate(config: RotationConfig): RotationRecord = mutex.withLock {
        onRotationStarted?.invoke()
        // v0.2 — 요구사항 4: IP 변경 시작 알림
        notifyEvent("IP 변경 시작", "모바일 데이터를 재연결하여 IP를 변경합니다")
        val record = rotateInternal(config)
        // v0.2 — 요구사항 5: 핫스팟 자동 켜기 옵션 확인 (회전 성공/실패 무관)
        if (config.hotspotAutoEnable && !hotspotController.isHotspotEnabled()) {
            DebugLogger.w("[NET] 핫스팟 꺼짐 감지 — 자동 켜기 시도")
            notifyEvent("핫스팟이 꺼져 있습니다", "SpotShift 설정에서 핫스팟을 다시 켜주세요")
            onHotspotOffDetected?.invoke()
        }
        onRotationCompletedNotify?.invoke(record)
        record
    }

    private suspend fun rotateInternal(config: RotationConfig): RotationRecord {
        DebugLogger.feature("RotationEngine", "rotate 시작")
        val startTime = System.currentTimeMillis()

        update { copy(phase = RotationPhase.CHECKING_IP, message = "현재 IP 확인 중") }
        val oldIp = ipVerifier.fetchPublicIp()
        update { copy(phase = RotationPhase.ROTATING_DATA, oldIp = oldIp, message = "IP 변경 중") }

        if (oldIp == null) {
            DebugLogger.e("시작 전 IP 조회 실패", "E-AND-NET-0001")
            return finish(
                RotationRecord(
                    oldIp = null,
                    changed = false,
                    method = RotationRecord.METHOD_NONE,
                    durationMs = elapsed(startTime),
                    errorCode = "E-AND-NET-0001"
                )
            )
        }

        // 1) 모바일 데이터 재연결 우선 (핫스팟 무중단, 재시도 포함)
        var attempt = 0
        var newIp: String? = null
        while (attempt <= config.retryCount) {
            attempt++
            update { copy(phase = RotationPhase.ROTATING_DATA, attempt = attempt) }
            val cycleOk = dataController.toggleCycle()
            if (!cycleOk) continue

            update { copy(phase = RotationPhase.VERIFYING, message = "IP 변경 확인 중") }
            newIp = ipVerifier.fetchPublicIp()
            if (newIp != null && newIp != oldIp) {
                DebugLogger.i("[NET] 데이터 재연결로 IP 변경 성공: $oldIp → $newIp (시도 $attempt)")
                return finish(
                    RotationRecord(
                        oldIp = oldIp,
                        newIp = newIp,
                        changed = true,
                        method = RotationRecord.METHOD_DATA_RECONNECT,
                        retryCount = attempt - 1,
                        durationMs = elapsed(startTime)
                    )
                )
            }
            if (attempt <= config.retryCount) {
                update {
                    copy(
                        phase = RotationPhase.RETRYING,
                        message = "IP 미변경, 재시도 ${attempt}/${config.retryCount + 1}",
                        attempt = attempt
                    )
                }
                delay(RETRY_DELAY_MILLIS)
            }
        }

        // 2) 폴백: 에어플레인 사이클 (핫스팟이 꺼짐 — 수동 복원 안내)
        if (config.fallbackEnabled) {
            update { copy(phase = RotationPhase.FALLBACK_AIRPLANE, message = "에어플레인 폴백 시도") }
            val cycleOk = airplaneController.toggleCycle(
                onHoldMillis = config.airplaneHoldSec * 1000L,
                recoveryMillis = AirplaneController.DEFAULT_RECOVERY_MILLIS
            )
            if (cycleOk) {
                update { copy(phase = RotationPhase.VERIFYING, message = "에어플레인 후 IP 확인 중") }
                newIp = ipVerifier.fetchPublicIp()
                if (newIp != null && newIp != oldIp) {
                    DebugLogger.i("[NET] 에어플레인 폴백으로 IP 변경 성공: $oldIp → $newIp")
                    if (!hotspotController.isHotspotEnabled()) {
                        DebugLogger.w("[NET] 핫스팟 꺼짐 감지 — 사용자 수동 복원 필요 (API 36 자동 복원 불가)")
                    }
                    return finish(
                        RotationRecord(
                            oldIp = oldIp,
                            newIp = newIp,
                            changed = true,
                            method = RotationRecord.METHOD_AIRPLANE,
                            retryCount = attempt - 1,
                            durationMs = elapsed(startTime)
                        )
                    )
                }
            }
        }

        DebugLogger.e("IP 변경 실패 (최종): $oldIp → $newIp", "E-AND-NET-0002")
        return finish(
            RotationRecord(
                oldIp = oldIp,
                newIp = newIp,
                changed = false,
                method = RotationRecord.METHOD_NONE,
                retryCount = attempt - 1,
                durationMs = elapsed(startTime),
                errorCode = "E-AND-NET-0002"
            )
        )
    }

    private fun finish(record: RotationRecord): RotationRecord {
        update {
            copy(
                phase = if (record.changed) RotationPhase.SUCCESS else RotationPhase.FAILED,
                currentIp = record.newIp,
                message = if (record.changed) "IP 변경 완료" else "IP 변경 실패",
                errorCode = record.errorCode
            )
        }
        // v0.2 — 요구사항 4: IP 변경 완료 알림 (x → y)
        if (record.changed) {
            notifyEvent(
                "IP 변경 완료",
                "${record.oldIp ?: "-"} → ${record.newIp ?: "-"}"
            )
        } else {
            notifyEvent(
                "IP 변경 실패",
                "현재 IP를 변경하지 못했습니다 (${record.errorCode ?: "E-AND-NET-0002"})"
            )
        }
        return record
    }

    /**
     * v0.2 — 요구사항 4: 이벤트 알림 발행 (서비스/ViewModel과 무관하게 단일 책임).
     */
    private fun notifyEvent(title: String, text: String) {
        DebugLogger.i("[NOTI] 알림: $title — $text")
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EVENT,
                "SpotShift 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "IP 변경 시작/완료 이벤트를 알립니다." }
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENT)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(System.currentTimeMillis().toInt() % Int.MAX_VALUE, notification)
    }

    private fun elapsed(start: Long) = System.currentTimeMillis() - start

    companion object {
        private const val RETRY_DELAY_MILLIS = 10_000L
        const val CHANNEL_EVENT = "spotshift_event"
    }
}