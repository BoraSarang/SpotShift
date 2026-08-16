package com.borasarang.spotshift.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Quick Settings 타일: IP 로테이션 시작/정지 토글.
 */
class SpotShiftTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTile(isRotationActive())
    }

    override fun onClick() {
        super.onClick()
        DebugLogger.feature("SpotShiftTileService", "onClick")
        if (!ShizukuManager.isReady) {
            DebugLogger.e("Shizuku 미준비 — 타일 무시", "E-AND-PERM-0002")
            return
        }
        scope.launch {
            val prefs = Prefs(applicationContext)
            val config = prefs.getConfig()
            val newEnabled = !isRotationActive()
            prefs.saveConfig(config.copy(enabled = newEnabled))

            val intent = Intent(applicationContext, IpRotationService::class.java)
            intent.action = if (newEnabled) IpRotationService.ACTION_START else IpRotationService.ACTION_STOP
            applicationContext.startForegroundService(intent)
            updateTile(newEnabled)
        }
    }

    private fun isRotationActive(): Boolean {
        val prefs = Prefs(applicationContext)
        return runCatching { kotlinx.coroutines.runBlocking { prefs.getConfig().enabled } }.getOrDefault(false)
    }

    private fun updateTile(active: Boolean) {
        qsTile?.let { tile ->
            tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (active) "SpotShift 켜짐" else "SpotShift 꺼짐"
            tile.contentDescription = tile.label
            tile.updateTile()
        }
    }
}
