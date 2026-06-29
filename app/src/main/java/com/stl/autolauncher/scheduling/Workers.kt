package com.stl.autolauncher.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.stl.autolauncher.AutoLauncherApp
import com.stl.autolauncher.data.ExecutionStatus
import com.stl.autolauncher.data.RemoteCommand
import com.stl.autolauncher.data.RemoteCommandResult
import com.stl.autolauncher.data.RepeatRule
import com.stl.autolauncher.data.TaskEntity
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

class RemoteSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val container by lazy { (applicationContext as AutoLauncherApp).container }

    override suspend fun doWork(): Result {
        val store = container.remoteDeviceStore
        return try {
            val registered = container.remoteSyncRepository.registerIfNeeded()
            if (!registered) {
                store.markSync(false, "远程设备注册失败")
                enqueueNext(applicationContext)
                return Result.success()
            }

            val pollResult = container.remoteSyncRepository.poll()
            if (!pollResult.success) {
                store.markSync(false, pollResult.message)
                enqueueNext(applicationContext)
                return Result.success()
            }

            val results = pollResult.commands.map { command ->
                applyCommand(command)
            }
            store.setPendingResults(results)
            if (results.isNotEmpty()) {
                val confirmResult = container.remoteSyncRepository.poll()
                if (confirmResult.success) {
                    store.setPendingResults(emptyList())
                }
            }
            store.markSync(true, if (results.isEmpty()) "远程同步成功" else "已处理 ${results.size} 条远程命令")
            enqueueNext(applicationContext)
            Result.success()
        } catch (error: Exception) {
            store.markSync(false, error.message?.takeIf { it.isNotBlank() } ?: "远程同步失败")
            enqueueNext(applicationContext)
            Result.success()
        }
    }

    private suspend fun applyCommand(command: RemoteCommand): RemoteCommandResult {
        val store = container.remoteDeviceStore
        if (store.isCommandApplied(command.id)) {
            return RemoteCommandResult(command.id, "applied")
        }

        return runCatching {
            when (command.type) {
                "CREATE_TASK" -> applyCreateTask(command)
                "SET_TASK_ENABLED" -> applySetTaskEnabled(command)
                "ADD_SKIP_DATES" -> applyAddSkipDates(command)
                "REMOVE_SKIP_DATE" -> applyRemoveSkipDate(command)
                else -> error("未知远程命令 ${command.type}")
            }
            store.rememberAppliedCommand(command.id)
            RemoteCommandResult(command.id, "applied")
        }.getOrElse { error ->
            RemoteCommandResult(command.id, "failed", error.message ?: "命令执行失败")
        }
    }

    private suspend fun applyCreateTask(command: RemoteCommand) {
        val payload = command.payload
        val name = payload.optString("name").trim()
        val targetPackage = payload.optString("targetPackage").trim()
        val targetAppLabel = payload.optString("targetAppLabel").trim()
        if (name.isBlank() || targetPackage.isBlank() || targetAppLabel.isBlank()) {
            error("新建任务参数不完整")
        }

        val repeatRule = runCatching { RepeatRule.valueOf(payload.optString("repeatRule")) }
            .getOrDefault(RepeatRule.WORKDAY_CN)
        val weeklyDays = payload.optJSONArray("weeklyDays").toCsv()
        val now = System.currentTimeMillis()
        val task = TaskEntity(
            id = 0,
            name = name,
            hour = payload.optInt("hour").coerceIn(0, 23),
            minute = payload.optInt("minute").coerceIn(0, 59),
            randomWindowMinutes = payload.optInt("randomWindowMinutes").coerceIn(0, 240),
            repeatRule = repeatRule,
            daysOfWeekCsv = if (repeatRule == RepeatRule.WEEKLY) weeklyDays else "",
            targetPackage = targetPackage,
            targetAppLabel = targetAppLabel,
            waitDurationSeconds = payload.optInt("waitDurationSeconds").coerceIn(1, 7200),
            enabled = payload.optBoolean("enabled", true),
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        val existingTask = container.taskRepository.getAllTasks().firstOrNull { current ->
            current.name == task.name &&
                current.hour == task.hour &&
                current.minute == task.minute &&
                current.randomWindowMinutes == task.randomWindowMinutes &&
                current.repeatRule == task.repeatRule &&
                current.daysOfWeekCsv == task.daysOfWeekCsv &&
                current.targetPackage == task.targetPackage &&
                current.waitDurationSeconds == task.waitDurationSeconds
        }
        val taskId = existingTask?.id ?: container.taskRepository.saveTask(task)
        if (existingTask == null) {
            container.taskScheduler.reconcileTask(taskId)
        }
        if (repeatRule == RepeatRule.WORKDAY_CN) {
            HolidaySyncWorker.enqueueNow(applicationContext, "remote_create_workday_task")
        }
        container.taskRepository.appendLog(
            taskId,
            name,
            ExecutionStatus.SUCCESS,
            if (existingTask == null) "已应用远程新建任务命令" else "远程新建任务已存在，未重复创建",
        )
    }

    private suspend fun applySetTaskEnabled(command: RemoteCommand) {
        val taskId = command.payload.requireTaskId()
        val enabled = command.payload.optBoolean("enabled")
        val task = container.taskRepository.getTask(taskId) ?: error("任务不存在")
        container.taskRepository.setEnabled(taskId, enabled)
        container.taskScheduler.reconcileTask(taskId)
        container.taskRepository.appendLog(taskId, task.name, ExecutionStatus.SUCCESS, "已远程${if (enabled) "启用" else "停用"}任务")
    }

    private suspend fun applyAddSkipDates(command: RemoteCommand) {
        val taskId = command.payload.requireTaskId()
        val task = container.taskRepository.getTask(taskId) ?: error("任务不存在")
        val today = LocalDate.now()
        val dates = command.payload.optJSONArray("dates").toDates()
            .filterNot { it.isBefore(today) }
        dates.forEach { date -> container.taskRepository.addSkipDate(taskId, date) }
        container.taskRepository.prunePastSkipDates(today)
        container.taskScheduler.reconcileTask(taskId)
        container.taskRepository.appendLog(taskId, task.name, ExecutionStatus.SUCCESS, "已应用远程跳过日期 ${dates.joinToString("、")}")
    }

    private suspend fun applyRemoveSkipDate(command: RemoteCommand) {
        val taskId = command.payload.requireTaskId()
        val task = container.taskRepository.getTask(taskId) ?: error("任务不存在")
        val date = LocalDate.parse(command.payload.optString("date"))
        container.taskRepository.deleteSkipDate(taskId, date)
        container.taskScheduler.reconcileTask(taskId)
        container.taskRepository.appendLog(taskId, task.name, ExecutionStatus.SUCCESS, "已删除远程跳过日期 $date")
    }

    companion object {
        private const val UNIQUE_WORK = "remote_sync_once"
        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueueNow(context: Context, reason: String) {
            val request = OneTimeWorkRequestBuilder<RemoteSyncWorker>()
                .setInputData(workDataOf("reason" to reason))
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }

        fun enqueueNext(context: Context) {
            val request = OneTimeWorkRequestBuilder<RemoteSyncWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

private val remoteDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private fun JSONArray?.toCsv(): String {
    if (this == null) return ""
    return (0 until length())
        .mapNotNull { index -> optInt(index).takeIf { it in 1..7 } }
        .distinct()
        .sorted()
        .joinToString(",")
}

private fun JSONArray?.toDates(): List<LocalDate> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        runCatching { LocalDate.parse(optString(index), remoteDateFormatter) }.getOrNull()
    }.distinct().sorted()
}

private fun org.json.JSONObject.requireTaskId(): Long {
    val value = optString("taskId")
    return value.toLongOrNull() ?: error("任务 ID 无效")
}
