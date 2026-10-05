package com.borasarang.spotshift.scheduler

import android.content.Context
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.IpVerifier
import com.borasarang.spotshift.core.RotationEngine
import com.borasarang.spotshift.core.SpeedChecker
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
    private val conditions: Conditions,
    private val speedChecker: SpeedChecker = SpeedChecker()
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
                    // 무조건 변경 (T-10 주기 내 스킵 폐지): 주기가 되면 IP가 바뀌었든 말든 실행.
                    // 수동/이동으로 IP가 바뀌어도 스케줄은 그대로 돈다.
                    val result = conditions.evaluate(config)
                    if (result.passed) {
                        runSpeedGatedRotation(config)
                    } else {
                        DebugLogger.i("[SCH] 조건 미충족으로 스킵: ${result.skippedReason}")
                    }
                } else {
                    DebugLogger.d("[SCH] 스케줄 비활성 — 대기")
                }
                delay(intervalMillis(config.intervalMinutes))
            }
        }
    }

    /**
     * v0.2 요구사항 2 (T-10) — 폐지됨.
     * 무조건 변경 원칙으로 전환되어 더 이상 호출되지 않음. 삭제 예정, 남겨둔 이유는
     * 히스토리 참조용. 호출부 없음.
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

    /**
     * 변경 후 속도 검증: 주기가 되면 무조건 변경하고, 측정 속도가 기준 미만이면
     * 재변경한다. 기준 달성 또는 최대 반복 도달 시 종료 (건너뛰기 없음).
     */
    private suspend fun runSpeedGatedRotation(config: com.borasarang.spotshift.data.RotationConfig) {
        val maxAttempts = config.speedMaxRechecks.coerceAtLeast(1)
        val threshold = config.speedThresholdMbps
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            val record = doRotate(config)
            val speed = speedChecker.measure()
            if (!speed.success) {
                prefs.addRecord(
                    com.borasarang.spotshift.data.RotationRecord(
                        changed = false,
                        method = com.borasarang.spotshift.data.RotationRecord.METHOD_SPEED_CHECK,
                        note = "측정 실패 — 검증 없이 종료(E-AND-NET-0004)"
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
                    com.borasarang.spotshift.data.RotationRecord(
                        changed = false,
                        method = com.borasarang.spotshift.data.RotationRecord.METHOD_SPEED_CHECK,
                        note = "측정 ${label}Mbps ≥ 기준 ${threshold}Mbps — 달성"
                    )
                )
                DebugLogger.i("[SCH] 속도 달성(${label}Mbps ≥ ${threshold}Mbps) — 종료")
                return
            }
            prefs.addRecord(
                com.borasarang.spotshift.data.RotationRecord(
                    changed = false,
                    method = com.borasarang.spotshift.data.RotationRecord.METHOD_SPEED_CHECK,
                    note = "측정 ${label}Mbps < 기준 ${threshold}Mbps — 재변경 ${attempt}/${maxAttempts}"
                )
            )
            if (attempt >= maxAttempts) {
                DebugLogger.i("[SCH] 최대 반복 도달(${attempt}/${maxAttempts}) — 포기")
                return
            }
            DebugLogger.i("[SCH] 저속(${label}Mbps < ${threshold}Mbps) — 재변경 ${attempt + 1}/${maxAttempts}")
        }
    }

    private suspend fun doRotate(config: com.borasarang.spotshift.data.RotationConfig): com.borasarang.spotshift.data.RotationRecord {
        val record = engine.rotate(config)
        prefs.addRecord(record)
        if (record.changed && record.newIp != null) {
            prefs.updateRotationMeta(System.currentTimeMillis(), record.newIp)
        }
        onRotationCompleted?.invoke(record)
        return record
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
