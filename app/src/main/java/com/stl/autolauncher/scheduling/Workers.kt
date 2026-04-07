package com.stl.autolauncher.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.stl.autolauncher.AutoLauncherApp
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class HolidaySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AutoLauncherApp).container
        val currentYear = LocalDate.now().year
        val years = listOf(currentYear, currentYear + 1)
        val summary = container.holidayRepository.syncYears(years)
        val hasCurrentCache = container.holidayRepository.hasLocalCoverage(currentYear)
        val hasNextCache = container.holidayRepository.hasLocalCoverage(currentYear + 1)
        val okCurrent = summary.resultFor(currentYear)?.success == true || hasCurrentCache
        val okNext = summary.resultFor(currentYear + 1)?.success == true || hasNextCache
        container.taskScheduler.reconcileAllTasks()
        return if (okCurrent || okNext) {
            Result.success(
                workDataOf(
                    "status" to if (summary.results.all { it.success }) "success" else "partial",
                    "reason" to summary.firstFailureReason(),
                ),
            )
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "holiday_sync_periodic"
        private const val UNIQUE_IMMEDIATE_WORK = "holiday_sync_immediate"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<HolidaySyncWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun enqueueNow(context: Context, reason: String) {
            val request = OneTimeWorkRequestBuilder<HolidaySyncWorker>()
                .setInputData(workDataOf("reason" to reason))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

class TaskRescheduleWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AutoLauncherApp).container
        container.taskScheduler.reconcileAllTasks()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "task_reschedule_once"

        fun enqueueNow(context: Context, reason: String) {
            val request = OneTimeWorkRequestBuilder<TaskRescheduleWorker>()
                .setInputData(workDataOf("reason" to reason))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
