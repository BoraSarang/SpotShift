package com.borasarang.spotshift.scheduler

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.SignalMonitor
import com.borasarang.spotshift.data.RotationConfig

/**
 * 스마트 스케줄링 조건 평가.
 *
 * 원칙: 주기가 되면 무조건 실행한다. 조건은 실행을 막는 문지기가 아니라
 * 실행 결과 옆에 같이 적히는 체온계다. 배터리가 낮든 음영지역이든 돌리고,
 * 실패하면 재시도 → 다음 주기로 넘긴다.
 *
 * 유일한 실행 전 관문은 셀룰러 모드다 (Wi-Fi 연결 시 데이터 토글은 위험하고
 * IP 검증 자체가 와이파이로 나가서 의미가 없음).
 */
class Conditions(private val context: Context) {

    data class ConditionResult(
        val passed: Boolean,
        val skippedReason: String? = null
    )

    /**
     * 셀룰러 모드 여부 (Wi-Fi 연결 시 false).
     * v0.2 — 요구사항 0: 셀룰러 모드일 때만 동작해야 함. (유지)
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
        return ConditionResult(true)
    }

    /**
     * 환경 스냅샷 (기록용, 관문 아님): 배터리 잔량 + LTE RSRP.
     * 못 읽으면 null 항목으로 조용히 생략한다 (페일오픈).
     */
    fun snapshot(): EnvSnapshot {
        val battery = runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return@runCatching null
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it >= 0 }
        }.getOrNull()
        val rsrp = runCatching {
            SignalMonitor(context).snapshot().rsrpDbm
        }.getOrNull()
        return EnvSnapshot(batteryPercent = battery, lteRsrpDbm = rsrp)
    }

    data class EnvSnapshot(
        val batteryPercent: Int? = null,
        val lteRsrpDbm: Int? = null
    ) {
        /** 기록 note에 붙이는 한 줄 (읽힌 것만). */
        fun label(): String? {
            val parts = mutableListOf<String>()
            batteryPercent?.let { parts += "배터리 ${it}%" }
            lteRsrpDbm?.let { parts += "RSRP ${it}dBm" }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
    }
}
