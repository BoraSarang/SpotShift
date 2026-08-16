package com.borasarang.spotshift

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.service.IpRotationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpotShiftApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DebugLogger.feature("SpotShiftApp", "onCreate")
        // v0.3.1 — 자동 IP 변경이 켜져 있으면 서비스를 시작 (앱 재설치/기기 재부팅 후에도 복원)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            val config = runCatching { Prefs(this@SpotShiftApp).getConfig() }.getOrNull()
            if (config?.enabled == true) {
                val intent = Intent(this@SpotShiftApp, IpRotationService::class.java)
                    .setAction(IpRotationService.ACTION_START)
                ContextCompat.startForegroundService(this@SpotShiftApp, intent)
                DebugLogger.feature("SpotShiftApp", "자동 IP 변경 ON — 서비스 자동 시작")
            }
        }
    }
}