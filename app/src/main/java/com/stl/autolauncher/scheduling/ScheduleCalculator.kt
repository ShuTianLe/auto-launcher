package com.stl.autolauncher.scheduling

import com.stl.autolauncher.data.RepeatRule
import com.stl.autolauncher.data.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

data class ScheduledOccurrence(
    val scheduledDate: LocalDate,
    val triggerAtMillis: Long,
    val offsetMinutes: Int,
)

data class ScheduleWindow(
    val scheduledDate: LocalDate,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
) {
    fun intersects(actualDate: LocalDate): Boolean {
        val dayStart = actualDate.atStartOfDay()
        val dayEnd = actualDate.plusDays(1).atStartOfDay()
        return startsAt.isBefore(dayEnd) && !endsAt.isBefore(dayStart)
    }
}

enum class SchedulePreviewStatus {
    SCHEDULED,
    SKIPPED,
    NO_TASK,
    WAITING_HOLIDAY_DATA,
    DISABLED,
}

data class SchedulePreviewDay(
    val date: LocalDate,
    val status: SchedulePreviewStatus,
    val windows: List<ScheduleWindow> = emptyList(),
)

object ScheduleCalculator {
    private const val NEXT_LOOKAHEAD_DAYS = 400

    suspend fun findNextOccurrence(
        task: TaskEntity,
        nowMillis: Long,
        zoneId: ZoneId,
        skipDates: Set<LocalDate>,
        workdayResolver: suspend (LocalDate) -> Boolean?,
        randomOffsetMinutes: (Int) -> Int = { maxInclusive ->
            if (maxInclusive <= 0) 0 else Random.nextInt(maxInclusive + 1)
        },
    ): ScheduledOccurrence? {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        for (offset in 0..NEXT_LOOKAHEAD_DAYS) {
            val date = today.plusDays(offset.toLong())
            if (matchesRepeatRule(task, date, workdayResolver) != true) continue

            val occurrence = existingOccurrenceForDate(task, date, nowMillis)
                ?: newOccurrenceForDate(task, date, zoneId, randomOffsetMinutes)

            if (occurrence.triggerAtMillis <= nowMillis) continue

            val actualDate = actualDateFor(occurrence.triggerAtMillis, zoneId)
            if (actualDate in skipDates) continue

            return occurrence
        }
        return null
    }

    suspend fun buildSevenDayPreview(
        task: TaskEntity,
        startDate: LocalDate,
        skipDates: Set<LocalDate>,
        workdayResolver: suspend (LocalDate) -> Boolean?,
    ): List<SchedulePreviewDay> {
        val horizon = (0 until 7).map { startDate.plusDays(it.toLong()) }
        if (!task.enabled) {
            return horizon.map { date -> SchedulePreviewDay(date = date, status = SchedulePreviewStatus.DISABLED) }
        }

        val windowsByDate = linkedMapOf<LocalDate, MutableList<ScheduleWindow>>()
        val unknownDates = mutableSetOf<LocalDate>()
        val lookbackDays = (task.randomWindowMinutes.coerceAtLeast(0) / (24 * 60)) + 1
        val firstBaseDate = startDate.minusDays(lookbackDays.toLong())
        val lastBaseDate = startDate.plusDays(6)

        var cursor = firstBaseDate
        while (!cursor.isAfter(lastBaseDate)) {
            val match = matchesRepeatRule(task, cursor, workdayResolver)
            val window = windowForDate(task, cursor)
            val intersectingDates = horizon.filter(window::intersects)
            when {
                match == true -> intersectingDates.forEach { date ->
                    windowsByDate.getOrPut(date) { mutableListOf() } += window
                }
                match == null -> unknownDates += intersectingDates
            }
            cursor = cursor.plusDays(1)
        }

        return horizon.map { date ->
            val windows = windowsByDate[date].orEmpty().sortedBy { it.startsAt }
            val status = when {
                windows.isNotEmpty() && date in skipDates -> SchedulePreviewStatus.SKIPPED
                windows.isNotEmpty() -> SchedulePreviewStatus.SCHEDULED
                date in unknownDates -> SchedulePreviewStatus.WAITING_HOLIDAY_DATA
                else -> SchedulePreviewStatus.NO_TASK
            }
            SchedulePreviewDay(date = date, status = status, windows = windows)
        }
    }

    fun actualDateFor(triggerAtMillis: Long, zoneId: ZoneId): LocalDate {
        return Instant.ofEpochMilli(triggerAtMillis).atZone(zoneId).toLocalDate()
    }

    private fun existingOccurrenceForDate(task: TaskEntity, date: LocalDate, nowMillis: Long): ScheduledOccurrence? {
        val nextTriggerAtMillis = task.nextTriggerAtMillis ?: return null
        if (task.scheduledDate != date.toString() || nextTriggerAtMillis <= nowMillis) return null
        return ScheduledOccurrence(
            scheduledDate = date,
            triggerAtMillis = nextTriggerAtMillis,
            offsetMinutes = task.scheduledOffsetMinutes ?: 0,
        )
    }

    private fun newOccurrenceForDate(
        task: TaskEntity,
        date: LocalDate,
        zoneId: ZoneId,
        randomOffsetMinutes: (Int) -> Int,
    ): ScheduledOccurrence {
        val maxOffset = task.randomWindowMinutes.coerceAtLeast(0)
        val offsetMinutes = randomOffsetMinutes(maxOffset).coerceIn(0, maxOffset)
        val triggerAt = LocalDateTime.of(date, LocalTime.of(task.hour, task.minute))
            .plusMinutes(offsetMinutes.toLong())
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        return ScheduledOccurrence(date, triggerAt, offsetMinutes)
    }

    private fun windowForDate(task: TaskEntity, date: LocalDate): ScheduleWindow {
        val start = LocalDateTime.of(date, LocalTime.of(task.hour, task.minute))
        return ScheduleWindow(
            scheduledDate = date,
            startsAt = start,
            endsAt = start.plusMinutes(task.randomWindowMinutes.coerceAtLeast(0).toLong()),
        )
    }

    private suspend fun matchesRepeatRule(
        task: TaskEntity,
        date: LocalDate,
        workdayResolver: suspend (LocalDate) -> Boolean?,
    ): Boolean? {
        return when (task.repeatRule) {
            RepeatRule.DAILY -> true
            RepeatRule.WEEKLY -> date.dayOfWeek in task.weeklyDays()
            RepeatRule.WORKDAY_CN -> workdayResolver(date)
        }
    }
}
