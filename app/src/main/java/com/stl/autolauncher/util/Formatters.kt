package com.stl.autolauncher.util

import com.stl.autolauncher.data.RepeatRule
import com.stl.autolauncher.data.TaskEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE)

fun formatNextTrigger(triggerAtMillis: Long?): String {
    if (triggerAtMillis == null) return "未安排"
    return Instant.ofEpochMilli(triggerAtMillis)
        .atZone(ZoneId.systemDefault())
        .format(dateTimeFormatter)
}

fun TaskEntity.repeatSummary(): String {
    return when (repeatRule) {
        RepeatRule.DAILY -> "每天"
        RepeatRule.WORKDAY_CN -> "中国工作日"
        RepeatRule.WEEKLY -> {
            val labels = weeklyDays().sortedBy { it.value }.joinToString("/") { day ->
                when (day.value) {
                    1 -> "周一"
                    2 -> "周二"
                    3 -> "周三"
                    4 -> "周四"
                    5 -> "周五"
                    6 -> "周六"
                    else -> "周日"
                }
            }
            if (labels.isBlank()) "指定周几" else labels
        }
    }
}
