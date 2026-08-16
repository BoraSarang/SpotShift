package com.borasarang.spotshift.data

data class RotationRecord(
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val oldIp: String? = null,
    val newIp: String? = null,
    val changed: Boolean = false,
    val method: String = METHOD_NONE,
    val retryCount: Int = 0,
    val durationMs: Long = 0L,
    val errorCode: String? = null
) {
    companion object {
        const val METHOD_NONE = "NONE"
        const val METHOD_DATA_RECONNECT = "DATA_RECONNECT"
        const val METHOD_AIRPLANE = "AIRPLANE"
        const val METHOD_HOTSPOT_RESTART = "HOTSPOT_RESTART"
    }
}

data class RotationConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 120,
    val scheduleWindowStart: Int? = null,
    val scheduleWindowEnd: Int? = null,
    val minBatteryPercent: Int = 30,
    val minSignalDbm: Int = -110,
    val retryCount: Int = 2,
    val airplaneHoldSec: Int = 5,
    val fallbackEnabled: Boolean = true,
    val hotspotAutoEnable: Boolean = true,
    val lastRotationAt: Long = 0L,
    val lastKnownIp: String? = null
)

enum class RotationPhase {
    IDLE,
    CHECKING_IP,
    ROTATING_DATA,
    VERIFYING,
    RETRYING,
    FALLBACK_AIRPLANE,
    SUCCESS,
    FAILED
}

data class RotationState(
    val phase: RotationPhase = RotationPhase.IDLE,
    val currentIp: String? = null,
    val oldIp: String? = null,
    val message: String? = null,
    val attempt: Int = 0,
    val errorCode: String? = null
)
