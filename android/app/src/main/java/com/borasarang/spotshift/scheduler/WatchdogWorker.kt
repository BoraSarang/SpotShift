package com.borasarang.spotshift.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.AirplaneController
import com.borasarang.spotshift.core.DataController
import com.borasarang.spotshift.core.HotspotController
import com.borasarang.spotshift.core.IpVerifier
import com.borasarang.spotshift.core.RotationEngine
import com.borasarang.spotshift.core.SpeedWatchdog
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.data.RotationRecord

/**
 * T-42 — 속도 감시 워커 (주기 외 트리거).
 * 패시브 측정 → 저속/생존실패 시에만 RotationEngine 실행.
 * 조용히 동작한다 (포그라운드 승격 없음, 트리거 시에만 기록+메타 갱신).
 */
class WatchdogWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val config = prefs.getConfig()
        if (!config.enabled || !prefs.getSpeedWatch()) {
            DebugLogger.d("[WATCH] 감시 OFF — 종료")
            return Result.success()
        }
        // 셀룰러 관문 (로테이션과 동일 규칙)
        val gate = Conditions(ctx).evaluate(config)
        if (!gate.passed) {
            DebugLogger.i("[WATCH] 스킵: ${gate.skippedReason}")
            return Result.success()
        }
        return when (val verdict = SpeedWatchdog().check(config.speedThresholdMbps)) {
            is SpeedWatchdog.Verdict.IdleHealthy ->
                Result.success().also { DebugLogger.d("[WATCH] 유휴·생존 — 조치 없음") }
            is SpeedWatchdog.Verdict.Healthy ->
                Result.success().also {
                    DebugLogger.d("[WATCH] %.2fMbps 달성 — 조치 없음".format(verdict.mbps))
                }
            is SpeedWatchdog.Verdict.Low -> {
                DebugLogger.feature("WatchdogWorker", "저속 감지 — 로테이션 트리거")
                triggerRotation("감시 저속 %.2fMbps < 기준 %.0fMbps".format(verdict.mbps, config.speedThresholdMbps))
            }
            is SpeedWatchdog.Verdict.LivenessFailed -> {
                DebugLogger.e("감시 생존 확인 실패 — 로테이션 트리거", "E-AND-NET-0001")
                triggerRotation("감시 생존 확인 실패")
            }
        }
    }

    private suspend fun triggerRotation(reason: String): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val config = prefs.getConfig()
        val engine = RotationEngine(
            context = ctx,
            dataController = DataController(),
            airplaneController = AirplaneController(ctx),
            hotspotController = HotspotController(ctx),
            ipVerifier = IpVerifier()
        )
        val record = engine.rotate(config)
        prefs.addRecord(
            record.copy(note = (record.note?.let { "$it · " } ?: "") + reason)
        )
        if (record.changed && record.newIp != null) {
            prefs.updateRotationMeta(System.currentTimeMillis(), record.newIp)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "spotshift_watchdog"
    }
}
