package com.stl.autolauncher.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalDate

enum class RepeatRule {
    DAILY,
    WORKDAY_CN,
    WEEKLY,
}

enum class ExecutionStatus {
    STARTED,
    SUCCESS,
    FAILED,
    SKIPPED,
}

data class InstalledApp(
    val label: String,
    val packageName: String,
)

data class PermissionSnapshot(
    val exactAlarmsGranted: Boolean,
    val ignoreBatteryOptimizations: Boolean,
    val notificationsGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val deviceAdminEnabled: Boolean,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hour: Int,
    val minute: Int,
    val randomWindowMinutes: Int,
    val repeatRule: RepeatRule,
    val daysOfWeekCsv: String = "",
    val targetPackage: String,
    val targetAppLabel: String,
    val waitDurationSeconds: Int,
    val enabled: Boolean = true,
    val nextTriggerAtMillis: Long? = null,
    val scheduledDate: String? = null,
    val scheduledOffsetMinutes: Int? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun weeklyDays(): Set<DayOfWeek> {
        if (daysOfWeekCsv.isBlank()) return emptySet()
        return daysOfWeekCsv.split(',')
            .mapNotNull { value -> value.toIntOrNull() }
            .mapNotNull { number -> DayOfWeek.entries.getOrNull(number - 1) }
            .toSet()
    }
}

@Entity(
    tableName = "task_skip_dates",
    primaryKeys = ["taskId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["date"]),
    ],
)
data class TaskSkipDateEntity(
    val taskId: Long,
    val date: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    fun localDate(): LocalDate = LocalDate.parse(date)
}

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long?,
    val taskName: String,
    val status: ExecutionStatus,
    val detail: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "holiday_entries")
data class HolidayEntryEntity(
    @PrimaryKey val date: String,
    val isWorkday: Boolean,
    val isHoliday: Boolean,
    val name: String?,
    val source: String,
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun localDate(): LocalDate = LocalDate.parse(date)
}

class RoomConverters {
    @TypeConverter
    fun fromRepeatRule(value: RepeatRule): String = value.name

    @TypeConverter
    fun toRepeatRule(value: String): RepeatRule = RepeatRule.valueOf(value)

    @TypeConverter
    fun fromExecutionStatus(value: ExecutionStatus): String = value.name

    @TypeConverter
    fun toExecutionStatus(value: String): ExecutionStatus = ExecutionStatus.valueOf(value)
}
