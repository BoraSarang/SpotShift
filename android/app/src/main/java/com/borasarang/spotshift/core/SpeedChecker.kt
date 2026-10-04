package com.borasarang.spotshift.core

import com.borasarang.spotshift.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * v0.4 (T-17) — 속도 기반 조건부 실행용 다운로드 속도 측정.
 * 일반 INTERNET 권한만 사용 (Shizuku 불필요). 1MB 파일 수신 시간으로 Mbps 환산.
 */
class SpeedChecker(private val client: OkHttpClient = defaultClient) {

    data class SpeedResult(
        val success: Boolean,
        val mbps: Double? = null,
        val errorCode: String? = null
    )

    suspend fun measure(): SpeedResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(SPEED_ENDPOINT)
                .header("User-Agent", "SpotShift/0.4")
                .build()
            val start = System.currentTimeMillis()
            var bytes = 0L
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    DebugLogger.e("속도 측정 응답 실패: HTTP ${response.code}", "E-AND-NET-0004")
                    return@withContext SpeedResult(false, errorCode = "E-AND-NET-0004")
                }
                val body = response.body ?: run {
                    DebugLogger.e("속도 측정 본문 없음", "E-AND-NET-0004")
                    return@withContext SpeedResult(false, errorCode = "E-AND-NET-0004")
                }
                val stream = body.byteStream()
                val buf = ByteArray(64 * 1024)
                while (bytes <= MAX_BYTES) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    bytes += n
                }
            }
            val elapsedMs = (System.currentTimeMillis() - start).coerceAtLeast(1)
            if (bytes <= 0) {
                DebugLogger.e("속도 측정 수신 0바이트", "E-AND-NET-0004")
                return@withContext SpeedResult(false, errorCode = "E-AND-NET-0004")
            }
            val mbps = bytes * 8.0 / elapsedMs / 1000.0
            DebugLogger.perf("speed_check", elapsedMs)
            DebugLogger.d("[NET] 속도 측정: ${"%.2f".format(mbps)}Mbps (${bytes}B/${elapsedMs}ms)")
            SpeedResult(true, mbps)
        } catch (e: Exception) {
            DebugLogger.e("속도 측정 실패", "E-AND-NET-0004", e)
            SpeedResult(false, errorCode = "E-AND-NET-0004")
        }
    }

    companion object {
        // v0.4.1 — speedtest.tele2.net 연결 거부 실측 → Cloudflare로 교체
        const val SPEED_ENDPOINT = "https://speed.cloudflare.com/__down?bytes=1048576"
        private const val MAX_BYTES = 2L * 1024 * 1024
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
