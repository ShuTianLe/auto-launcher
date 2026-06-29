package com.stl.autolauncher.data

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.util.Base64
import com.stl.autolauncher.BuildConfig
import com.stl.autolauncher.util.PermissionInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.security.SecureRandom
import javax.net.ssl.SSLException

data class RemoteDeviceInfo(
    val deviceCode: String,
    val lastSyncAtMillis: Long,
    val lastSyncMessage: String,
    val lastSyncSuccess: Boolean,
)

data class RemoteCommandResult(
    val id: String,
    val status: String,
    val error: String? = null,
)

data class RemoteCommand(
    val id: String,
    val type: String,
    val payload: JSONObject,
)

class RemoteDeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("remote_device", Context.MODE_PRIVATE)

    val deviceCode: String
        get() = prefs.getString(KEY_DEVICE_CODE, null) ?: createIdentity().deviceCode

    val deviceSecret: String
        get() = prefs.getString(KEY_DEVICE_SECRET, null) ?: createIdentity().secret

    fun info(): RemoteDeviceInfo {
        return RemoteDeviceInfo(
            deviceCode = deviceCode,
            lastSyncAtMillis = prefs.getLong(KEY_LAST_SYNC_AT, 0L),
            lastSyncMessage = prefs.getString(KEY_LAST_SYNC_MESSAGE, "尚未同步") ?: "尚未同步",
            lastSyncSuccess = prefs.getBoolean(KEY_LAST_SYNC_SUCCESS, false),
        )
    }

    fun markSync(success: Boolean, message: String) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .putString(KEY_LAST_SYNC_MESSAGE, message)
            .putBoolean(KEY_LAST_SYNC_SUCCESS, success)
            .apply()
    }

    fun pendingResults(): List<RemoteCommandResult> {
        val raw = prefs.getString(KEY_PENDING_RESULTS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            RemoteCommandResult(
                id = item.optString("id"),
                status = item.optString("status"),
                error = item.optString("error").takeIf { it.isNotBlank() },
            )
        }.filter { it.id.isNotBlank() && it.status in setOf("applied", "failed") }
    }

    fun setPendingResults(results: List<RemoteCommandResult>) {
        val array = JSONArray()
        results.takeLast(50).forEach { result ->
            array.put(
                JSONObject()
                    .put("id", result.id)
                    .put("status", result.status)
                    .put("error", result.error),
            )
        }
        prefs.edit().putString(KEY_PENDING_RESULTS, array.toString()).apply()
    }

    fun isCommandApplied(commandId: String): Boolean {
        return appliedCommandIds().contains(commandId)
    }

    fun rememberAppliedCommand(commandId: String) {
        val ids = (appliedCommandIds() + commandId).takeLast(200)
        prefs.edit().putString(KEY_APPLIED_COMMANDS, JSONArray(ids).toString()).apply()
    }

    private fun appliedCommandIds(): List<String> {
        val raw = prefs.getString(KEY_APPLIED_COMMANDS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
    }

    private fun createIdentity(): Identity {
        synchronized(this) {
            val existingCode = prefs.getString(KEY_DEVICE_CODE, null)
            val existingSecret = prefs.getString(KEY_DEVICE_SECRET, null)
            if (!existingCode.isNullOrBlank() && !existingSecret.isNullOrBlank()) {
                return Identity(existingCode, existingSecret)
            }
            val identity = Identity(
                deviceCode = generateDeviceCode(),
                secret = randomSecret(),
            )
            prefs.edit()
                .putString(KEY_DEVICE_CODE, identity.deviceCode)
                .putString(KEY_DEVICE_SECRET, identity.secret)
                .apply()
            return identity
        }
    }

    private data class Identity(val deviceCode: String, val secret: String)

    private companion object {
        const val KEY_DEVICE_CODE = "device_code"
        const val KEY_DEVICE_SECRET = "device_secret"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_LAST_SYNC_MESSAGE = "last_sync_message"
        const val KEY_LAST_SYNC_SUCCESS = "last_sync_success"
        const val KEY_PENDING_RESULTS = "pending_results"
        const val KEY_APPLIED_COMMANDS = "applied_commands"

        fun generateDeviceCode(): String {
            val bytes = ByteArray(6)
            SecureRandom().nextBytes(bytes)
            val value = bytes.joinToString("") { byte -> "%02X".format(byte) }
            return "AL-${value.substring(0, 4)}-${value.substring(4, 8)}-${value.substring(8, 12)}"
        }

        fun randomSecret(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }
}

class RemoteSyncRepository(
    private val context: Context,
    private val store: RemoteDeviceStore,
    private val taskRepository: TaskRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val permissionInspector: PermissionInspector,
) {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun registerIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("deviceCode", store.deviceCode)
            .put("secret", store.deviceSecret)
            .put("displayName", Build.MODEL?.takeIf { it.isNotBlank() } ?: "Auto Launcher 设备")
            .put("appVersion", BuildConfig.VERSION_NAME)
        requestJson("/api/device/register", payload, authenticated = false).success
    }

    suspend fun poll(): RemotePollResult = withContext(Dispatchers.IO) {
        val tasks = taskRepository.getAllTasks()
        val payload = JSONObject()
            .put("device", deviceJson())
            .put("tasks", JSONArray(tasks.map { task -> taskJson(task) }))
            .put("executionLogs", JSONArray(taskRepository.getRecentLogs(120).map(::logJson)))
            .put("commandResults", JSONArray(store.pendingResults().map(::commandResultJson)))

        val response = requestJson("/api/device/poll", payload, authenticated = true)
        if (!response.success || response.json == null) {
            return@withContext RemotePollResult(success = false, message = response.message, commands = emptyList())
        }

        val commandsArray = response.json.optJSONArray("commands") ?: JSONArray()
        val commands = (0 until commandsArray.length()).mapNotNull { index ->
            val item = commandsArray.optJSONObject(index) ?: return@mapNotNull null
            RemoteCommand(
                id = item.optString("id"),
                type = item.optString("type"),
                payload = item.optJSONObject("payload") ?: JSONObject(),
            )
        }.filter { it.id.isNotBlank() && it.type.isNotBlank() }

        RemotePollResult(success = true, message = "远程同步成功", commands = commands)
    }

    private suspend fun deviceJson(): JSONObject {
        val battery = context.getSystemService(BatteryManager::class.java)
        val batteryPercent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it >= 0 } ?: 0
        return JSONObject()
            .put("deviceCode", store.deviceCode)
            .put("displayName", Build.MODEL?.takeIf { it.isNotBlank() } ?: "Auto Launcher 设备")
            .put("online", true)
            .put("charging", false)
            .put("batteryPercent", batteryPercent)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("lastSyncAtMillis", System.currentTimeMillis())
            .put("timezone", java.util.TimeZone.getDefault().id)
            .put("permissions", permissionsJson(permissionInspector.snapshot()))
            .put("installedApps", JSONArray(installedAppRepository.getLaunchableApps().map(::installedAppJson)))
    }

    private suspend fun taskJson(task: TaskEntity): JSONObject {
        val skipDates = taskRepository.getSkipDates(task.id)
            .map { it.format(formatter) }
            .sorted()
        return JSONObject()
            .put("id", task.id.toString())
            .put("name", task.name)
            .put("hour", task.hour)
            .put("minute", task.minute)
            .put("randomWindowMinutes", task.randomWindowMinutes)
            .put("repeatRule", task.repeatRule.name)
            .put("weeklyDays", JSONArray(task.weeklyDays().map { it.value }.sorted()))
            .put("targetPackage", task.targetPackage)
            .put("targetAppLabel", task.targetAppLabel)
            .put("waitDurationSeconds", task.waitDurationSeconds)
            .put("enabled", task.enabled)
            .put("skipDates", JSONArray(skipDates))
            .put("createdAtMillis", task.createdAtMillis)
            .put("updatedAtMillis", task.updatedAtMillis)
    }

    private fun logJson(log: ExecutionLogEntity): JSONObject {
        return JSONObject()
            .put("id", log.id.toString())
            .put("taskId", log.taskId?.toString())
            .put("taskName", log.taskName)
            .put("status", log.status.name)
            .put("detail", log.detail)
            .put("createdAtMillis", log.createdAtMillis)
    }

    private fun permissionsJson(snapshot: PermissionSnapshot): JSONObject {
        return JSONObject()
            .put("exactAlarmsGranted", snapshot.exactAlarmsGranted)
            .put("ignoreBatteryOptimizations", snapshot.ignoreBatteryOptimizations)
            .put("notificationsGranted", snapshot.notificationsGranted)
            .put("accessibilityEnabled", snapshot.accessibilityEnabled)
            .put("deviceAdminEnabled", snapshot.deviceAdminEnabled)
    }

    private fun installedAppJson(app: InstalledApp): JSONObject {
        return JSONObject()
            .put("label", app.label)
            .put("packageName", app.packageName)
    }

    private fun commandResultJson(result: RemoteCommandResult): JSONObject {
        return JSONObject()
            .put("id", result.id)
            .put("status", result.status)
            .put("error", result.error)
    }

    private fun requestJson(path: String, payload: JSONObject, authenticated: Boolean): RemoteHttpResponse {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(BuildConfig.REMOTE_BASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "AutoLauncher/${BuildConfig.VERSION_NAME}")
                if (authenticated) {
                    setRequestProperty("X-Device-Code", store.deviceCode)
                    setRequestProperty("Authorization", "Bearer ${store.deviceSecret}")
                }
            }
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val body = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (connection.responseCode !in 200..299) {
                RemoteHttpResponse(success = false, message = "服务器返回 HTTP ${connection.responseCode}")
            } else {
                RemoteHttpResponse(success = true, message = "ok", json = JSONObject(body))
            }
        } catch (_: SocketTimeoutException) {
            RemoteHttpResponse(success = false, message = "远程控制台连接超时")
        } catch (_: SSLException) {
            RemoteHttpResponse(success = false, message = "远程控制台 TLS 连接失败")
        } catch (error: Exception) {
            RemoteHttpResponse(success = false, message = error.message?.takeIf { it.isNotBlank() } ?: "远程控制台连接失败")
        } finally {
            connection?.disconnect()
        }
    }
}

data class RemotePollResult(
    val success: Boolean,
    val message: String,
    val commands: List<RemoteCommand>,
)

private data class RemoteHttpResponse(
    val success: Boolean,
    val message: String,
    val json: JSONObject? = null,
)
