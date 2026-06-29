package com.stl.autolauncher.service

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.app.KeyguardManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.stl.autolauncher.AutoLauncherApp
import com.stl.autolauncher.data.ExecutionStatus
import com.stl.autolauncher.data.TaskEntity
import com.stl.autolauncher.receiver.AutoLauncherDeviceAdminReceiver
import com.stl.autolauncher.scheduling.ScheduleCalculator
import com.stl.autolauncher.ui.MainActivity
import com.stl.autolauncher.ui.WakeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskExecutionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { (application as AutoLauncherApp).container }
    private var wakeLock: PowerManager.WakeLock? = null
    private var processing = false
    private var rerunRequested = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (processing) {
            rerunRequested = true
            return START_NOT_STICKY
        }
        processing = true
        serviceScope.launch {
            do {
                rerunRequested = false
                drainDueTasks()
            } while (rerunRequested)
            processing = false
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeLock?.releaseSafely()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun drainDueTasks() {
        startForeground(NOTIFICATION_ID, buildNotification("执行计划任务中"))
        while (true) {
            val task = container.taskRepository.getDueTasks(System.currentTimeMillis()).firstOrNull() ?: break
            val scheduledDate = task.scheduledDate
            if (scheduledDate != null && !container.taskRepository.markScheduledDateHandled(task.id, scheduledDate)) {
                container.taskScheduler.onTaskCompleted(task.id, scheduledDate)
                continue
            }
            if (!skipDueTaskIfNeeded(task)) {
                executeTask(task)
            }
            container.taskScheduler.onTaskCompleted(task.id, scheduledDate)
        }
    }

    private suspend fun skipDueTaskIfNeeded(task: TaskEntity): Boolean {
        val triggerAtMillis = task.nextTriggerAtMillis ?: System.currentTimeMillis()
        val triggerDate = ScheduleCalculator.actualDateFor(triggerAtMillis, ZoneId.systemDefault())
        if (!container.taskRepository.hasSkipDate(task.id, triggerDate)) return false

        container.taskRepository.appendLog(
            task.id,
            task.name,
            ExecutionStatus.SKIPPED,
            "已按一次性跳过日期 ${triggerDate} 跳过本次任务",
        )
        return true
    }

    private suspend fun executeTask(task: TaskEntity) {
        val now = System.currentTimeMillis()
        val driftSeconds = task.nextTriggerAtMillis?.let { (now - it) / 1_000 } ?: 0L
        val targetRunningBeforeLaunch = isPackageRunning(task.targetPackage)
        val startMessage = if (driftSeconds > 60) {
            "任务顺延 ${driftSeconds} 秒后执行"
        } else {
            "任务开始执行"
        }
        container.taskRepository.appendLog(task.id, task.name, ExecutionStatus.STARTED, startMessage)
        logStep(
            task = task,
            detail = buildString {
                append("执行环境：")
                append("计划触发=")
                append(formatTimestamp(task.nextTriggerAtMillis))
                append("，实际开始=")
                append(formatTimestamp(now))
                append("，屏幕点亮=")
                append(getSystemService(PowerManager::class.java).isInteractive)
                append("，已锁屏=")
                append(getSystemService(KeyguardManager::class.java).isKeyguardLocked)
                append("，目标应用存活=")
                append(targetRunningBeforeLaunch)
                append("，本进程重要性=")
                append(currentProcessImportanceLabel())
                append("，辅助功能=")
                append(container.permissionInspector.snapshot().accessibilityEnabled)
                append("，设备管理器=")
                append(container.permissionInspector.snapshot().deviceAdminEnabled)
            },
        )
        updateForeground("执行 ${task.name}")

        val result = runCatching {
            wakeScreen()
            logStep(task, "步骤 1/5：已请求亮屏并展示唤醒页。")
            delay(1_200)
            bringLauncherToForeground()
            logStep(task, "步骤 2/5：已拉起 AutoLauncher 前台，等待界面稳定 1800ms。")
            delay(1_800)
            if (!launchTargetApp(task)) {
                error("无法启动 ${task.targetAppLabel}")
            }
            logStep(
                task,
                "步骤 3/5：已启动 ${task.targetAppLabel}，启动后目标应用存活=${isPackageRunning(task.targetPackage)}，停留 ${task.waitDurationSeconds.coerceAtLeast(1)} 秒。",
            )
            delay(task.waitDurationSeconds.coerceAtLeast(1) * 1_000L)
            returnToLauncher()
            logStep(task, "步骤 4/5：任务结束，已回到 AutoLauncher，等待 800ms 后锁屏。")
            delay(800)
            if (!lockScreenIfPossible()) {
                container.taskRepository.appendLog(
                    task.id,
                    task.name,
                    ExecutionStatus.SKIPPED,
                    "已回到 Auto Launcher，但未启用设备管理器，无法真正锁屏。",
                )
            } else {
                logStep(task, "步骤 5/5：已调用设备管理器锁屏。")
            }
        }

        result.onSuccess {
            container.taskRepository.appendLog(task.id, task.name, ExecutionStatus.SUCCESS, "任务执行完成")
        }.onFailure { throwable ->
            container.taskRepository.appendLog(
                task.id,
                task.name,
                ExecutionStatus.FAILED,
                throwable.message ?: "未知错误",
            )
        }
    }

    private fun wakeScreen() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock?.releaseSafely()
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "autolauncher:task",
        ).apply {
            acquire(10_000)
        }
        startActivity(
            Intent(this, WakeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    private fun launchTargetApp(task: TaskEntity): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(task.targetPackage) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching {
            startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    private fun bringLauncherToForeground() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
    }

    private fun returnToLauncher() {
        bringLauncherToForeground()
    }

    private fun lockScreenIfPossible(): Boolean {
        val policyManager = getSystemService(DevicePolicyManager::class.java)
        val component = AutoLauncherDeviceAdminReceiver.componentName(this)
        if (policyManager.isAdminActive(component)) {
            policyManager.lockNow()
            wakeLock?.releaseSafely()
            wakeLock = null
            return true
        }
        wakeLock?.releaseSafely()
        wakeLock = null
        return false
    }

    private fun updateForeground(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private suspend fun logStep(task: TaskEntity, detail: String) {
        container.taskRepository.appendLog(task.id, task.name, ExecutionStatus.STARTED, detail)
    }

    private fun isPackageRunning(packageName: String): Boolean {
        val activityManager = getSystemService(ActivityManager::class.java)
        return activityManager.runningAppProcesses.orEmpty().any { process ->
            packageName in process.pkgList
        }
    }

    private fun currentProcessImportanceLabel(): String {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return when (info.importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
            else -> info.importance.toString()
        }
    }

    private fun formatTimestamp(timestampMillis: Long?): String {
        if (timestampMillis == null) return "未安排"
        return Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Auto Launcher")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Task execution",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun PowerManager.WakeLock.releaseSafely() {
        runCatching {
            if (isHeld) release()
        }
    }

    companion object {
        private const val CHANNEL_ID = "task_execution"
        private const val NOTIFICATION_ID = 1001
    }
}
