package com.borasarang.spotshift.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.borasarang.spotshift.core.AirplaneController
import com.borasarang.spotshift.core.DataController
import com.borasarang.spotshift.core.HotspotController
import com.borasarang.spotshift.core.IpVerifier
import com.borasarang.spotshift.core.RotationEngine
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.core.SpeedChecker
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.data.RotationRecord
import com.borasarang.spotshift.plugin.PluginContract
import com.borasarang.spotshift.plugin.PluginLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * T-35 — 연동 계약 v2 §4 액션 수신기 (L2).
 *
 *   adb shell am broadcast -a com.borasarang.spotshift.PLUGIN_ACTION --es cmd autorotate
 *
 * 브로드캐스트라 UI 전면 전환 없음 (v1 MainActivity 경로는 SDK §4.3에 따라 제거).
 * 결과는 v2 [REMOTE] 한 줄로만 보고한다. HomeViewModel.manualRotate와 동일 루프의
 * 헤드리스 복제 (수동 경로를 건드리지 않기 위한 의도적 분리).
 */
class PluginActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PluginContract.ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAction(context.applicationContext, intent.getStringExtra("cmd"))
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleAction(context: Context, rawCmd: String?) {
        val cmd = PluginContract.sanitizeCmd(rawCmd)
        if (cmd != PluginContract.CMD_AUTOROTATE) {
            PluginLog.remote(PluginContract.remoteInvalid(cmd))
            return
        }
        if (!ShizukuManager.isReady) {
            PluginLog.remote(
                PluginContract.remoteFail("E-AND-PLG-0001", "shizuku not ready")
            )
            return
        }
        val prefs = Prefs(context)
        // fresh read (콜드스타트 초기값 오판 방지)
        val cfg = prefs.getConfig()
        if (!cfg.pluginAllowed) {
            PluginLog.remote(PluginContract.remoteDenied())
            return
        }
        val engine = RotationEngine(
            context = context,
            dataController = DataController(),
            airplaneController = AirplaneController(context),
            hotspotController = HotspotController(context),
            ipVerifier = IpVerifier()
        )
        val speedChecker = SpeedChecker()
        val threshold = cfg.speedThresholdMbps
        val maxAttempts = cfg.speedMaxRechecks.coerceAtLeast(1)
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            val base = engine.rotate(cfg)
            engine.notifySpeedChecking()
            val speed = speedChecker.measure()
            if (speed.success && speed.mbps != null) {
                prefs.updateLastSpeed(speed.mbps.toFloat())
                val label = "%.2f".format(speed.mbps)
                prefs.addRecord(
                    RotationRecord(
                        changed = false,
                        method = RotationRecord.METHOD_SPEED_CHECK,
                        note = "측정 ${label}Mbps"
                    )
                )
                if (speed.mbps >= threshold) {
                    finishAction(prefs, base, speed.mbps, "측정 ${label}Mbps ≥ 기준 ${threshold}Mbps — 달성")
                    return
                }
                if (attempt >= maxAttempts) {
                    finishAction(prefs, base, speed.mbps, "측정 ${label}Mbps < 기준 ${threshold}Mbps — 기준 미달 (IP 변경은 완료)")
                    return
                }
                prefs.addRecord(base.copy(note = "측정 ${label}Mbps < 기준 ${threshold}Mbps — 재변경 ${attempt}/${maxAttempts}"))
                if (base.changed && base.newIp != null) {
                    prefs.updateRotationMeta(System.currentTimeMillis(), base.newIp)
                }
                continue
            }
            finishAction(prefs, base, null, base.errorCode ?: base.note ?: "E-AND-NET-0004")
            return
        }
    }

    private suspend fun finishAction(
        prefs: Prefs,
        base: RotationRecord,
        speedMbps: Double?,
        note: String
    ) {
        prefs.addRecord(base.copy(note = note))
        if (base.changed && base.newIp != null) {
            prefs.updateRotationMeta(System.currentTimeMillis(), base.newIp)
        }
        if (base.changed) {
            PluginLog.remote(
                PluginContract.remoteOk(base.oldIp, base.newIp, speedMbps, note)
            )
        } else {
            PluginLog.remote(
                PluginContract.remoteFail("E-AND-PLG-0003", note)
            )
        }
    }
}
