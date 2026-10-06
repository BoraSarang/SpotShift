package com.borasarang.spotshift.ui.components

/**
 * v0.5 (T-27) — Mbps 옆 B/s 병기. "24.6 Mbps (3.1MB/s)", 저속은 KB/s.
 */
fun formatRate(mbps: Double): String {
    val mbs = mbps / 8.0
    return if (mbs >= 1) {
        "%.1fMB/s".format(mbs)
    } else {
        "%.0fKB/s".format(mbs * 1024)
    }
}

fun formatMbps(mbps: Double): String = "%.1f Mbps (%s)".format(mbps, formatRate(mbps))
