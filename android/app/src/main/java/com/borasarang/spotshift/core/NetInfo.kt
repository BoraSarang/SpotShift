package com.borasarang.spotshift.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

/**
 * v0.5 (T-27) — 속도 탭 접속 상태 표시용.
 * 현재 사용 네트워크 + Wi-Fi 상태 + 셀룰러 상태를 각각 문자열로 제공한다.
 */
class NetInfo(private val context: Context) {

    data class Snapshot(
        val current: String,
        val wifi: String,
        val cell: String
    )

    fun snapshot(): Snapshot {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.let { runCatching { it.getNetworkCapabilities(it.activeNetwork) }.getOrNull() }
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val onCell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val current = when {
            onWifi && onCell -> "Wi-Fi + 셀룰러"
            onWifi -> "Wi-Fi"
            onCell -> cellRat()
            else -> "연결 없음"
        }
        return Snapshot(current, wifiLabel(onWifi), cellLabel(onCell))
    }

    fun isCellularActive(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun cellRat(): String =
        runCatching { SignalMonitor(context).snapshot().rat }.getOrNull() ?: "셀룰러"

    private fun wifiLabel(onWifi: Boolean): String {
        if (!onWifi) return "꺼짐"
        return runCatching {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(WifiManager::class.java)
                ?: return "켜짐"
            val info = wm.connectionInfo ?: return "켜짐"
            val ssid = info.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            val rssi = info.rssi.takeIf { it != -127 }
            listOfNotNull(ssid, rssi?.let { "RSSI $it dBm" }).joinToString(" · ").ifEmpty { "켜짐" }
        }.getOrDefault("켜짐")
    }

    private fun cellLabel(onCell: Boolean): String {
        if (!onCell) return "미사용"
        return runCatching { SignalMonitor(context).snapshot().display() }
            .getOrNull() ?: "사용 중"
    }
}
