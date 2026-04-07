package com.stl.autolauncher.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.stl.autolauncher.scheduling.HolidaySyncWorker
import com.stl.autolauncher.scheduling.TaskRescheduleWorker
import com.stl.autolauncher.service.TaskExecutionService

class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, TaskExecutionService::class.java)
                .putExtra(EXTRA_TASK_ID, taskId),
        )
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskRescheduleWorker.enqueueNow(context, intent.action ?: "boot")
        HolidaySyncWorker.enqueueNow(context, intent.action ?: "boot")
    }
}

class AutoLauncherDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun componentName(context: Context): ComponentName {
            return ComponentName(context, AutoLauncherDeviceAdminReceiver::class.java)
        }
    }
}
