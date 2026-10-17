package com.borasarang.spotshift.scheduler

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.borasarang.spotshift.DebugLogger
import java.util.concurrent.TimeUnit

/**
 * T-42 — 감시 주기 등록/취소 단일 진입점.
 * 회전 주기를 공유하되 최소 60분 (측정 30초 + 여유).
 */
object WatchdogSchedule {

    fun sync(context: Context, enabled: Boolean, watchOn: Boolean, intervalMinutes: Int) {
        if (!enabled || !watchOn) {
            cancel(context)
            return
        }
        val minutes = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES).toLong()
        val req = PeriodicWorkRequestBuilder<WatchdogWorker>(minutes, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WatchdogWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        DebugLogger.feature("WatchdogSchedule", "감시 등록 ${minutes}분")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WatchdogWorker.WORK_NAME)
        DebugLogger.feature("WatchdogSchedule", "감시 취소")
    }

    private const val MIN_INTERVAL_MINUTES = 60
}
