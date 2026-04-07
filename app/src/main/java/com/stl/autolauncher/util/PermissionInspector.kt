package com.stl.autolauncher.util

import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.stl.autolauncher.data.PermissionSnapshot
import com.stl.autolauncher.receiver.AutoLauncherDeviceAdminReceiver

class PermissionInspector(private val context: Context) {
    fun snapshot(): PermissionSnapshot {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
        val accessibilityEnabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )?.contains(context.packageName) == true
        return PermissionSnapshot(
            exactAlarmsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            },
            ignoreBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            accessibilityEnabled = accessibilityEnabled,
            deviceAdminEnabled = devicePolicyManager.isAdminActive(
                AutoLauncherDeviceAdminReceiver.componentName(context),
            ),
        )
    }
}
