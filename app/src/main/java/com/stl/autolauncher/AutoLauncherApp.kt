package com.stl.autolauncher

import android.app.Application
import com.stl.autolauncher.data.AutoLauncherDatabase
import com.stl.autolauncher.data.HolidayRepository
import com.stl.autolauncher.data.InstalledAppRepository
import com.stl.autolauncher.data.RemoteDeviceStore
import com.stl.autolauncher.data.RemoteSyncRepository
import com.stl.autolauncher.data.TaskRepository
import com.stl.autolauncher.scheduling.HolidaySyncWorker
import com.stl.autolauncher.scheduling.RemoteSyncWorker
import com.stl.autolauncher.scheduling.TaskRescheduleWorker
import com.stl.autolauncher.scheduling.TaskScheduler
import com.stl.autolauncher.util.PermissionInspector

class AutoLauncherApp : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.ensureBackgroundWorkConfigured()
    }
}

class AppContainer(app: Application) {
    private val appContext = app.applicationContext
    private val database = AutoLauncherDatabase.build(appContext)

    val taskRepository = TaskRepository(database.taskDao(), database.taskSkipDateDao(), database.logDao())
    val holidayRepository = HolidayRepository(appContext, database.holidayDao())
    val installedAppRepository = InstalledAppRepository(appContext)
    val permissionInspector = PermissionInspector(appContext)
    val remoteDeviceStore = RemoteDeviceStore(appContext)
    val remoteSyncRepository = RemoteSyncRepository(
        appContext,
        remoteDeviceStore,
        taskRepository,
        installedAppRepository,
        permissionInspector,
    )
    val taskScheduler = TaskScheduler(appContext, database.taskDao(), database.taskSkipDateDao(), holidayRepository)

    fun ensureBackgroundWorkConfigured() {
        HolidaySyncWorker.enqueuePeriodic(appContext)
        HolidaySyncWorker.enqueueNow(appContext, "app_start")
        TaskRescheduleWorker.enqueueNow(appContext, "app_start")
        RemoteSyncWorker.enqueueNow(appContext, "app_start")
    }
}
