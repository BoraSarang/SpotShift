package com.borasarang.spotshift.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.MainActivity
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.service.IpRotationService
import kotlinx.coroutines.runBlocking

/**
 * v0.4 (T-18) — 부팅 시 스케줄 자동 복원.
 * Shizuku는 부팅 후 자동 시작 불가 → 미연결 시 알림으로 재시작을 유도한다.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT) return
        val app = context.applicationContext
        runCatching {
            val prefs = Prefs(app)
            val config = runBlocking { prefs.getConfig() }
            if (!config.enabled || !config.bootAutoStart) {
                DebugLogger.i("[SCH] 부팅 복원 스킵 (enabled=${config.enabled}, bootAuto=${config.bootAutoStart})")
                return
            }
            runCatching {
                val svc = Intent(app, IpRotationService::class.java)
                    .setAction(IpRotationService.ACTION_START)
                ContextCompat.startForegroundService(app, svc)
                DebugLogger.feature("BootReceiver", "서비스 시작 요청")
            }.onFailure { e ->
                DebugLogger.e("부팅 시 서비스 시작 실패", "E-AND-SRV-0001", e as? Exception)
                notifyTapToStart(app, "SpotShift를 탭하여 시작하세요")
            }
            if (!ShizukuManager.isReady) {
                notifyTapToStart(app, "Shizuku를 다시 시작하세요")
            }
        }.onFailure { e ->
            DebugLogger.e("부팅 복원 실패", "E-AND-SCH-0002", e as? Exception)
        }
    }

    private fun notifyTapToStart(app: Context, text: String) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "SpotShift 부팅 알림", NotificationManager.IMPORTANCE_DEFAULT)
        )
        // POST_NOTIFICATIONS 미허용 시 예외 가능 → 조용히 포기
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
        DebugLogger.i("[SCH] 부팅 알림: $text")
    }

    companion object {
        private const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        private const val CHANNEL = "spotshift_boot"
        private const val NOTIF_ID = 1002
    }
}
