package com.borasarang.spotshift.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.MainActivity
import com.borasarang.spotshift.data.Prefs
import kotlinx.coroutines.runBlocking

/**
 * T-41 — 충전기 연결/분리 알림.
 * 테더링 머신은 상시 충전이 생명이라 분리 즉시 알린다 (가드 ON일 때만).
 */
class PowerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_POWER_CONNECTED && action != Intent.ACTION_POWER_DISCONNECTED) return
        runCatching {
            val prefs = Prefs(context.applicationContext)
            if (!runBlocking { prefs.getPowerGuard() }) return
            val connected = action == Intent.ACTION_POWER_CONNECTED
            DebugLogger.feature("PowerReceiver", if (connected) "충전기 연결" else "충전기 분리")
            if (!connected) {
                notifyPower(context.applicationContext, "충전기가 분리되었습니다. 테더링 중 방전에 주의하세요.")
            }
        }.onFailure { e ->
            DebugLogger.e("전원 상태 알림 실패", "E-AND-SRV-0001", e as? Exception)
        }
    }

    private fun notifyPower(app: Context, text: String) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "SpotShift 전원 알림", NotificationManager.IMPORTANCE_DEFAULT)
        )
        runCatching {
            val pi = PendingIntent.getActivity(
                app, 0, Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            nm.notify(
                NOTIF_ID,
                NotificationCompat.Builder(app, CHANNEL)
                    .setSmallIcon(com.borasarang.spotshift.R.drawable.ic_notification)
                    .setContentTitle("SpotShift")
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
            )
        }
        DebugLogger.i("[POWER] 분리 알림 발행")
    }

    companion object {
        private const val CHANNEL = "spotshift_power"
        private const val NOTIF_ID = 1003
    }
}
