package com.borasarang.spotshift.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.data.Prefs

/**
 * T-40 — 온도 가드.
 * 배터리 온도 + thermal 상태를 조회해 로테이션 연기 여부를 판단한다.
 * A90 실측: thermal 4 (critical) + 스킨 53~57°C에서도 모뎀 스로틀로 속도 저하.
 * 연기 기준은 emergency급으로 두어 평시 실행을 막지 않는다 (토글 OFF 시 전체 스킵).
 */
data class ThermalSnapshot(
    /** 배터리 온도 (0.1°C 단위, -1이면 조회 실패) */
    val batteryTempTenths: Int,
    /** PowerManager thermal 상태 (API 29+, 미지원이면 -1) */
    val thermalStatus: Int,
    val guardEnabled: Boolean
) {
    val batteryTempC: Float get() = batteryTempTenths / 10f
    val defer: Boolean
        get() = guardEnabled &&
            (batteryTempTenths >= ThermalGuard.DEFER_BATTERY_TENTHS ||
                thermalStatus >= ThermalGuard.DEFER_THERMAL_STATUS)

    fun reason(): String =
        "배터리 ${if (batteryTempTenths < 0) "?" else "%.1f".format(batteryTempC)}°C, thermal=$thermalStatus"
}

class ThermalGuard(private val context: Context) {

    suspend fun snapshot(): ThermalSnapshot {
        val prefs = Prefs(context)
        val enabled = runCatching { prefs.getThermalGuard() }.getOrDefault(true)
        val battery = runCatching {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val status = context.registerReceiver(null, filter)
            status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        }.getOrDefault(-1)
        val thermal = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runCatching -1
            context.getSystemService(PowerManager::class.java)
                ?.currentThermalStatus ?: -1
        }.getOrDefault(-1)
        val snap = ThermalSnapshot(battery, thermal, enabled)
        DebugLogger.d("[THERMAL] ${snap.reason()} defer=${snap.defer}")
        return snap
    }

    /**
     * T-48 — 홈 히어로 카드의 "온도" 지표용 경량 조회.
     * [snapshot]과 달리 Prefs·로그를 건드리지 않는다 (5초 폴링에서 호출).
     */
    fun batteryTempC(): Float? = runCatching {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (tenths < 0) null else tenths / 10f
    }.getOrNull()

    companion object {
        /** 연기 기준: 배터리 45°C 이상 */
        const val DEFER_BATTERY_TENTHS = 450
        /** 연기 기준: thermal emergency(5) 이상 (4=critical은 경고만) */
        const val DEFER_THERMAL_STATUS = 5
    }
}
