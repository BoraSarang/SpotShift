package com.borasarang.spotshift.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.data.Prefs

/**
 * T-41 — 전원 감시.
 * 충전 상태 + 배터리 잔량을 조회해 로테이션 연기 여부를 판단한다.
 * A90 실측: 테더링 머신이 충전 미연결(93% 방전 중)인 채로 운용되고 있었다.
 */
data class PowerSnapshot(
    /** 충전 중 여부 (USB/AC/무선 모두 포함) */
    val charging: Boolean,
    /** 배터리 잔량 % (0~100, -1이면 조회 실패) */
    val batteryPct: Int,
    val guardEnabled: Boolean
) {
    val defer: Boolean
        get() = guardEnabled && !charging && batteryPct in 0..PowerMonitor.DEFER_BATTERY_PCT

    fun reason(): String =
        "충전=${if (charging) "중" else "해제"}, 잔량=${if (batteryPct < 0) "?" else "$batteryPct%"}"
}

class PowerMonitor(private val context: Context) {

    suspend fun snapshot(): PowerSnapshot {
        val prefs = Prefs(context)
        val enabled = runCatching { prefs.getPowerGuard() }.getOrDefault(true)
        var charging = false
        var pct = -1
        runCatching {
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            status?.let {
                val plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                charging = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                    plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                    plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) pct = (level * 100 / scale)
            }
        }
        val snap = PowerSnapshot(charging, pct, enabled)
        DebugLogger.d("[POWER] ${snap.reason()} defer=${snap.defer}")
        return snap
    }

    companion object {
        /** 연기 기준: 미충전 + 잔량 20% 이하 */
        const val DEFER_BATTERY_PCT = 20
    }
}
