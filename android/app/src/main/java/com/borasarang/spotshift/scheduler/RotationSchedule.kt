package com.borasarang.spotshift.scheduler

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.borasarang.spotshift.DebugLogger
import java.util.concurrent.TimeUnit

/**
 * v0.5 — WorkManager 주기 등록/취소 단일 진입점.
 * 주기는 15분 하한 (WorkManager Periodic 최소 간격). 15분 미만 저장값은 강제 올림.
 */
object RotationSchedule {

    const val PERIODIC_NAME = "spotshift_periodic"
    private const val ONCE_NAME = "spotshift_once"

    fun enqueuePeriodic(context: Context, intervalMinutes: Int) {
        val minutes = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES).toLong()
        val req = PeriodicWorkRequestBuilder<RotationWorker>(minutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(RotationWorker.KEY_TICK to true))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        DebugLogger.feature("RotationSchedule", "주기 등록 ${minutes}분")
    }

    /**
     * 실제 1회 실행 (주기 틱이 위임하는 expedited 작업 — 백그라운드 FGS 허용 경로).
     * 포그라운드 수동 변경(HomeViewModel.manualRotate)은 인프로세스로 유지하므로 여기 쓰지 않는다.
     */
    fun enqueueOnce(context: Context) {
        val req = OneTimeWorkRequestBuilder<RotationWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(RotationWorker.KEY_TICK to false))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(ONCE_NAME)
        DebugLogger.feature("RotationSchedule", "예약 취소")
    }

    private const val MIN_INTERVAL_MINUTES = 15
}
