package com.borasarang.spotshift

import android.util.Log

object DebugLogger {

    private const val TAG = "SpotShift"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun i(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun e(message: String, errorCode: String? = null, throwable: Throwable? = null) {
        val prefix = if (errorCode != null) "[$errorCode] " else ""
        Log.e(TAG, prefix + message, throwable)
    }

    fun feature(featureName: String, detail: String = "") {
        i("[FEATURE] $featureName 진입 $detail".trim())
    }

    fun perf(operation: String, durationMs: Long) {
        i("[PERF] $operation ${durationMs}ms")
    }
}
