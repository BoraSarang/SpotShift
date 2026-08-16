package com.borasarang.spotshift.scheduler

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.data.RotationConfig
import java.util.Calendar

/**
 * 스마트 스케줄링 조건 평가 (셀룰러 / 배터리 / 시간대 / 네트워크 품질).
 * v0.2: 셀룰러 모드 전용 — Wi-Fi 연결 시 스킵 (요구사항 0).
 */
class Conditions(private val context: Context) {

    data class ConditionResult(
        val passed: Boolean,
        val skippedReason: String? = null
    )

    /**
     * 셀룰러 모드 여부 (Wi-Fi 연결 시 false).
     * v0.2 — 요구사항 0: 셀룰러 모드일 때만 동작해야 함.
     */
    val isCellularMode: Boolean
        get() = runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }.getOrDefault(false)

    fun evaluate(config: RotationConfig): ConditionResult {
        if (!isCellularMode) {
            DebugLogger.i("[SCH] Wi-Fi 모드 — 셀룰러 전용 스킵 → E-AND-SCH-0001")
            return ConditionResult(false, "Wi-Fi 모드 (셀룰러 전용)")
        }

        val battery = checkBattery(config.minBatteryPercent)
        if (!battery.passed) return battery

        val window = checkTimeWindow(config.scheduleWindowStart, config.scheduleWindowEnd)
        if (!window.passed) return window

        val signal = checkSignal(config.minSignalDbm)
        if (!signal.passed) return signal

        return ConditionResult(true)
    }

    private fun checkBattery(minPercent: Int): ConditionResult {
        if (minPercent <= 0) return ConditionResult(true)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return ConditionResult(true)
        val capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (capacity < minPercent) {
            DebugLogger.i("[SCH] 배터리 조건 미충족: $capacity% < $minPercent% → E-AND-SCH-0001")
            return ConditionResult(false, "배터리 ${capacity}% (최소 ${minPercent}%)")
        }
        return ConditionResult(true)
    }

    private fun checkTimeWindow(startHour: Int?, endHour: Int?): ConditionResult {
        if (startHour == null || endHour == null) return ConditionResult(true)
        val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val inWindow = if (startHour <= endHour) {
            now in startHour..endHour
        } else {
            now >= startHour || now <= endHour
        }
        if (!inWindow) {
            DebugLogger.i("[SCH] 시간대 조건 미충족: now=${now}시 (${startHour}~${endHour}) → E-AND-SCH-0001")
            return ConditionResult(false, "시간대 ${startHour}~${endHour}시 외")
        }
        return ConditionResult(true)
    }

    private fun checkSignal(minDbm: Int): ConditionResult {
        if (minDbm >= 0) return ConditionResult(true)
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ConditionResult(true)
        val rssi = wm.connectionInfo?.rssi ?: return ConditionResult(true)
        if (rssi < minDbm) {
            DebugLogger.i("[SCH] 신호 조건 미충족: ${rssi}dBm < ${minDbm}dBm → E-AND-SCH-0001")
            return ConditionResult(false, "신호 ${rssi}dBm")
        }
        return ConditionResult(true)
    }
}
