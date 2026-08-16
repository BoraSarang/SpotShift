package com.borasarang.spotshift.scheduler

import android.content.Context
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.IpVerifier
import com.borasarang.spotshift.core.RotationEngine
import com.borasarang.spotshift.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 주기 기반 IP 로테이션 스케줄러.
 * 시작 시 1회 대기 후, 설정 주기마다:
 * 1) 셀룰러 모드 확인 (v0.2)
 * 2) 주기 내 IP 변경 여부 확인 — 마지막 변경 후 주기 미경과 시 스킵 (v0.2)
 * 3) 조건 평가 → RotationEngine 실행
 */
class RotationScheduler(
    private val context: Context,
    private val engine: RotationEngine,
    private val conditions: Conditions
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val prefs = Prefs(context)

    val isRunning: Boolean get() = job?.isActive == true

    var onRotationCompleted: ((com.borasarang.spotshift.data.RotationRecord) -> Unit)? = null

    fun start() {
        if (isRunning) return
        DebugLogger.feature("RotationScheduler", "start")
        job = scope.launch {
            delay(STARTUP_DELAY_MILLIS)
            while (isActive) {
                val config = prefs.getConfig()
                if (config.enabled) {
                    if (shouldSkipByRotation(config)) {
                        DebugLogger.i("[SCH] 주기 내 IP 변경됨 — 자동 변경 스킵 (E-AND-SCH-0001)")
                    } else {
                        val result = conditions.evaluate(config)
                        if (result.passed) {
                            val record = engine.rotate(config)
                            prefs.addRecord(record)
                            if (record.changed && record.newIp != null) {
                                prefs.updateRotationMeta(System.currentTimeMillis(), record.newIp)
                            }
                            onRotationCompleted?.invoke(record)
                        } else {
                            DebugLogger.i("[SCH] 조건 미충족으로 스킵: ${result.skippedReason}")
                        }
                    }
                } else {
                    DebugLogger.d("[SCH] 스케줄 비활성 — 대기")
                }
                delay(intervalMillis(config.intervalMinutes))
            }
        }
    }

    /**
     * v0.2 — 요구사항 2: 마지막 IP 변경 후 변경 주기가 지나지 않았으면 자동 변경하지 않는다.
     * - 이동으로 IP가 바뀐 경우: 현재 IP가 lastKnownIp와 다르면 이미 변경된 것으로 간주 → 스킵 + 메타 갱신
     * - 셀룰러 모드 전용 (Wi-Fi는 IpVerifier 조회 대상이 아니므로 검사 생략)
     */
    private suspend fun shouldSkipByRotation(config: com.borasarang.spotshift.data.RotationConfig): Boolean {
        val lastAt = prefs.getLastRotationAt()
        if (lastAt <= 0L) return false
        val elapsedMs = System.currentTimeMillis() - lastAt
        if (elapsedMs < intervalMillis(config.intervalMinutes)) {
            val currentIp = IpVerifier().fetchPublicIp()
            val lastIp = config.lastKnownIp
            if (lastIp != null && currentIp != null && currentIp != lastIp) {
                // 이동으로 IP가 이미 변경됨 → 변경 불필요, 메타만 갱신
                prefs.updateRotationMeta(System.currentTimeMillis(), currentIp)
                DebugLogger.i("[SCH] 주기 내 이동 IP 변경 감지: $lastIp → $currentIp")
                return true
            }
            DebugLogger.d("[SCH] 주기 내 IP 유지: $currentIp (변경 불필요)")
            return true
        }
        return false
    }

    fun stop() {
        DebugLogger.feature("RotationScheduler", "stop")
        job?.cancel()
        job = null
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private fun intervalMillis(minutes: Int): Long {
        val effective = minutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
        return effective * 60_000L
    }

    companion object {
        private const val STARTUP_DELAY_MILLIS = 5_000L
        private const val MIN_INTERVAL_MINUTES = 1
    }
}
