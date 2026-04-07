package com.stl.autolauncher.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.stl.autolauncher.data.HolidayRepository
import com.stl.autolauncher.data.RepeatRule
import com.stl.autolauncher.data.TaskDao
import com.stl.autolauncher.data.TaskEntity
import com.stl.autolauncher.receiver.TaskAlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

class TaskScheduler(
    private val context: Context,
    private val taskDao: TaskDao,
    private val holidayRepository: HolidayRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val zoneId = ZoneId.systemDefault()

    suspend fun reconcileAllTasks(nowMillis: Long = System.currentTimeMillis()) {
        val tasks = taskDao.getAll()
        tasks.forEach { task -> reconcileTask(task.id, nowMillis) }
    }

    suspend fun reconcileTask(taskId: Long, nowMillis: Long = System.currentTimeMillis()) {
        val task = taskDao.getById(taskId) ?: return
        if (!task.enabled) {
            cancelTask(taskId)
            taskDao.updateSchedule(taskId, null, null, null, System.currentTimeMillis())
            return
        }
        val next = computeNextOccurrence(task, nowMillis)
        if (next == null) {
            cancelTask(taskId)
            taskDao.updateSchedule(taskId, null, null, null, System.currentTimeMillis())
            return
        }
        taskDao.updateSchedule(
            taskId = taskId,
            nextTriggerAtMillis = next.triggerAtMillis,
            scheduledDate = next.scheduledDate.toString(),
            scheduledOffsetMinutes = next.offsetMinutes,
            updatedAtMillis = System.currentTimeMillis(),
        )
        scheduleAlarm(taskId, next.triggerAtMillis)
    }

    suspend fun onTaskCompleted(taskId: Long) {
        val task = taskDao.getById(taskId) ?: return
        cancelTask(taskId)
        val cleared = task.copy(nextTriggerAtMillis = null, scheduledDate = null, scheduledOffsetMinutes = null)
        taskDao.upsert(cleared)
        reconcileTask(taskId, System.currentTimeMillis() + 1_000)
    }

    fun cancelTask(taskId: Long) {
        alarmManager.cancel(buildPendingIntent(taskId))
    }

    private suspend fun computeNextOccurrence(task: TaskEntity, nowMillis: Long): ScheduledOccurrence? = withContext(Dispatchers.Default) {
        val today = LocalDate.now(zoneId)
        for (offset in 0..400) {
            val date = today.plusDays(offset.toLong())
            val matches = when (task.repeatRule) {
                RepeatRule.DAILY -> true
                RepeatRule.WEEKLY -> date.dayOfWeek in task.weeklyDays()
                RepeatRule.WORKDAY_CN -> holidayRepository.isChineseWorkday(date) == true
            }
            if (!matches) continue

            val base = LocalDateTime.of(date, LocalTime.of(task.hour, task.minute))
            val existingSchedule = if (
                task.scheduledDate == date.toString() &&
                task.nextTriggerAtMillis != null &&
                task.nextTriggerAtMillis > nowMillis
            ) {
                ScheduledOccurrence(date, task.nextTriggerAtMillis, task.scheduledOffsetMinutes ?: 0)
            } else {
                val offsetMinutes = if (task.randomWindowMinutes <= 0) 0 else Random.nextInt(task.randomWindowMinutes + 1)
                val triggerAt = base.plusMinutes(offsetMinutes.toLong()).atZone(zoneId).toInstant().toEpochMilli()
                ScheduledOccurrence(date, triggerAt, offsetMinutes)
            }

            if (existingSchedule.triggerAtMillis > nowMillis) {
                return@withContext existingSchedule
            }
        }
        null
    }

    private fun scheduleAlarm(taskId: Long, triggerAtMillis: Long) {
        val pendingIntent = buildPendingIntent(taskId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun buildPendingIntent(taskId: Long): PendingIntent {
        val intent = Intent(context, TaskAlarmReceiver::class.java)
            .putExtra(TaskAlarmReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class ScheduledOccurrence(
        val scheduledDate: LocalDate,
        val triggerAtMillis: Long,
        val offsetMinutes: Int,
    )
}
