package com.borasarang.spotshift

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import com.borasarang.spotshift.core.DunKeeper
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.receiver.PowerReceiver
import com.borasarang.spotshift.scheduler.RotationSchedule
import com.borasarang.spotshift.scheduler.WatchdogSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class SpotShiftApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DebugLogger.feature("SpotShiftApp", "onCreate")
        // T-41 — 충전기 연결/분리 동적 수신 (매니페스트로는 백그라운드 제한에 막힘).
        // 앱 프로세스 생존 중에만 동작한다 (상시 감시는 회전 시점 스냅샷이 담당).
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                this, PowerReceiver(), filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            DebugLogger.feature("SpotShiftApp", "전원 수신기 등록")
        }
        // v0.5 — 자동 IP 변경이 켜져 있으면 WorkManager 주기 작업 등록 (UPDATE라 중복 없음).
        // 구 startForegroundService 방식은 백그라운드 FGS 금지 크래시의 원인이어서 폐지.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            val config = runCatching { Prefs(this@SpotShiftApp).getConfig() }.getOrNull()
            if (config?.enabled == true) {
                RotationSchedule.enqueuePeriodic(this@SpotShiftApp, config.intervalMinutes)
                DebugLogger.feature("SpotShiftApp", "자동 IP 변경 ON — 주기 작업 등록")
                // T-42 — 감시 스케줄 동기화
                runCatching {
                    val watch = Prefs(this@SpotShiftApp).getSpeedWatch()
                    WatchdogSchedule.sync(this@SpotShiftApp, true, watch, config.intervalMinutes)
                }
            }
        }
        // T-39 — Shizuku 연결 시 DUN 유지 확인 (재부팅·리셋 복구)
        scope.launch {
            ShizukuManager.ready.filter { it }.collect {
                runCatching {
                    val ok = DunKeeper(Prefs(this@SpotShiftApp)).ensure()
                    DebugLogger.feature("SpotShiftApp", "DUN 유지 확인 ok=$ok")
                }
            }
        }
    }
}
