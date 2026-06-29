package com.stl.autolauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.net.ssl.SSLException

class TaskRepository(
    private val taskDao: TaskDao,
    private val taskSkipDateDao: TaskSkipDateDao,
    private val logDao: LogDao,
) {
    fun observeTasks(): Flow<List<TaskEntity>> = taskDao.observeAll()

    fun observeTask(taskId: Long): Flow<TaskEntity?> = taskDao.observeById(taskId)

    fun observeTaskSkipDates(taskId: Long): Flow<List<TaskSkipDateEntity>> = taskSkipDateDao.observeByTaskId(taskId)

    fun observeFutureSkipDates(startDate: LocalDate): Flow<List<TaskSkipDateEntity>> {
        return taskSkipDateDao.observeFromDate(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    fun observeLogs(limit: Int = 200): Flow<List<ExecutionLogEntity>> = logDao.observeRecent(limit)

    suspend fun getTask(taskId: Long): TaskEntity? = taskDao.getById(taskId)

    suspend fun getAllTasks(): List<TaskEntity> = taskDao.getAll()

    suspend fun getDueTasks(nowMillis: Long): List<TaskEntity> = taskDao.getDueTasks(nowMillis)

    suspend fun getRecentLogs(limit: Int): List<ExecutionLogEntity> = logDao.getRecent(limit)

    suspend fun getSkipDates(taskId: Long): Set<LocalDate> {
        return taskSkipDateDao.getDatesByTaskId(taskId)
            .mapNotNull { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            .toSet()
    }

    suspend fun hasSkipDate(taskId: Long, date: LocalDate): Boolean {
        return taskSkipDateDao.countByTaskAndDate(taskId, date.format(DateTimeFormatter.ISO_LOCAL_DATE)) > 0
    }

    suspend fun saveTask(task: TaskEntity): Long {
        val now = System.currentTimeMillis()
        val prepared = if (task.id == 0L) {
            task.copy(createdAtMillis = now, updatedAtMillis = now)
        } else {
            task.copy(updatedAtMillis = now)
        }
        return taskDao.upsert(prepared)
    }

    suspend fun setEnabled(taskId: Long, enabled: Boolean) {
        taskDao.setEnabled(taskId, enabled, System.currentTimeMillis())
    }

    suspend fun updateSchedule(taskId: Long, nextTriggerAtMillis: Long?, scheduledDate: String?, offsetMinutes: Int?) {
        taskDao.updateSchedule(
            taskId = taskId,
            nextTriggerAtMillis = nextTriggerAtMillis,
            scheduledDate = scheduledDate,
            scheduledOffsetMinutes = offsetMinutes,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun deleteTask(taskId: Long) {
        taskSkipDateDao.deleteByTaskId(taskId)
        taskDao.deleteById(taskId)
    }

    suspend fun addSkipDate(taskId: Long, date: LocalDate) {
        taskSkipDateDao.upsert(
            TaskSkipDateEntity(
                taskId = taskId,
                date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            ),
        )
    }

    suspend fun deleteSkipDate(taskId: Long, date: LocalDate) {
        taskSkipDateDao.delete(taskId, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    suspend fun prunePastSkipDates(today: LocalDate) {
        taskSkipDateDao.prunePastDates(today.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    suspend fun appendLog(taskId: Long?, taskName: String, status: ExecutionStatus, detail: String) {
        logDao.insert(
            ExecutionLogEntity(
                taskId = taskId,
                taskName = taskName,
                status = status,
                detail = detail,
            ),
        )
        logDao.prune(300)
    }
}

class InstalledAppRepository(private val context: Context) {
    suspend fun getLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.MATCH_ALL
        pm.queryIntentActivities(queryIntent, flags)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                InstalledApp(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }
}

data class HolidayYearSyncResult(
    val year: Int,
    val success: Boolean,
    val reason: String? = null,
    val entryCount: Int = 0,
)

data class HolidaySyncSummary(
    val results: List<HolidayYearSyncResult>,
) {
    fun resultFor(year: Int): HolidayYearSyncResult? = results.firstOrNull { it.year == year }

    fun firstFailureReason(): String = results.firstOrNull { !it.success }?.reason ?: "节假日数据同步失败"
}

class HolidayRepository(
    private val context: Context,
    private val holidayDao: HolidayDao,
) {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val mirrorBaseUrl = "https://19930630.xyz/autolauncher/holiday"

    suspend fun hasLocalCoverage(year: Int): Boolean {
        val start = LocalDate.of(year, 1, 1).format(formatter)
        val end = LocalDate.of(year, 12, 31).format(formatter)
        return holidayDao.countInRange(start, end) > 300
    }

    suspend fun ensureCoverage(year: Int): Boolean {
        if (hasLocalCoverage(year)) return true
        return syncYear(year).success
    }

    suspend fun isChineseWorkday(date: LocalDate): Boolean? {
        val entry = holidayDao.getByDate(date.format(formatter))
        if (entry != null) return entry.isWorkday

        val hasCoverage = ensureCoverage(date.year)
        if (!hasCoverage) return null

        return holidayDao.getByDate(date.format(formatter))?.isWorkday
    }

    suspend fun syncYear(year: Int): HolidayYearSyncResult = withContext(Dispatchers.IO) {
        syncYearsInternal(listOf(year)).resultFor(year)
            ?: HolidayYearSyncResult(year = year, success = false, reason = "节假日数据同步失败")
    }

    suspend fun syncYears(years: Collection<Int>): HolidaySyncSummary = withContext(Dispatchers.IO) {
        syncYearsInternal(years)
    }

    private suspend fun syncYearsInternal(years: Collection<Int>): HolidaySyncSummary {
        val uniqueYears = years.distinct().sorted()
        if (uniqueYears.isEmpty()) return HolidaySyncSummary(emptyList())

        val regionResult = fetchCnRegionIndex()
        if (regionResult.region == null) {
            val reason = regionResult.reason ?: "节假日索引下载失败"
            return HolidaySyncSummary(
                uniqueYears.map { year ->
                    HolidayYearSyncResult(year = year, success = false, reason = reason)
                },
            )
        }

        val region = regionResult.region
        val results = uniqueYears.map { year ->
            if (year !in region.startYear..region.endYear) {
                HolidayYearSyncResult(
                    year = year,
                    success = false,
                    reason = "服务器暂未提供 ${year} 年中国节假日数据",
                )
            } else {
                val payloadResult = requestText("$mirrorBaseUrl/CN/$year.json")
                when {
                    payloadResult.payload.isNullOrBlank() -> HolidayYearSyncResult(
                        year = year,
                        success = false,
                        reason = "${year} 年节假日数据下载失败：${payloadResult.reason ?: "返回空数据"}",
                    )
                    else -> {
                        val entries = runCatching { parseYearPayload(year, payloadResult.payload) }.getOrElse {
                            emptyList()
                        }
                        if (entries.isEmpty()) {
                            HolidayYearSyncResult(
                                year = year,
                                success = false,
                                reason = "${year} 年节假日数据格式错误",
                            )
                        } else {
                            holidayDao.upsertAll(entries)
                            HolidayYearSyncResult(
                                year = year,
                                success = true,
                                entryCount = entries.size,
                            )
                        }
                    }
                }
            }
        }
        return HolidaySyncSummary(results)
    }

    private fun fetchCnRegionIndex(): RegionIndexResponse {
        val payloadResult = requestText("$mirrorBaseUrl/index.json")
        val payload = payloadResult.payload
            ?: return RegionIndexResponse(reason = "节假日索引下载失败：${payloadResult.reason ?: "返回空数据"}")

        val region = runCatching {
            val root = JSONObject(payload)
            val regions = root.optJSONArray("regions") ?: JSONArray()
            for (index in 0 until regions.length()) {
                val candidate = regions.optJSONObject(index) ?: continue
                if (candidate.optString("name") == "CN") {
                    val startYear = candidate.optInt("startYear", -1)
                    val endYear = candidate.optInt("endYear", -1)
                    if (startYear > 0 && endYear >= startYear) {
                        return@runCatching RegionIndex(startYear = startYear, endYear = endYear)
                    }
                }
            }
            null
        }.getOrNull()

        return if (region == null) {
            RegionIndexResponse(reason = "节假日索引格式错误")
        } else {
            RegionIndexResponse(region = region)
        }
    }

    private fun requestText(urlString: String): RemotePayload {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AutoLauncher/1.0.1")
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                RemotePayload(reason = "服务器返回 HTTP $responseCode")
            } else {
                RemotePayload(payload = connection.inputStream.bufferedReader().use { it.readText() })
            }
        } catch (_: SocketTimeoutException) {
            RemotePayload(reason = "连接节假日服务器超时")
        } catch (_: UnknownHostException) {
            RemotePayload(reason = "无法解析节假日服务器地址")
        } catch (_: SSLException) {
            RemotePayload(reason = "节假日服务器 TLS 连接失败")
        } catch (error: Exception) {
            RemotePayload(reason = error.message?.takeIf { it.isNotBlank() } ?: "无法连接节假日服务器")
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseYearPayload(year: Int, payload: String): List<HolidayEntryEntity> {
        val root = JSONObject(payload)
        val dates = root.optJSONArray("dates") ?: return emptyList()
        val results = linkedMapOf<String, HolidayEntryEntity>()

        for (index in 0 until dates.length()) {
            val raw = dates.optJSONObject(index) ?: continue
            val dateText = raw.optString("date")
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: continue
            if (date.year != year) continue

            val type = raw.optString("type")
            val explicitEntry = when (type) {
                "public_holiday" -> HolidayEntryEntity(
                    date = date.format(formatter),
                    isWorkday = false,
                    isHoliday = true,
                    name = raw.optString("name_cn").ifBlank { raw.optString("name") }.takeIf { it.isNotBlank() },
                    source = "holiday-calendar",
                )
                "transfer_workday" -> HolidayEntryEntity(
                    date = date.format(formatter),
                    isWorkday = true,
                    isHoliday = false,
                    name = raw.optString("name_cn").ifBlank { raw.optString("name") }.takeIf { it.isNotBlank() },
                    source = "holiday-calendar",
                )
                else -> null
            }
            if (explicitEntry != null) {
                results[explicitEntry.date] = explicitEntry
            }
        }

        var cursor = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year, 12, 31)
        while (!cursor.isAfter(end)) {
            val key = cursor.format(formatter)
            if (key !in results) {
                val isWeekday = cursor.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                results[key] = HolidayEntryEntity(
                    date = key,
                    isWorkday = isWeekday,
                    isHoliday = !isWeekday,
                    name = null,
                    source = "inferred",
                )
            }
            cursor = cursor.plusDays(1)
        }

        return results.values.toList()
    }

    private data class RegionIndex(
        val startYear: Int,
        val endYear: Int,
    )

    private data class RegionIndexResponse(
        val region: RegionIndex? = null,
        val reason: String? = null,
    )

    private data class RemotePayload(
        val payload: String? = null,
        val reason: String? = null,
    )
}
