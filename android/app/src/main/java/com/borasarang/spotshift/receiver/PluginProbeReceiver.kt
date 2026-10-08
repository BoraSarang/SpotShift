package com.borasarang.spotshift.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.plugin.PluginContract
import com.borasarang.spotshift.plugin.PluginLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * T-33 — 연동 계약 discovery 프로브 수신기. T-35 v2 형식으로 응답.
 * (SDK 원천: RelayConsole docs/PLUGIN_SDK.md §2)
 *
 *   adb shell am broadcast -a com.borasarang.spotshift.PLUGIN_PROBE \
 *     -n com.borasarang.spotshift/.receiver.PluginProbeReceiver
 *
 * 응답은 logcat(TAG SpotShift)으로만 전달. `연동 허용` OFF여도 응답한다.
 */
class PluginProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PluginContract.ACTION_PROBE) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allowed = runCatching {
                    Prefs(context.applicationContext).getConfig().pluginAllowed
                }.getOrDefault(true)
                PluginLog.plugin(PluginContract.probeLine(allowed))
            } finally {
                pending.finish()
            }
        }
    }
}
