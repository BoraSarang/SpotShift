package com.borasarang.spotshift.scheduler

import android.content.Context
import com.borasarang.spotshift.DebugLogger
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
 * 1) 셀룰러 모드 확인 (유일한 관문 — Wi-Fi 연결 시 스킵 + 기록)
 * 2) 무조건 RotationEngine 실행 → 변경 후 속도 검증
 * 배터리·LTE 신호는 실행을 막지 않고 기록에만 남는다 (체온계).
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
                    // 무조건 변경: 주기가 되면 IP가 바뀌었든 말든 실행한다.
                    // 유일한 관문은 셀룰러 모드. 스킵해도 로그 + 기록에 남긴다.
                    val result = conditions.evaluate(config)
                    if (result.passed) {
                        runSpeedGatedRotation(config)
                    } else {
                        DebugLogger.i("[SCH] 조건 미충족으로 스킵: ${result.skippedReason}")
                        prefs.addRecord(
                            com.borasarang.spotshift.data.RotationRecord(
                                changed = false,
                                method = com.borasarang.spotshift.data.RotationRecord.METHOD_NONE,
                                note = "스킵: ${result.skippedReason}",
                                errorCode = "E-AND-SCH-0001"
                            )
                        )
                    }
                } else {
                    DebugLogger.d("[SCH] 스케줄 비활성 — 대기")
                }
                delay(intervalMillis(config.intervalMinutes))
            }
        }
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
        // 체온계: 실행 전 환경 (배터리·LTE 신호) — 막지 않고 기록에만 남긴다.
        val env = conditions.snapshot().label()?.let { " ($it)" } ?: ""
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
                    com.borasarang.spotshift.data.RotationRecord(
                        changed = false,
                        method = com.borasarang.spotshift.data.RotationRecord.METHOD_SPEED_CHECK,
                        note = "측정 ${label}Mbps ≥ 기준 ${threshold}Mbps — 달성$env"
                    )
                )
                DebugLogger.i("[SCH] 속도 달성(${label}Mbps ≥ ${threshold}Mbps) — 종료")
                return
            }
            prefs.addRecord(
                com.borasarang.spotshift.data.RotationRecord(
                    changed = false,
                    method = com.borasarang.spotshift.data.RotationRecord.METHOD_SPEED_CHECK,
                    note = "측정 ${label}Mbps < 기준 ${threshold}Mbps — 재변경 ${attempt}/${maxAttempts}$env"
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
