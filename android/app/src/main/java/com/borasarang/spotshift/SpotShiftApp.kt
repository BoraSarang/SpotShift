package com.borasarang.spotshift

import android.app.Application
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.scheduler.RotationSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpotShiftApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DebugLogger.feature("SpotShiftApp", "onCreate")
        // v0.5 — 자동 IP 변경이 켜져 있으면 WorkManager 주기 작업 등록 (UPDATE라 중복 없음).
        // 구 startForegroundService 방식은 백그라운드 FGS 금지 크래시의 원인이어서 폐지.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            val config = runCatching { Prefs(this@SpotShiftApp).getConfig() }.getOrNull()
            if (config?.enabled == true) {
                RotationSchedule.enqueuePeriodic(this@SpotShiftApp, config.intervalMinutes)
                DebugLogger.feature("SpotShiftApp", "자동 IP 변경 ON — 주기 작업 등록")
            }
        }
    }
}
