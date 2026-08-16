package com.borasarang.spotshift.core

import android.content.Context
import android.os.IBinder
import android.os.Looper
import com.borasarang.spotshift.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.Proxy
import java.util.concurrent.Executors

/**
 * 핫스팟(Wi-Fi 테더링) 재시작 폴백 컨트롤러.
 * Shizuku 권한으로 TetheringManager 시스템 API를 리플렉션 호출한다.
 * Android 11+ (API 30+) 대상.
 */
class HotspotController(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * 핫스팟 ON/OFF 상태 (WifiManager.isWifiApEnabled 리플렉션).
     */
    @Suppress("DEPRECATION")
    suspend fun isHotspotEnabled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
                ?: return@withContext false
            val method = wm.javaClass.getMethod("isWifiApEnabled")
            method.invoke(wm) as Boolean
        } catch (e: Exception) {
            DebugLogger.e("핫스팟 상태 조회 실패", "E-AND-NET-0003", e)
            false
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
     */
    suspend fun restartHotspot(
        offWaitMillis: Long = DEFAULT_OFF_WAIT_MILLIS,
        onWaitMillis: Long = DEFAULT_ON_WAIT_MILLIS
    ): Boolean = withContext(Dispatchers.IO) {
        DebugLogger.feature("HotspotController", "restartHotspot 시작")
        val tetheringManager = createTetheringManager() ?: run {
            DebugLogger.e("TetheringManager 생성 실패", "E-AND-NET-0003")
            return@withContext false
        }
        try {
            stopTethering(tetheringManager)
            delay(offWaitMillis)
            val started = startTethering(tetheringManager)
            if (started) {
                delay(onWaitMillis)
                DebugLogger.i("[NET] 핫스팟 재시작 완료")
                true
            } else {
                DebugLogger.e("핫스팟 시작 실패", "E-AND-NET-0003")
                false
            }
        } catch (e: Exception) {
            DebugLogger.e("핫스팟 재시작 예외", "E-AND-NET-0003", e)
            false
        }
    }

    private fun createTetheringManager(): Any? {
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getService = serviceManagerClass.getMethod("getService", String::class.java)
            val tetheringBinder = getService.invoke(null, "tethering") as IBinder
            val connectivityBinder = getService.invoke(null, "connectivity") as IBinder

            val tetheringClass = Class.forName("android.net.TetheringManager")
            val iCmClass = Class.forName("android.net.IConnectivityManager\$Stub")
            val asInterface = iCmClass.getMethod("asInterface", IBinder::class.java)
            val iCm = asInterface.invoke(null, connectivityBinder)

            val constructor = tetheringClass.constructors.firstOrNull { c ->
                val params = c.parameterTypes
                params.size == 4 &&
                    params[0] == Context::class.java &&
                    params[1] == IBinder::class.java &&
                    params[2].name == "android.net.IConnectivityManager"
            } ?: throw NoSuchMethodException("TetheringManager(Context, IBinder, IConnectivityManager, Looper)")

            val wrapper = ShizukuBinderWrapper(tetheringBinder)
            constructor.newInstance(context, wrapper, iCm, Looper.getMainLooper())
        } catch (e: Exception) {
            DebugLogger.e("TetheringManager 생성 실패 (버전 미지원일 수 있음)", "E-AND-NET-0003", e)
            null
        }
    }

    private fun stopTethering(manager: Any) {
        val method = try {
            manager.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
        } catch (e: NoSuchMethodException) {
            manager.javaClass.getMethod("stopTethering", java.lang.Integer.TYPE)
        }
        method.invoke(manager, TETHERING_WIFI)
        DebugLogger.d("[NET] stopTethering 호출 완료")
    }

    private fun startTethering(manager: Any): Boolean {
        val callbackClass = Class.forName("android.net.TetheringManager\$OnStartTetheringCallback")
        var success = false
        val latch = java.util.concurrent.CountDownLatch(1)
        val proxy = Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { _, method, _ ->
            when (method.name) {
                "onTetheringStarted" -> {
                    DebugLogger.i("[NET] onTetheringStarted 콜백 수신")
                    success = true
                    latch.countDown()
                }
                "onTetheringFailed" -> {
                    DebugLogger.e("onTetheringFailed 콜백 수신", "E-AND-NET-0003")
                    latch.countDown()
                }
            }
            null
        }

        val method = try {
            manager.javaClass.getMethod(
                "startTethering",
                Int::class.javaPrimitiveType,
                java.util.concurrent.Executor::class.java,
                callbackClass
            )
        } catch (e: NoSuchMethodException) {
            manager.javaClass.getMethod(
                "startTethering",
                java.lang.Integer.TYPE,
                java.util.concurrent.Executor::class.java,
                callbackClass
            )
        }
        method.invoke(manager, TETHERING_WIFI, executor, proxy)
        latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
        return success
    }

    companion object {
        const val TETHERING_WIFI = 0
        const val DEFAULT_OFF_WAIT_MILLIS = 3_000L
        const val DEFAULT_ON_WAIT_MILLIS = 5_000L
    }
}

data class ApConfig(val ssid: String, val password: String)
