package com.borasarang.spotshift.core

import android.content.Context
import com.borasarang.spotshift.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 핫스팟(Wi-Fi 테더링) 상태 조회 컨트롤러.
 *
 * API 36 제약 (S22 실검증, 2026-08-16):
 * TetheringManager 리플렉션/`cmd wifi start-softap` 모두 TETHER_PRIVILEGED로 차단 —
 * Shizuku(shell)에서도 프로그램적 ON 불가. 꺼짐 감지→설정 안내만 수행한다.
 * (재시작 코드는 데드코드라 삭제됨, T-31)
 */
class HotspotController(private val context: Context) {

    /**
     * 핫스팟 ON/OFF 상태 (WifiManager.isWifiApEnabled 리플렉션).
     * @return true=켜짐, false=꺼짐, null=조회 실패(모름 — 꺼짐으로 오판 금지, T-31)
     */
    @Suppress("DEPRECATION")
    suspend fun isHotspotEnabled(): Boolean? = withContext(Dispatchers.IO) {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
                ?: return@withContext null
            val method = wm.javaClass.getMethod("isWifiApEnabled")
            method.invoke(wm) as Boolean
        } catch (e: Exception) {
            DebugLogger.e("핫스팟 상태 조회 실패", "E-AND-NET-0003", e)
            null
        }
    }

    /**
     * 현재 핫스팟 SSID/비밀번호 읽기.
     * API 33+는 SoftApConfiguration(getSoftApConfiguration) 정식 API,
     * 그 외 구버전은 getWifiApConfiguration 리플렉션 폴백.
     * 복원 검증용 — 쓰기는 하지 않는다.
     */
    suspend fun getApConfig(): ApConfig? = withContext(Dispatchers.IO) {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
                ?: return@withContext null
            val softApMethod = runCatching { wm.javaClass.getMethod("getSoftApConfiguration") }.getOrNull()
            if (softApMethod != null) {
                val config = softApMethod.invoke(wm)
                val ssid = config.javaClass.getMethod("getSsid").invoke(config) as? String ?: ""
                val key = config.javaClass.getMethod("getPassphrase").invoke(config) as? String ?: ""
                DebugLogger.d("[NET] 핫스팟 설정 읽기(SoftApConfiguration): SSID=$ssid")
                return@withContext ApConfig(ssid, key)
            }
            @Suppress("DEPRECATION")
            val method = wm.javaClass.getMethod("getWifiApConfiguration")
            val config = method.invoke(wm)
            val ssidField = config.javaClass.getField("SSID")
            val keyField = config.javaClass.getField("preSharedKey")
            val ssid = ssidField.get(config) as? String ?: ""
            val key = keyField.get(config) as? String ?: ""
            DebugLogger.d("[NET] 핫스팟 설정 읽기(legacy): SSID=$ssid")
            ApConfig(ssid, key)
        } catch (e: Exception) {
            DebugLogger.e("핫스팟 설정 읽기 실패", "E-AND-NET-0003", e)
            null
        }
    }

    /**
     * 핫스팟 재시작 (OFF → 대기 → ON).
     * @return 재시작 성공 여부
     *
     * API 36에서 TETHER_PRIVILEGED로 차단되어 호출처 없음 (T-31에서 삭제 예정이었으나
     * 호환용으로 유지 — 신규 호출 금지, 꺼짐 감지→설정 안내만 사용).
     */
    @Deprecated("API 36 TETHER_PRIVILEGED 차단 — 호출 금지, 꺼짐 감지→설정 안내만 사용", level = DeprecationLevel.ERROR)
    suspend fun restartHotspot(
        offWaitMillis: Long = 3_000L,
        onWaitMillis: Long = 5_000L
    ): Boolean = withContext(Dispatchers.IO) {
        DebugLogger.e("핫스팟 재시작 미지원 (API 36 차단)", "E-AND-NET-0003")
        false
    }
}

data class ApConfig(val ssid: String, val password: String)
