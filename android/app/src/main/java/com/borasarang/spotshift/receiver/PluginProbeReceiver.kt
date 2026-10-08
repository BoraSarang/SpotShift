package com.borasarang.spotshift.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * T-33 — 연동 계약 v1 discovery 프로브 수신기.
 *
 * dumpsys는 application meta-data를 출력하지 않아 명찰을 읽을 수 없다.
 * 대신 명시적 브로드캐스트로 능력을 질의한다 (앱 꺼져 있어도 프로세스 기동 후 응답,
 * UI 없음, 상시 연결 없음):
 *
 *   adb shell am broadcast -a com.borasarang.spotshift.PLUGIN_PROBE \
 *     -n com.borasarang.spotshift/.receiver.PluginProbeReceiver
 *
 * 응답은 logcat(TAG SpotShift)으로만 전달:
 *   [PLUGIN] version=1 actions=autorotate logTag=SpotShift allowed=<true|false>
 *
 * `연동 허용` OFF여도 응답은 한다 (allowed=false로 꺼짐 상태 전달).
 */
class PluginProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_PROBE) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allowed = runCatching {
                    Prefs(context.applicationContext).getConfig().pluginAllowed
                }.getOrDefault(true)
                DebugLogger.i(
                    "[PLUGIN] version=$PLUGIN_VERSION " +
                        "actions=$PLUGIN_ACTIONS " +
                        "logTag=SpotShift " +
                        "allowed=$allowed"
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PROBE = "com.borasarang.spotshift.PLUGIN_PROBE"
        const val PLUGIN_VERSION = 1
        const val PLUGIN_ACTIONS = "autorotate"
    }
}
