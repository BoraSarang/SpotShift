package com.borasarang.spotshift.core

import android.content.Context
import android.provider.Settings
import com.borasarang.spotshift.DebugLogger
import kotlinx.coroutines.delay

class AirplaneController(private val context: Context) {

    val isAirplaneModeOn: Boolean
        get() = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) == 1

    /**
     * 에어플레인 모드 ON → OFF 사이클을 수행한다.
     * @param onHoldMillis 에어플레인 ON 유지 시간
     * @param recoveryMillis OFF 후 네트워크 복구 대기 시간
     * @return 성공 여부
     */
    suspend fun toggleCycle(
        onHoldMillis: Long = DEFAULT_ON_HOLD_MILLIS,
        recoveryMillis: Long = DEFAULT_RECOVERY_MILLIS
    ): Boolean {
        DebugLogger.feature("AirplaneController", "toggleCycle 시작")
        if (setAirplaneMode(true)) {
            delay(onHoldMillis)
            if (setAirplaneMode(false)) {
                delay(recoveryMillis)
                DebugLogger.i("[NET] 에어플레인 사이클 완료 (홀드 ${onHoldMillis}ms)")
                return true
            }
        }
        DebugLogger.e("에어플레인 토글 실패", "E-AND-NET-0002")
        return false
    }

    private suspend fun setAirplaneMode(enabled: Boolean): Boolean {
        // Android 13+ (API 33+)에서 am broadcast AIRPLANE_MODE가 차단되어
        // cmd connectivity airplane-mode 커맨드로 대체한다. (API 36 검증 완료)
        val cmdResult = ShizukuManager.runShell(
            "cmd", "connectivity", "airplane-mode",
            if (enabled) "enable" else "disable"
        )
        if (!cmdResult.isSuccess) {
            DebugLogger.e("에어플레인 모드 설정 실패 (enabled=$enabled)", "E-AND-NET-0002")
            return false
        }

        // 상태 반영 대기
        repeat(10) {
            delay(300)
            if (isAirplaneModeOn == enabled) return true
        }
        DebugLogger.w("에어플레인 상태 반영 대기 초과 (enabled=$enabled)")
        return false
    }

    companion object {
        const val DEFAULT_ON_HOLD_MILLIS = 5_000L
        const val DEFAULT_RECOVERY_MILLIS = 20_000L
    }
}
