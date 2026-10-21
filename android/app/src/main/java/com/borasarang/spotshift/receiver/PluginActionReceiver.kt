package com.borasarang.spotshift.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.core.RotationGate
import com.borasarang.spotshift.core.ShizukuManager
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.plugin.PluginContract
import com.borasarang.spotshift.plugin.PluginLog
import com.borasarang.spotshift.scheduler.RemoteRotationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * T-35 — 연동 계약 v2 §4 액션 수신기 (L2).
 *
 *   adb shell am broadcast -a com.borasarang.spotshift.PLUGIN_ACTION --es cmd autorotate
 *
 * 브로드캐스트라 UI 전면 전환 없음 (v1 MainActivity 경로는 SDK §4.3에 따라 제거).
 *
 * T-52 — 수신기는 **접수/거부만** 한다:
 * - 이미 다른 IP 변경이 진행 중이면 즉시 `E-AND-PLG-0004` BUSY 한 줄로 거부 (진행 중 작업은 계속).
 * - 아니면 게이트를 선점하고 [RemoteRotationWorker]에 실행을 위임한 뒤 즉시 반환.
 *   (v2까지 goAsync로 직접 회전 → 30~100초 소요로 브로드캐스트 타임아웃 ANR 실측)
 * 결과는 워커가 계약 §5 `[REMOTE]` 한 줄로 보고한다.
 */
class PluginActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PluginContract.ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAction(context.applicationContext, intent.getStringExtra("cmd"))
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleAction(context: Context, rawCmd: String?) {
        val cmd = PluginContract.sanitizeCmd(rawCmd)
        if (cmd != PluginContract.CMD_AUTOROTATE) {
            PluginLog.remote(PluginContract.remoteInvalid(cmd))
            return
        }
        if (!ShizukuManager.isReady) {
            PluginLog.remote(
                PluginContract.remoteFail("E-AND-PLG-0001", "shizuku not ready")
            )
            return
        }
        val prefs = Prefs(context)
        // fresh read (콜드스타트 초기값 오판 방지)
        val cfg = prefs.getConfig()
        if (!cfg.pluginAllowed) {
            PluginLog.remote(PluginContract.remoteDenied())
            return
        }
        // T-52 — 진행 중이면 즉시 BUSY 응답 (진행 중인 작업은 그대로 계속)
        if (!RotationGate.tryEnter(RotationGate.Source.REMOTE)) {
            DebugLogger.w("[GATE] 이미 IP 변경 진행 중 — 원격 요청 거부 (${RotationGate.describeBusy()})")
            PluginLog.remote(PluginContract.remoteBusy(RotationGate.describeBusy()))
            return
        }
        try {
            RemoteRotationWorker.enqueue(context)
            DebugLogger.feature("PluginActionReceiver", "원격 회전 예약 (워커 위임)")
        } catch (e: Exception) {
            // 예약 실패 시 게이트를 즉시 반납해 다음 요청이 막히지 않게 한다.
            RotationGate.exit()
            DebugLogger.e("원격 회전 예약 실패", "E-AND-PLG-0003", e)
            PluginLog.remote(PluginContract.remoteFail("E-AND-PLG-0003", "enqueue failed"))
        }
    }
}
