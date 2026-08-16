package com.borasarang.spotshift.core

import com.borasarang.spotshift.DebugLogger
import kotlinx.coroutines.delay

/**
 * 모바일 데이터 재연결 컨트롤러.
 * Shizuku 권한으로 `svc data disable/enable`을 실행해 셀룰러 업스트림만 재연결한다.
 * 핫스팟(Wi-Fi SoftAP)은 유지한 채 공인 IP를 변경하는 우선 방법이다.
 * (S22/API 36 실검증: IP 변경 성공 + TetheredState 유지)
 */
class DataController {

    /**
     * 모바일 데이터 OFF → 대기 → ON 사이클을 수행한다.
     * @param offHoldMillis 데이터 OFF 유지 시간
     * @param recoveryMillis ON 후 네트워크 복구 대기 시간
     * @return 성공 여부
     */
    suspend fun toggleCycle(
        offHoldMillis: Long = DEFAULT_OFF_HOLD_MILLIS,
        recoveryMillis: Long = DEFAULT_RECOVERY_MILLIS
    ): Boolean {
        DebugLogger.feature("DataController", "toggleCycle 시작")
        if (setDataEnabled(false)) {
            delay(offHoldMillis)
            if (setDataEnabled(true)) {
                delay(recoveryMillis)
                DebugLogger.i("[NET] 모바일 데이터 재연결 완료 (홀드 ${offHoldMillis}ms)")
                return true
            }
        }
        DebugLogger.e("모바일 데이터 토글 실패", "E-AND-NET-0002")
        return false
    }

    private suspend fun setDataEnabled(enabled: Boolean): Boolean {
        val arg = if (enabled) "enable" else "disable"
        val result = ShizukuManager.runShell("svc", "data", arg)
        if (!result.isSuccess) {
            DebugLogger.e("svc data $arg 실패", "E-AND-NET-0002")
            return false
        }
        DebugLogger.d("[NET] svc data $arg exit=${result.exitCode}")
        return true
    }

    companion object {
        const val DEFAULT_OFF_HOLD_MILLIS = 5_000L
        const val DEFAULT_RECOVERY_MILLIS = 20_000L
    }
}