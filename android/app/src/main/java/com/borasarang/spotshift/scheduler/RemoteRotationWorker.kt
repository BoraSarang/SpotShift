package com.borasarang.spotshift.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.borasarang.spotshift.DebugLogger
import com.borasarang.spotshift.R
import com.borasarang.spotshift.core.AirplaneController
import com.borasarang.spotshift.core.DataController
import com.borasarang.spotshift.core.HotspotController
import com.borasarang.spotshift.core.IpVerifier
import com.borasarang.spotshift.core.RotationEngine
import com.borasarang.spotshift.core.RotationGate
import com.borasarang.spotshift.core.SpeedChecker
import com.borasarang.spotshift.data.Prefs
import com.borasarang.spotshift.data.RotationConfig
import com.borasarang.spotshift.data.RotationRecord
import com.borasarang.spotshift.plugin.PluginContract
import com.borasarang.spotshift.plugin.PluginLog

/**
 * T-52 — 외부(플러그인) IP 변경 요청 실행 워커.
 *
 * 배경: v2까지는 `PluginActionReceiver`가 `goAsync()`로 회전을 직접 수행했다. 회전은
 * 재시도 포함 30~100초가 걸리는데 브로드캐스트 타임아웃(포그라운드 10초/백그라운드 60초)을
 * 넘겨 **ANR → 프로세스 킬**이 실측됐다 (2026-10-21, A90: `am_anr ... PLUGIN_ACTION`).
 *
 * 그래서 수신기는 즉시 접수/거부만 하고, 실행은 이 워커가 맡는다.
 * - 게이트는 수신기가 선점하고 이 워커가 승계한다 ([RotationGate] 리스).
 * - 이미 다른 작업(수동/주기/감시)이 진행 중이면 `E-AND-PLG-0004` BUSY 한 줄로 거부한다.
 * - 결과는 계약 §5 `[REMOTE]` 한 줄로만 보고한다 (기존 형식 유지).
 */
class RemoteRotationWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val cfg = prefs.getConfig()

        // 수신기가 선점한 리스를 승계한다. 프로세스 재시작 등으로 리스가 없으면 재획득.
        if (!RotationGate.isBusy() && !RotationGate.tryEnter(RotationGate.Source.REMOTE)) {
            PluginLog.remote(PluginContract.remoteBusy(RotationGate.describeBusy()))
            return Result.success()
        }
        try {
            // API 31+ expedited는 잡으로 실행되어 포그라운드 승격이 필수는 아니지만,
            // 실패해도 회전은 계속한다 (RotationWorker와 동일한 방어).
            runCatching { setForeground(foregroundInfo()) }
                .onFailure { e ->
                    DebugLogger.w("[REMOTE] 포그라운드 승격 실패 — 계속 진행")
                    DebugLogger.e("원격 회전 포그라운드 승격 실패", "E-AND-PLG-0003", e as? Exception)
                }
            runRemoteLoop(prefs, cfg)
        } finally {
            RotationGate.exit()
        }
        return Result.success()
    }

    /** 계약 v2 §4와 동일한 루프 (변경 → 속도 측정 → 기준 미달 시 재변경). */
    private suspend fun runRemoteLoop(prefs: Prefs, cfg: RotationConfig) {
        val engine = RotationEngine(
            context = applicationContext,
            dataController = DataController(),
            airplaneController = AirplaneController(applicationContext),
            hotspotController = HotspotController(applicationContext),
            ipVerifier = IpVerifier()
        )
        val speedChecker = SpeedChecker()
        val threshold = cfg.speedThresholdMbps
        val maxAttempts = cfg.speedMaxRechecks.coerceAtLeast(1)
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            val base = engine.rotate(cfg)
            engine.notifySpeedChecking()
            val speed = speedChecker.measure()
            if (speed.success && speed.mbps != null) {
                prefs.updateLastSpeed(speed.mbps.toFloat())
                val label = "%.2f".format(speed.mbps)
                prefs.addRecord(
                    RotationRecord(
                        changed = false,
                        method = RotationRecord.METHOD_SPEED_CHECK,
                        note = "측정 ${label}Mbps"
                    )
                )
                if (speed.mbps >= threshold) {
                    finishAction(prefs, base, speed.mbps, "측정 ${label}Mbps ≥ 기준 ${threshold}Mbps — 달성")
                    return
                }
                if (attempt >= maxAttempts) {
                    finishAction(prefs, base, speed.mbps, "측정 ${label}Mbps < 기준 ${threshold}Mbps — 기준 미달 (IP 변경은 완료)")
                    return
                }
                prefs.addRecord(base.copy(note = "측정 ${label}Mbps < 기준 ${threshold}Mbps — 재변경 ${attempt}/${maxAttempts}"))
                if (base.changed && base.newIp != null) {
                    prefs.updateRotationMeta(System.currentTimeMillis(), base.newIp)
                }
                continue
            }
            finishAction(prefs, base, null, base.errorCode ?: base.note ?: "E-AND-NET-0004")
            return
        }
    }

    private suspend fun finishAction(
        prefs: Prefs,
        base: RotationRecord,
        speedMbps: Double?,
        note: String
    ) {
        prefs.addRecord(base.copy(note = note))
        if (base.changed && base.newIp != null) {
            prefs.updateRotationMeta(System.currentTimeMillis(), base.newIp)
        }
        if (base.changed) {
            PluginLog.remote(
                PluginContract.remoteOk(base.oldIp, base.newIp, speedMbps, note)
            )
        } else {
            PluginLog.remote(
                PluginContract.remoteFail("E-AND-PLG-0003", note)
            )
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo()

    private fun foregroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_REMOTE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMOTE,
                    "원격 IP 변경",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "외부 연동 요청으로 실행되는 IP 변경 상태입니다."
                    setShowBadge(false)
                }
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_REMOTE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("IP 변경 요청 처리 중")
            .setContentText("외부 요청으로 IP를 변경하고 있습니다.")
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "spotshift_remote_once"
        private const val CHANNEL_REMOTE = "spotshift_remote"
        private const val NOTIFICATION_ID = 1004

        /** 수신기에서 호출 — 실행은 워커에 위임하고 즉시 반환한다. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RemoteRotationWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
