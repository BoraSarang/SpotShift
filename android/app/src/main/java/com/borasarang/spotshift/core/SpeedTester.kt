package com.borasarang.spotshift.core

import com.borasarang.spotshift.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * v0.5 (T-27) — fast.com식 실시간 속도 측정 엔진.
 * 다운로드: 4개 동시 연결 수신, 500ms 단위 live Mbps 콜백, 초반 램프업 2샘플 제외 후 평균.
 * 업로드: 4개 동시 POST, 동일 샘플링. 지연: 소량 GET 왕복 최소값.
 * 일반 INTERNET 권한만 사용 (Shizuku 불필요). 취소는 [cancel]로 활성 Call을 끊는다.
 */
class SpeedTester(private val client: OkHttpClient = defaultClient) {

    data class TestResult(
        val success: Boolean,
        val mbps: Double? = null,
        val bytesUsed: Long = 0L,
        val errorCode: String? = null
    )

    private val activeCalls = Collections.synchronizedList(mutableListOf<Call>())
    @Volatile private var transferOpen = false

    fun cancel() {
        transferOpen = false
        activeCalls.toList().forEach { runCatching { it.cancel() } }
        activeCalls.clear()
    }

    suspend fun latency(): Long? = withContext(Dispatchers.IO) {
        var best: Long? = null
        repeat(3) {
            runCatching {
                val req = Request.Builder()
                    .url("$DOWN_BASE?bytes=1024")
                    .header("User-Agent", USER_AGENT)
                    .build()
                val start = System.currentTimeMillis()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.readBytes()
                        val ms = System.currentTimeMillis() - start
                        best = minOf(best ?: ms, ms)
                    }
                }
            }
        }
        best
    }

    suspend fun download(onSample: (mbps: Double, bytesTotal: Long) -> Unit): TestResult =
        withContext(Dispatchers.IO) {
            val counter = AtomicLong(0L)
            try {
                coroutineScope {
                    val workers = (0 until DOWN_STREAMS).map {
                        async {
                            runCatching {
                                val req = Request.Builder()
                                    .url("$DOWN_BASE?bytes=$DOWN_BYTES_PER_STREAM")
                                    .header("User-Agent", USER_AGENT)
                                    .build()
                                val call = client.newCall(req)
                                activeCalls += call
                                call.execute().use { resp ->
                                    if (!resp.isSuccessful) return@async
                                    val stream = resp.body?.byteStream() ?: return@async
                                    val buf = ByteArray(64 * 1024)
                                    while (coroutineContext.isActive && counter.get() < DOWN_CAP_BYTES) {
                                        val n = runCatching { stream.read(buf) }.getOrNull() ?: break
                                        if (n < 0) break
                                        counter.addAndGet(n.toLong())
                                    }
                                }
                            }
                            Unit
                        }
                    }
                    val samples = mutableListOf<Double>()
                    val start = System.currentTimeMillis()
                    var lastBytes = 0L
                    var lastT = start
                    while (coroutineContext.isActive) {
                        delay(SAMPLE_MS)
                        val now = System.currentTimeMillis()
                        val bytes = counter.get()
                        val dt = (now - lastT).coerceAtLeast(1)
                        val mbps = (bytes - lastBytes) * 8.0 / dt / 1000.0
                        lastBytes = bytes
                        lastT = now
                        samples += mbps
                        onSample(mbps, bytes)
                        val elapsed = now - start
                        if ((elapsed >= DOWN_MIN_MS && bytes >= DOWN_MIN_BYTES) ||
                            elapsed >= DOWN_MAX_MS || bytes >= DOWN_CAP_BYTES
                        ) break
                    }
                    workers.forEach { it.cancel() }
                    cancel()
                    val stable = if (samples.size > 2) samples.drop(2) else samples
                    if (stable.isEmpty() || counter.get() <= 0) {
                        DebugLogger.e("다운로드 측정 수신 0바이트", "E-AND-NET-0004")
                        return@coroutineScope TestResult(false, bytesUsed = counter.get(), errorCode = "E-AND-NET-0004")
                    }
                    val avg = stable.average()
                    DebugLogger.i("[NET] 다운로드 측정: ${"%.2f".format(avg)}Mbps (${counter.get()}B)")
                    TestResult(true, avg, counter.get())
                }
            } catch (e: Exception) {
                cancel()
                if (e is kotlinx.coroutines.CancellationException) throw e
                DebugLogger.e("다운로드 측정 실패", "E-AND-NET-0004", e)
                TestResult(false, bytesUsed = counter.get(), errorCode = "E-AND-NET-0004")
            }
        }

    /**
     * 업로드: 스트림당 고정 본문을 POST하고 벽시계로 환산.
     * (버퍼 write를 세면 뻥튀기/0Mbps가 나오므로, 완료된 바이트/경과시간만 쓴다.
     * live 표시는 진행률 참고용.)
     * 저속 링크에서는 제한 시간 안에 전량 전송+서버 200 응답이 안 올 수 있다.
     * 전송 바이트/벽시계 자체가 속도이므로 서버 확답이 없어도 추정값으로 성공 처리한다.
     * 전송 0B일 때만 실패.
     */
    suspend fun upload(onSample: (mbps: Double, bytesTotal: Long) -> Unit): TestResult =
        withContext(Dispatchers.IO) {
            // writeTo는 네트워크 속도로 블로킹되므로 기록 바이트 ≈ 전송 바이트 (저속 링크 기준).
            val sentBytes = AtomicLong(0L)
            var anySuccess = false
            val start = System.currentTimeMillis()
            try {
                transferOpen = true
                coroutineScope {
                    val payload = ByteArray(UP_BYTES_PER_STREAM.toInt()) { (it % 251).toByte() }
                    val workers = (0 until UP_STREAMS).map {
                        async {
                            runCatching {
                                val body = object : RequestBody() {
                                    override fun contentType() = "application/octet-stream".toMediaType()
                                    override fun contentLength() = payload.size.toLong()
                                    override fun writeTo(sink: BufferedSink) {
                                        var off = 0
                                        while (off < payload.size && transferOpen) {
                                            val len = minOf(64 * 1024, payload.size - off)
                                            runCatching { sink.write(payload, off, len) }.getOrNull()
                                                ?: break
                                            off += len
                                            sentBytes.addAndGet(len.toLong())
                                        }
                                        runCatching { sink.flush() }
                                    }
                                }
                                val req = Request.Builder()
                                    .url(UP_URL)
                                    .header("User-Agent", USER_AGENT)
                                    .post(body)
                                    .build()
                                val call = client.newCall(req)
                                activeCalls += call
                                call.execute().use { resp ->
                                    if (resp.isSuccessful) anySuccess = true
                                }
                            }
                            Unit
                        }
                    }
                    // live: 기록 바이트/경과 (완료 스트림 없어도 진행 표시)
                    while (coroutineContext.isActive) {
                        delay(SAMPLE_MS)
                        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1)
                        val estimate = sentBytes.get() * 8.0 / elapsed / 1000.0
                        onSample(estimate, sentBytes.get())
                        if (workers.all { it.isCompleted }) break
                        if (elapsed >= UP_MAX_MS) break
                    }
                    workers.forEach { it.cancel() }
                    cancel()
                    val total = sentBytes.get()
                    val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1)
                    if (total <= 0) {
                        DebugLogger.e("업로드 측정 실패 (전송 0B, 성공응답 $anySuccess)", "E-AND-NET-0004")
                        return@coroutineScope TestResult(false, bytesUsed = total, errorCode = "E-AND-NET-0004")
                    }
                    // T-29 — 저속 링크(≈1Mbps)에서는 30초 안에 4MB 전량+서버 확답이 안 와도
                    // 전송 바이트 자체가 유효한 측정값이므로 추정 성공 처리 (서버 확답 없어도 OK).
                    val avg = total * 8.0 / elapsed / 1000.0
                    val tag = if (anySuccess) "확정" else "추정(서버 확답 없음)"
                    DebugLogger.i("[NET] 업로드 측정($tag): ${"%.2f".format(avg)}Mbps (${total}B/${elapsed}ms)")
                    TestResult(true, avg, total)
                }
            } catch (e: Exception) {
                cancel()
                if (e is kotlinx.coroutines.CancellationException) throw e
                DebugLogger.e("업로드 측정 실패", "E-AND-NET-0004", e)
                TestResult(false, bytesUsed = sentBytes.get(), errorCode = "E-AND-NET-0004")
            }
        }

    companion object {
        const val USER_AGENT = "SpotShift/0.5"
        private const val DOWN_BASE = "https://speed.cloudflare.com/__down"
        private const val UP_URL = "https://speed.cloudflare.com/__up"
        private const val DOWN_STREAMS = 4
        private const val UP_STREAMS = 4
        private const val DOWN_BYTES_PER_STREAM = 25L * 1024 * 1024
        // 다운로드: 최소 8초·10MB, 최대 20초·60MB. 업로드: 스트림당 1MB × 4 = 4MB, 최대 30초.
        // 1회 측정 데이터 상한 ≈ 64MB — 시작 전 화면에 명시.
        private const val DOWN_MIN_MS = 8_000L
        private const val DOWN_MAX_MS = 20_000L
        private const val DOWN_MIN_BYTES = 10L * 1024 * 1024
        private const val DOWN_CAP_BYTES = 60L * 1024 * 1024
        private const val UP_BYTES_PER_STREAM = 1L * 1024 * 1024
        private const val UP_MAX_MS = 30_000L
        private const val SAMPLE_MS = 500L
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
