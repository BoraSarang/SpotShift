package com.borasarang.spotshift.core

import com.borasarang.spotshift.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class IpVerifier(private val client: OkHttpClient = defaultClient) {

    /**
     * 공인 IPv4 주소를 조회한다. 실패 시 null.
     */
    suspend fun fetchPublicIp(): String? = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            val request = Request.Builder()
                .url(IPIFY_ENDPOINT)
                .header("User-Agent", "SpotShift/0.1")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    DebugLogger.w("ipify 응답 실패: HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val ip = JSONObject(body).optString("ip").takeIf { it.isNotBlank() }
                DebugLogger.perf("ipify_fetch", System.currentTimeMillis() - start)
                DebugLogger.d("[NET] 공인 IP 조회: $ip")
                ip
            }
        } catch (e: Exception) {
            DebugLogger.e("공인 IP 조회 실패", "E-AND-NET-0001", e)
            null
        }
    }

    companion object {
        const val IPIFY_ENDPOINT = "https://api.ipify.org?format=json"
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
