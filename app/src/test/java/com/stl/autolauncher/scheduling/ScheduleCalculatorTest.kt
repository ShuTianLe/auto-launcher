package com.stl.autolauncher.scheduling

import com.stl.autolauncher.data.RepeatRule
import com.stl.autolauncher.data.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ScheduleCalculatorTest {
    private val zoneId: ZoneId = ZoneId.of("UTC")

    @Test
    fun multipleSkipDatesAreAppliedIndependently() = runBlocking {
        val skipDates = setOf(
            LocalDate.of(2026, 6, 25),
            LocalDate.of(2026, 6, 26),
        )

        val next = ScheduleCalculator.findNextOccurrence(
            task = task(),
            nowMillis = millis(2026, 6, 25, 8, 0),
            zoneId = zoneId,
            skipDates = skipDates,
            workdayResolver = { true },
            randomOffsetMinutes = { 0 },
        )

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 6, 27), next!!.scheduledDate)
        assertEquals(LocalDate.of(2026, 6, 27), ScheduleCalculator.actualDateFor(next.triggerAtMillis, zoneId))
    }

    @Test
    fun pastSkipDateDoesNotAffectToday() = runBlocking {
        val next = ScheduleCalculator.findNextOccurrence(
            task = task(),
            nowMillis = millis(2026, 6, 25, 8, 0),
            zoneId = zoneId,
            skipDates = setOf(LocalDate.of(2026, 6, 24)),
            workdayResolver = { true },
            randomOffsetMinutes = { 0 },
        )

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 6, 25), next!!.scheduledDate)
    }

    @Test
    fun skipDateUsesActualTriggerDateWhenRandomWindowCrossesMidnight() = runBlocking {
        val next = ScheduleCalculator.findNextOccurrence(
            task = task(hour = 23, minute = 50, randomWindowMinutes = 20),
            nowMillis = millis(2026, 6, 25, 23, 0),
            zoneId = zoneId,
            skipDates = setOf(LocalDate.of(2026, 6, 26)),
            workdayResolver = { true },
            randomOffsetMinutes = { 15 },
        )

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 6, 26), next!!.scheduledDate)
        assertEquals(LocalDate.of(2026, 6, 27), ScheduleCalculator.actualDateFor(next.triggerAtMillis, zoneId))
    }

    @Test
    fun existingScheduledOccurrenceIsSkippedAfterSkipDateIsAdded() = runBlocking {
        val scheduledTrigger = millis(2026, 6, 25, 9, 12)
        val next = ScheduleCalculator.findNextOccurrence(
            task = task(
                scheduledDate = "2026-06-25",
                nextTriggerAtMillis = scheduledTrigger,
                scheduledOffsetMinutes = 12,
            ),
            nowMillis = millis(2026, 6, 25, 8, 0),
            zoneId = zoneId,
            skipDates = setOf(LocalDate.of(2026, 6, 25)),
            workdayResolver = { true },
            randomOffsetMinutes = { 0 },
        )

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 6, 26), next!!.scheduledDate)
        assertEquals(0, next.offsetMinutes)
    }

    @Test
    fun completedRandomWindowDoesNotRescheduleSameScheduledDate() = runBlocking {
        val next = ScheduleCalculator.findNextOccurrence(
            task = task(
                hour = 20,
                minute = 0,
                randomWindowMinutes = 90,
                lastHandledScheduledDate = "2026-06-25",
            ),
            nowMillis = millis(2026, 6, 25, 20, 23),
            zoneId = zoneId,
            skipDates = emptySet(),
            workdayResolver = { true },
            randomOffsetMinutes = { 23 },
        )

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 6, 26), next!!.scheduledDate)
    }

    @Test
    fun handledExistingOccurrenceMovesToNextScheduledDate() = runBlocking {
        val next = ScheduleCalculator.findNextOccurrence(
            task = task(
                scheduledDate = "2026-06-25",
                nextTriggerAtMillis = millis(2026, 6, 25, 20, 30),
                scheduledOffsetMinutes = 30,
                lastHandledScheduledDate = "2026-06-25",
            ),
            nowMillis = millis(2026, 6, 25, 19, 0),
            zoneId = zoneId,
            skipDates = emptySet(),
            workdayResolver = { true },
            randomOffsetMinutes = { 0 },
        )

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 6, 26), next!!.scheduledDate)
    }

    @Test
    fun completedCrossMidnightWindowAllowsNextScheduledDate() = runBlocking {
        val next = ScheduleCalculator.findNextOccurrence(
            task = task(
                hour = 23,
                minute = 50,
                randomWindowMinutes = 20,
                lastHandledScheduledDate = "2026-06-25",
            ),
            nowMillis = millis(2026, 6, 26, 0, 7),
            zoneId = zoneId,
            skipDates = emptySet(),
            workdayResolver = { true },
            randomOffsetMinutes = { 15 },
        )

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 6, 26), next!!.scheduledDate)
        assertEquals(LocalDate.of(2026, 6, 27), ScheduleCalculator.actualDateFor(next.triggerAtMillis, zoneId))
    }

    @Test
    fun sevenDayPreviewMarksMultipleSkippedDates() = runBlocking {
        val preview = ScheduleCalculator.buildSevenDayPreview(
            task = task(randomWindowMinutes = 30),
            startDate = LocalDate.of(2026, 6, 25),
            skipDates = setOf(LocalDate.of(2026, 6, 26), LocalDate.of(2026, 6, 28)),
            workdayResolver = { true },
        )

        assertEquals(SchedulePreviewStatus.SCHEDULED, preview[0].status)
        assertEquals(SchedulePreviewStatus.SKIPPED, preview[1].status)
        assertEquals(SchedulePreviewStatus.SCHEDULED, preview[2].status)
        assertEquals(SchedulePreviewStatus.SKIPPED, preview[3].status)
        assertEquals(7, preview.size)
    }

    @Test
    fun sevenDayPreviewShowsNoTaskForUnselectedWeeklyDays() = runBlocking {
        val preview = ScheduleCalculator.buildSevenDayPreview(
            task = task(repeatRule = RepeatRule.WEEKLY, daysOfWeekCsv = "1"),
            startDate = LocalDate.of(2026, 6, 23),
            skipDates = emptySet(),
            workdayResolver = { true },
        )

        assertEquals(SchedulePreviewStatus.NO_TASK, preview.first().status)
    }

    @Test
    fun sevenDayPreviewShowsWaitingWhenWorkdayDataIsMissing() = runBlocking {
        val preview = ScheduleCalculator.buildSevenDayPreview(
            task = task(repeatRule = RepeatRule.WORKDAY_CN),
            startDate = LocalDate.of(2026, 6, 25),
            skipDates = emptySet(),
            workdayResolver = { null },
        )

        assertEquals(SchedulePreviewStatus.WAITING_HOLIDAY_DATA, preview.first().status)
    }

    private fun task(
        repeatRule: RepeatRule = RepeatRule.DAILY,
        daysOfWeekCsv: String = "",
        hour: Int = 9,
        minute: Int = 0,
        randomWindowMinutes: Int = 0,
        scheduledDate: String? = null,
        nextTriggerAtMillis: Long? = null,
        scheduledOffsetMinutes: Int? = null,
        lastHandledScheduledDate: String? = null,
    ): TaskEntity {
        return TaskEntity(
            id = 1L,
            name = "Task",
            hour = hour,
            minute = minute,
            randomWindowMinutes = randomWindowMinutes,
            repeatRule = repeatRule,
            daysOfWeekCsv = daysOfWeekCsv,
            targetPackage = "com.example.target",
            targetAppLabel = "Target",
            waitDurationSeconds = 30,
            scheduledDate = scheduledDate,
            nextTriggerAtMillis = nextTriggerAtMillis,
            scheduledOffsetMinutes = scheduledOffsetMinutes,
            lastHandledScheduledDate = lastHandledScheduledDate,
        )
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
