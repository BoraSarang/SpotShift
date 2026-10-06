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
    val errorCode: String? = null,
    // v0.4 — 스킵 사유 등 표시용 메모 (기존 기록과 호환: null 허용)
    val note: String? = null
) {
    companion object {
        const val METHOD_NONE = "NONE"
        const val METHOD_DATA_RECONNECT = "DATA_RECONNECT"
        const val METHOD_AIRPLANE = "AIRPLANE"
        const val METHOD_HOTSPOT_RESTART = "HOTSPOT_RESTART"
        // v0.4 — 속도 측정 단독 기록
        const val METHOD_SPEED_CHECK = "SPEED_CHECK"
    }
}

data class RotationConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 120,
    val retryCount: Int = 2,
    val airplaneHoldSec: Int = 5,
    val fallbackEnabled: Boolean = true,
    val hotspotAutoEnable: Boolean = true,
    val lastRotationAt: Long = 0L,
    val lastKnownIp: String? = null,
    // v0.4 — 변경 후 속도 검증 (기준 미달이면 재변경, 최대 N회)
    val speedThresholdMbps: Float = 2.0f,
    val speedMaxRechecks: Int = 3,
    // v0.4 (T-18) — 부팅 시 자동 시작
    val bootAutoStart: Boolean = true,
    // v0.4 — 변경 시작/완료 알림 (끄면 상태 알림만 유지)
    val eventAlertEnabled: Boolean = true
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

/**
 * v0.5 (T-27) — 속도 탭 측정 기록 1건.
 */
data class SpeedRecord(
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val networkType: String = "",
    val downloadMbps: Float? = null,
    val uploadMbps: Float? = null,
    val latencyMs: Long? = null,
    val bytesUsed: Long = 0L,
    val signal: String? = null
)
