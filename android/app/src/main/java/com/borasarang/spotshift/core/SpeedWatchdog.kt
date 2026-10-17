package com.borasarang.spotshift.core

import android.net.TrafficStats
import com.borasarang.spotshift.DebugLogger
import kotlinx.coroutines.delay

/**
 * T-42 — 속도 감시 (절약 모드).
 * 패시브 우선: 모바일 인터페이스 실사용량을 30초 창(window)으로 환산한다.
 * 평소 소비 0바이트. 유휴(사용량 미미)면 ipify 생존 확인으로 갈음하고,
 * 저속이거나 생존 실패일 때만 로테이션을 트리거한다.
 */
class SpeedWatchdog {

    sealed interface Verdict {
        /** 유휴 + 생존 확인 — 조치 없음 */
        data object IdleHealthy : Verdict
        /** 실사용 중 기준 달성 — 조치 없음 */
        data class Healthy(val mbps: Double) : Verdict
        /** 실사용 중 기준 미달 — 로테이션 트리거 */
        data class Low(val mbps: Double) : Verdict
        /** 유휴인데 생존 확인 실패 — 로테이션 트리거 */
        data object LivenessFailed : Verdict
    }

    suspend fun check(thresholdMbps: Float): Verdict {
        val before = mobileBytes()
        if (before < 0) {
            DebugLogger.i("[WATCH] TrafficStats 미지원 — 생존 확인으로 갈음")
            return if (IpVerifier().fetchPublicIp() == null) Verdict.LivenessFailed
            else Verdict.IdleHealthy
        }
        delay(WINDOW_MILLIS)
        val after = mobileBytes()
        if (after < 0) return Verdict.IdleHealthy
        val deltaBytes = after - before
        if (deltaBytes < IDLE_BYTES) {
            // 유휴: 속도 판단 불가 → 생존 확인만 (약 1KB)
            DebugLogger.d("[WATCH] 유휴 (30초 ${deltaBytes}B) — 생존 확인")
            return if (IpVerifier().fetchPublicIp() == null) Verdict.LivenessFailed
            else Verdict.IdleHealthy
        }
        val mbps = deltaBytes * 8.0 / (WINDOW_MILLIS / 1000.0) / 1_000_000.0
        DebugLogger.feature("SpeedWatchdog", "패시브 %.2fMbps (30초 %dB)".format(mbps, deltaBytes))
        return if (mbps < thresholdMbps) Verdict.Low(mbps) else Verdict.Healthy(mbps)
    }

    private fun mobileBytes(): Long {
        val rx = TrafficStats.getMobileRxBytes()
        val tx = TrafficStats.getMobileTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return -1
        return rx + tx
    }

    companion object {
        private const val WINDOW_MILLIS = 30_000L
        /** 30초 50KB 미만이면 유휴로 간주 */
        private const val IDLE_BYTES = 50L * 1024L
    }
}
