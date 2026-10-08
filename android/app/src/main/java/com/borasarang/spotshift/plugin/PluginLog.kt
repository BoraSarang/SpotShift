package com.borasarang.spotshift.plugin

import android.util.Log

/**
 * T-35 — 연동 계약 로그 배출구 (SDK v2 §8).
 *
 * 계약 줄([PLUGIN]·[REMOTE]·[EVENT])은 android.util.Log 직접 출력만 사용한다.
 * DebugLogger.i/d는 릴리즈에서 DEBUG 조기반환으로 증발하므로 계약 로그에 사용 금지.
 * 한 사건 한 줄 (단일 emit).
 */
object PluginLog {
    const val TAG = "SpotShift"

    fun plugin(line: String) = Log.i(TAG, line)
    fun remote(line: String) = Log.i(TAG, line)
    fun event(line: String) = Log.i(TAG, line)
}
