package com.stl.autolauncher.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stl.autolauncher.AppContainer
import com.stl.autolauncher.AutoLauncherApp
import com.stl.autolauncher.data.ExecutionLogEntity
import com.stl.autolauncher.data.ExecutionStatus
import com.stl.autolauncher.data.HolidaySyncSummary
import com.stl.autolauncher.data.InstalledApp
import com.stl.autolauncher.data.PermissionSnapshot
import com.stl.autolauncher.data.RepeatRule
import com.stl.autolauncher.data.TaskEntity
import com.stl.autolauncher.data.TaskSkipDateEntity
import com.stl.autolauncher.receiver.AutoLauncherDeviceAdminReceiver
import com.stl.autolauncher.scheduling.ScheduleCalculator
import com.stl.autolauncher.scheduling.SchedulePreviewDay
import com.stl.autolauncher.scheduling.SchedulePreviewStatus
import com.stl.autolauncher.scheduling.ScheduleWindow
import com.stl.autolauncher.scheduling.HolidaySyncWorker
import com.stl.autolauncher.ui.theme.AutoLauncherTheme
import com.stl.autolauncher.util.formatNextTrigger
import com.stl.autolauncher.util.repeatSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel> {
        MainViewModel.factory((application as AutoLauncherApp).container, application as AutoLauncherApp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoLauncherTheme {
                AutoLauncherRoot(viewModel = viewModel, activity = this)
            }
        }
    }
}

enum class HolidaySyncStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
}

data class HolidaySyncUiState(
    val status: HolidaySyncStatus = HolidaySyncStatus.IDLE,
    val message: String? = null,
) {
    val isRunning: Boolean get() = status == HolidaySyncStatus.RUNNING
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    application: AutoLauncherApp,
    private val container: AppContainer,
) : AndroidViewModel(application) {
    val tasks = container.taskRepository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val logs = container.taskRepository.observeLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _today = MutableStateFlow(LocalDate.now())
    val futureSkipDates = _today
        .flatMapLatest { today -> container.taskRepository.observeFutureSkipDates(today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _permissions = MutableStateFlow(container.permissionInspector.snapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _holidaySyncState = MutableStateFlow(HolidaySyncUiState())
    val holidaySyncState: StateFlow<HolidaySyncUiState> = _holidaySyncState.asStateFlow()

    private val _syncMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val syncMessages = _syncMessages.asSharedFlow()

    init {
        refreshInstalledApps()
        refreshPermissions()
        prunePastSkipDates()
    }

    fun observeTask(taskId: Long) = container.taskRepository.observeTask(taskId)

    fun observeTaskSkipDates(taskId: Long) = container.taskRepository.observeTaskSkipDates(taskId)

    fun refreshCurrentDate() {
        _today.value = LocalDate.now()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = container.installedAppRepository.getLaunchableApps()
        }
    }

    fun refreshPermissions() {
        _permissions.value = container.permissionInspector.snapshot()
    }

    fun saveTask(task: TaskEntity, onComplete: () -> Unit) {
        viewModelScope.launch {
            val taskId = container.taskRepository.saveTask(task)
            container.taskScheduler.reconcileTask(taskId)
            if (task.repeatRule == RepeatRule.WORKDAY_CN) {
                HolidaySyncWorker.enqueueNow(getApplication(), "save_workday_task")
            }
            refreshPermissions()
            onComplete()
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            container.taskRepository.deleteTask(taskId)
            container.taskScheduler.cancelTask(taskId)
        }
    }

    fun setTaskEnabled(taskId: Long, enabled: Boolean) {
        viewModelScope.launch {
            container.taskRepository.setEnabled(taskId, enabled)
            container.taskScheduler.reconcileTask(taskId)
        }
    }

    fun addSkipDate(taskId: Long, date: LocalDate) {
        viewModelScope.launch {
            val today = LocalDate.now()
            if (date.isBefore(today)) {
                _syncMessages.tryEmit("不能添加过去日期")
                return@launch
            }
            container.taskRepository.addSkipDate(taskId, date)
            container.taskRepository.prunePastSkipDates(today)
            container.taskScheduler.reconcileTask(taskId)
            refreshCurrentDate()
            _syncMessages.tryEmit("已添加跳过日期 ${date.format(skipDateMessageFormatter)}")
        }
    }

    fun deleteSkipDate(taskId: Long, date: LocalDate) {
        viewModelScope.launch {
            container.taskRepository.deleteSkipDate(taskId, date)
            container.taskScheduler.reconcileTask(taskId)
            refreshCurrentDate()
            _syncMessages.tryEmit("已删除跳过日期 ${date.format(skipDateMessageFormatter)}")
        }
    }

    fun prunePastSkipDates() {
        viewModelScope.launch {
            container.taskRepository.prunePastSkipDates(LocalDate.now())
            refreshCurrentDate()
        }
    }

    suspend fun buildSevenDayPreview(task: TaskEntity, skipDates: Set<LocalDate>): List<SchedulePreviewDay> {
        return ScheduleCalculator.buildSevenDayPreview(
            task = task,
            startDate = LocalDate.now(),
            skipDates = skipDates,
            workdayResolver = { date -> container.holidayRepository.isChineseWorkday(date) },
        )
    }

    fun refreshHolidayCalendar() {
        if (_holidaySyncState.value.isRunning) return

        viewModelScope.launch {
            _holidaySyncState.value = HolidaySyncUiState(
                status = HolidaySyncStatus.RUNNING,
                message = "正在同步中国节假日数据…",
            )
            _syncMessages.tryEmit("正在同步中国节假日数据…")

            val currentYear = LocalDate.now().year
            val message = try {
                val summary = withTimeout(30_000) {
                    container.holidayRepository.syncYears(listOf(currentYear, currentYear + 1))
                }
                handleManualSyncResult(currentYear, summary)
            } catch (_: TimeoutCancellationException) {
                val timeoutMessage = "同步超时，请检查网络后重试。"
                _holidaySyncState.value = HolidaySyncUiState(
                    status = HolidaySyncStatus.TIMEOUT,
                    message = timeoutMessage,
                )
                timeoutMessage
            } catch (error: Exception) {
                val failureMessage = "同步失败：${error.message?.takeIf { it.isNotBlank() } ?: "未知错误"}"
                _holidaySyncState.value = HolidaySyncUiState(
                    status = HolidaySyncStatus.FAILED,
                    message = failureMessage,
                )
                failureMessage
            }

            _syncMessages.tryEmit(message)
        }
    }

    private suspend fun handleManualSyncResult(currentYear: Int, summary: HolidaySyncSummary): String {
        val currentResult = summary.resultFor(currentYear)
        val nextResult = summary.resultFor(currentYear + 1)

        return if (currentResult?.success == true) {
            container.taskScheduler.reconcileAllTasks()
            val successMessage = if (nextResult?.success == false) {
                "当前年份同步成功，下一年同步失败：${nextResult.reason ?: "未知错误"}"
            } else {
                "同步成功，工作日任务已重新安排。"
            }
            _holidaySyncState.value = HolidaySyncUiState(
                status = HolidaySyncStatus.SUCCESS,
                message = successMessage,
            )
            successMessage
        } else {
            val reason = currentResult?.reason ?: summary.firstFailureReason()
            val hasCurrentCache = container.holidayRepository.hasLocalCoverage(currentYear)
            val failureMessage = if (hasCurrentCache) {
                "同步失败：$reason；已继续使用本地缓存。"
            } else {
                "同步失败：$reason"
            }
            _holidaySyncState.value = HolidaySyncUiState(
                status = HolidaySyncStatus.FAILED,
                message = failureMessage,
            )
            failureMessage
        }
    }

    companion object {
        fun factory(container: AppContainer, app: AutoLauncherApp): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(app, container) as T
                }
            }
        }
    }
}

private sealed class Screen(val route: String) {
    data object Tasks : Screen("tasks")
    data object Logs : Screen("logs")
    data object Permissions : Screen("permissions")
    data object Edit : Screen("edit?taskId={taskId}") {
        fun create(taskId: Long? = null): String = taskId?.let { "edit?taskId=$it" } ?: "edit"
    }
    data object Schedule : Screen("schedule/{taskId}") {
        fun create(taskId: Long): String = "schedule/$taskId"
    }
}

@Composable
private fun AutoLauncherRoot(viewModel: MainViewModel, activity: Activity) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: Screen.Tasks.route
    val permissions by viewModel.permissions.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val holidaySyncState by viewModel.holidaySyncState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.syncMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!currentRoute.startsWith("edit") && !currentRoute.startsWith("schedule")) {
                NavigationBar {
                    val tabs = listOf(Screen.Tasks, Screen.Logs, Screen.Permissions)
                    tabs.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (screen) {
                                        Screen.Tasks -> Icons.Default.Schedule
                                        Screen.Logs -> Icons.Default.Info
                                        Screen.Permissions -> Icons.Default.Settings
                                        Screen.Edit -> Icons.Default.Add
                                        Screen.Schedule -> Icons.Outlined.CalendarMonth
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    when (screen) {
                                        Screen.Tasks -> "任务"
                                        Screen.Logs -> "日志"
                                        Screen.Permissions -> "权限"
                                        Screen.Edit -> "编辑"
                                        Screen.Schedule -> "时间表"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Screen.Tasks.route) {
                FloatingActionButton(onClick = { navController.navigate(Screen.Edit.create()) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Tasks.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Tasks.route) {
                TaskListScreen(
                    viewModel = viewModel,
                    permissions = permissions,
                    onEdit = { navController.navigate(Screen.Edit.create(it)) },
                    onOpenSchedule = { navController.navigate(Screen.Schedule.create(it)) },
                    onOpenPermissions = { navController.navigate(Screen.Permissions.route) },
                )
            }
            composable(Screen.Logs.route) {
                LogsScreen(logs = logs)
            }
            composable(Screen.Permissions.route) {
                PermissionsScreen(
                    snapshot = permissions,
                    holidaySyncState = holidaySyncState,
                    onRefresh = viewModel::refreshPermissions,
                    onSyncHoliday = viewModel::refreshHolidayCalendar,
                    activity = activity,
                )
            }
            composable(
                route = Screen.Edit.route,
                arguments = listOf(
                    navArgument("taskId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val taskId = entry.arguments?.getLong("taskId")?.takeIf { it > 0 }
                TaskEditorScreen(viewModel = viewModel, taskId = taskId, onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.Schedule.route,
                arguments = listOf(
                    navArgument("taskId") {
                        type = NavType.LongType
                    },
                ),
            ) { entry ->
                val taskId = entry.arguments?.getLong("taskId") ?: return@composable
                TaskScheduleScreen(viewModel = viewModel, taskId = taskId, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun ScreenBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListScreen(
    viewModel: MainViewModel,
    permissions: PermissionSnapshot,
    onEdit: (Long) -> Unit,
    onOpenSchedule: (Long) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val tasks by viewModel.tasks.collectAsState()
    val futureSkipDates by viewModel.futureSkipDates.collectAsState()
    val skipDatesByTask = remember(futureSkipDates) {
        futureSkipDates
            .groupBy { it.taskId }
            .mapValues { (_, dates) -> dates.map(TaskSkipDateEntity::localDate).sorted() }
    }
    val issues = criticalPermissionIssues(permissions)
    val needsHolidaySync = tasks.any { it.enabled && it.repeatRule == RepeatRule.WORKDAY_CN && it.nextTriggerAtMillis == null }

    LaunchedEffect(viewModel) {
        while (true) {
            viewModel.refreshCurrentDate()
            delay(60_000)
        }
    }

    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            CenterAlignedTopAppBar(title = { Text("定时任务") })
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (issues.isNotEmpty()) {
                    item {
                        WarningBanner(
                            title = "关键权限未开启",
                            message = issues.joinToString("；"),
                            onOpenPermissions = onOpenPermissions,
                        )
                    }
                }
                if (needsHolidaySync) {
                    item {
                        InfoBanner(
                            title = "工作日任务待安排",
                            message = "中国节假日数据尚未同步完成，工作日任务暂未生成触发时间。默认会自动同步；如果自动失败，请到“权限”页点击“立即同步中国节假日”。",
                            actionLabel = "去同步",
                            onClick = onOpenPermissions,
                        )
                    }
                }
                if (tasks.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "还没有定时任务",
                            description = "创建任务后，系统会按 24 小时制时间点进行调度，并在设定随机窗口内触发。",
                        )
                    }
                }
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        futureSkipDates = skipDatesByTask[task.id].orEmpty(),
                        onOpenSchedule = { onOpenSchedule(task.id) },
                        onEdit = { onEdit(task.id) },
                        onDelete = { viewModel.deleteTask(task.id) },
                        onEnabledChanged = { viewModel.setTaskEnabled(task.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    futureSkipDates: List<LocalDate>,
    onOpenSchedule: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
) {
    ElevatedSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(task.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${task.targetAppLabel} · ${task.repeatSummary()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = task.enabled,
                    onCheckedChange = onEnabledChanged,
                )
            }

            TaskMetaRow(icon = Icons.Default.Schedule, label = "下次触发", value = taskTriggerSummary(task))
            TaskMetaRow(
                icon = Icons.Outlined.CalendarMonth,
                label = "任务配置",
                value = formatClock(task.hour, task.minute) + " · 随机 " + task.randomWindowMinutes + " 分钟 · 停留 " + task.waitDurationSeconds + " 秒",
            )

            if (futureSkipDates.isNotEmpty()) {
                SkipDateReminderRow(dates = futureSkipDates)
            }

            Button(onClick = onOpenSchedule, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("时间表与跳过日期")
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("编辑")
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text("删除")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkipDateReminderRow(dates: List<LocalDate>) {
    val visibleDates = dates.take(4)
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EventBusy, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("将跳过以下日期", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleDates.forEach { date ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = date.format(skipDateChipFormatter),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (dates.size > visibleDates.size) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = "+${dates.size - visibleDates.size}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskMetaRow(icon: ImageVector, label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskEditorScreen(viewModel: MainViewModel, taskId: Long?, onBack: () -> Unit) {
    val installedApps by viewModel.installedApps.collectAsState()
    val taskFlow = remember(taskId) { taskId?.let(viewModel::observeTask) }
    val existingTask by (taskFlow?.collectAsState(initial = null) ?: remember { mutableStateOf<TaskEntity?>(null) })
    val defaultWeeklyDays = remember {
        setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }

    var name by remember { mutableStateOf("") }
    var selectedHour by remember { mutableStateOf(9) }
    var selectedMinute by remember { mutableStateOf(0) }
    var randomWindow by remember { mutableStateOf("0") }
    var waitSeconds by remember { mutableStateOf("30") }
    var repeatRule by remember { mutableStateOf(RepeatRule.WORKDAY_CN) }
    var selectedDays by remember { mutableStateOf(defaultWeeklyDays) }
    var targetPackage by remember { mutableStateOf("") }
    var targetLabel by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var appPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(taskId, existingTask?.id) {
        val task = existingTask
        if (task == null) {
            name = ""
            selectedHour = 9
            selectedMinute = 0
            randomWindow = "0"
            waitSeconds = "30"
            repeatRule = RepeatRule.WORKDAY_CN
            selectedDays = defaultWeeklyDays
            targetPackage = ""
            targetLabel = ""
            enabled = true
        } else {
            name = task.name
            selectedHour = task.hour
            selectedMinute = task.minute
            randomWindow = task.randomWindowMinutes.toString()
            waitSeconds = task.waitDurationSeconds.toString()
            repeatRule = task.repeatRule
            selectedDays = task.weeklyDays().ifEmpty { defaultWeeklyDays }
            targetPackage = task.targetPackage
            targetLabel = task.targetAppLabel
            enabled = task.enabled
        }
    }

    val context = LocalContext.current
    val showTimePicker = {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
            },
            selectedHour,
            selectedMinute,
            true,
        ).show()
    }

    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(if (taskId == null) "新建任务" else "编辑任务") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val parsedWindow = randomWindow.toIntOrNull()
                                val parsedWait = waitSeconds.toIntOrNull()
                                errorText = when {
                                    name.isBlank() -> "请输入任务名称"
                                    parsedWindow == null || parsedWindow !in 0..180 -> "随机窗口需在 0-180 分钟"
                                    parsedWait == null || parsedWait !in 5..7200 -> "停留时长需在 5-7200 秒"
                                    targetPackage.isBlank() -> "请选择目标应用"
                                    repeatRule == RepeatRule.WEEKLY && selectedDays.isEmpty() -> "至少选择一个星期几"
                                    else -> null
                                }
                                if (errorText == null) {
                                    viewModel.saveTask(
                                        TaskEntity(
                                            id = existingTask?.id ?: 0,
                                            name = name.trim(),
                                            hour = selectedHour,
                                            minute = selectedMinute,
                                            randomWindowMinutes = parsedWindow!!,
                                            repeatRule = repeatRule,
                                            daysOfWeekCsv = selectedDays.sortedBy { it.value }.joinToString(",") { it.value.toString() },
                                            targetPackage = targetPackage,
                                            targetAppLabel = targetLabel,
                                            waitDurationSeconds = parsedWait!!,
                                            enabled = enabled,
                                            nextTriggerAtMillis = null,
                                            scheduledDate = null,
                                            scheduledOffsetMinutes = null,
                                            createdAtMillis = existingTask?.createdAtMillis ?: System.currentTimeMillis(),
                                        ),
                                    ) { onBack() }
                                }
                            },
                        ) {
                            Icon(Icons.Default.Done, contentDescription = null)
                        }
                    },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    InstructionCard(showWorkdayHint = repeatRule == RepeatRule.WORKDAY_CN)
                }
                errorText?.let { message ->
                    item {
                        ErrorBanner(message = message)
                    }
                }
                item {
                    SectionCard(title = "基础信息", subtitle = "先为任务命名，并确认是否启用。") {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("任务名称") },
                            singleLine = true,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("启用任务", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                        }
                    }
                }
                item {
                    SectionCard(title = "时间与随机", subtitle = "使用 24 小时制选择触发时间点，再设置可接受的随机分钟数。") {
                        TimeSelectorRow(
                            value = formatClock(selectedHour, selectedMinute),
                            onClick = showTimePicker,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = randomWindow,
                                onValueChange = { randomWindow = it.filter(Char::isDigit).take(3) },
                                modifier = Modifier.weight(1f),
                                label = { Text("随机窗口(分)") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = waitSeconds,
                                onValueChange = { waitSeconds = it.filter(Char::isDigit).take(4) },
                                modifier = Modifier.weight(1f),
                                label = { Text("停留时长(秒)") },
                                singleLine = true,
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = "重复规则", subtitle = "支持每天、中国工作日，或指定周几执行。") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RepeatRule.entries.forEach { rule ->
                                val selected = repeatRule == rule
                                AssistChip(
                                    onClick = { repeatRule = rule },
                                    label = {
                                        Text(
                                            when (rule) {
                                                RepeatRule.DAILY -> "每天"
                                                RepeatRule.WORKDAY_CN -> "中国工作日"
                                                RepeatRule.WEEKLY -> "指定周几"
                                            },
                                        )
                                    },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Default.Done, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        if (repeatRule == RepeatRule.WEEKLY) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DayOfWeek.entries.forEach { day ->
                                    val checked = day in selectedDays
                                    AssistChip(
                                        onClick = {
                                            selectedDays = if (checked) selectedDays - day else selectedDays + day
                                        },
                                        label = { Text(dayLabel(day)) },
                                        leadingIcon = if (checked) {
                                            { Icon(Icons.Default.Done, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = "目标应用", subtitle = "任务触发后会先点亮设备，再打开这里选择的应用。") {
                        OutlinedTextField(
                            value = if (targetLabel.isBlank()) "" else "$targetLabel\n$targetPackage",
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("当前选择") },
                            readOnly = true,
                        )
                        Button(onClick = {
                            viewModel.refreshInstalledApps()
                            appPickerOpen = true
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("选择已安装应用")
                        }
                    }
                }
            }
        }
    }

    if (appPickerOpen) {
        AlertDialog(
            onDismissRequest = { appPickerOpen = false },
            title = { Text("选择应用") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(installedApps, key = { it.packageName }) { app ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    targetLabel = app.label
                                    targetPackage = app.packageName
                                    appPickerOpen = false
                                },
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { appPickerOpen = false }) {
                    Text("关闭")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskScheduleScreen(viewModel: MainViewModel, taskId: Long, onBack: () -> Unit) {
    val taskFlow = remember(taskId) { viewModel.observeTask(taskId) }
    val task by taskFlow.collectAsState(initial = null)
    val skipDateFlow = remember(taskId) { viewModel.observeTaskSkipDates(taskId) }
    val skipDateEntities by skipDateFlow.collectAsState(initial = emptyList())
    var today by remember { mutableStateOf(LocalDate.now()) }
    var previewDays by remember { mutableStateOf<List<SchedulePreviewDay>?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val zoneId = remember { ZoneId.systemDefault() }

    val skipDates = remember(skipDateEntities) {
        skipDateEntities.map(TaskSkipDateEntity::localDate).sorted()
    }
    val futureSkipDates = remember(skipDates, today) {
        skipDates.filterNot { it.isBefore(today) }
    }

    LaunchedEffect(viewModel) {
        viewModel.prunePastSkipDates()
        while (true) {
            today = LocalDate.now()
            viewModel.refreshCurrentDate()
            delay(60_000)
        }
    }

    LaunchedEffect(task, skipDates) {
        val currentTask = task
        if (currentTask == null) {
            previewDays = null
            return@LaunchedEffect
        }
        previewLoading = true
        previewDays = runCatching {
            viewModel.buildSevenDayPreview(currentTask, skipDates.toSet())
        }.getOrDefault(emptyList())
        previewLoading = false
    }

    val showSkipDatePicker = {
        val baseDate = LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                viewModel.addSkipDate(taskId, LocalDate.of(year, month + 1, dayOfMonth))
            },
            baseDate.year,
            baseDate.monthValue - 1,
            baseDate.dayOfMonth,
        ).apply {
            datePicker.minDate = baseDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        }.show()
    }

    ScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("任务时间表") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val currentTask = task
                if (currentTask == null) {
                    item {
                        EmptyStateCard(
                            title = "任务不存在",
                            description = "这个任务可能已经被删除，请返回任务列表重新选择。",
                        )
                    }
                } else {
                    item {
                        SectionCard(title = "任务摘要") {
                            Text(currentTask.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            TaskMetaRow(icon = Icons.Default.Schedule, label = "下次触发", value = taskTriggerSummary(currentTask))
                            TaskMetaRow(
                                icon = Icons.Outlined.CalendarMonth,
                                label = "执行窗口",
                                value = formatClock(currentTask.hour, currentTask.minute) + scheduleWindowSuffix(currentTask.randomWindowMinutes),
                            )
                            TaskMetaRow(icon = Icons.Default.PhoneAndroid, label = "目标应用", value = currentTask.targetAppLabel)
                        }
                    }
                    item {
                        SectionCard(title = "跳过日期", subtitle = "一次性日期，可添加多个。") {
                            Button(onClick = showSkipDatePicker, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("添加跳过日期")
                            }
                            if (futureSkipDates.isEmpty()) {
                                Text(
                                    "暂无未来跳过日期",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    futureSkipDates.forEach { date ->
                                        SkipDateChip(
                                            date = date,
                                            onDelete = { viewModel.deleteSkipDate(taskId, date) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        SectionCard(title = "未来 7 天", subtitle = "按实际触发日期展示。") {
                            when {
                                previewLoading -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                                previewDays.isNullOrEmpty() -> Text(
                                    "暂无预览",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                else -> previewDays.orEmpty().forEach { day ->
                                    SchedulePreviewDayRow(day = day)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipDateChip(date: LocalDate, onDelete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                date.format(skipDateChipFormatter),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SchedulePreviewDayRow(day: SchedulePreviewDay) {
    val (statusText, containerColor, contentColor) = previewStatusStyle(day.status)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = if (day.status == SchedulePreviewStatus.SKIPPED) Icons.Outlined.EventBusy else Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(day.date.format(fullDateFormatter), fontWeight = FontWeight.SemiBold)
                Text(
                    previewDetailText(day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.widthIn(min = 72.dp),
            ) {
                Text(
                    statusText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun InstructionCard(showWorkdayHint: Boolean) {
    val steps = listOf(
        "1. 选择 24 小时制触发时间。",
        "2. 设置随机窗口；系统会在每个新的触发周期重新随机一次。",
        "3. 设置停留时长。",
        "4. 选择需要自动唤醒的 App。",
        "5. 保存后，任务会自动启动 App，结束后回到定时任务并锁屏。",
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("使用说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            steps.forEach { step ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.padding(top = 2.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (showWorkdayHint) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        "中国工作日任务默认会自动同步节假日数据；如果自动失败，请手动点击“立即同步中国节假日”。",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeSelectorRow(value: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Timelapse,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("触发时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("24h", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsScreen(logs: List<ExecutionLogEntity>) {
    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            CenterAlignedTopAppBar(title = { Text("执行日志") })
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (logs.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "暂无执行日志",
                            description = "任务触发后，这里会按统一格式记录执行状态、详细结果和时间。",
                        )
                    }
                }
                items(logs, key = { it.id }) { log ->
                    LogCard(log = log)
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: ExecutionLogEntity) {
    val (label, background, contentColor) = logStatusStyle(log.status)
    ElevatedSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = log.taskName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    color = background,
                    contentColor = contentColor,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.widthIn(min = 78.dp),
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(
                log.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatNextTrigger(log.createdAtMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsScreen(
    snapshot: PermissionSnapshot,
    holidaySyncState: HolidaySyncUiState,
    onRefresh: () -> Unit,
    onSyncHoliday: () -> Unit,
    activity: Activity,
) {
    val syncMessageColor = when (holidaySyncState.status) {
        HolidaySyncStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        HolidaySyncStatus.FAILED, HolidaySyncStatus.TIMEOUT -> MaterialTheme.colorScheme.error
        HolidaySyncStatus.RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
        HolidaySyncStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            CenterAlignedTopAppBar(
                title = { Text("权限与系统设置") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                },
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionCard(
                        title = "说明",
                        subtitle = "这里列出影响定时唤醒、启动 App 和锁屏结果的系统能力。建议全部开启。",
                    ) {
                        Text(
                            "如果设备没有开启关键权限，任务可能不准时、无法持续运行，或结束后不能真正锁屏。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    PermissionCard(
                        title = "精确闹钟",
                        granted = snapshot.exactAlarmsGranted,
                        description = "决定定时触发能否接近设定时间。",
                        icon = Icons.Default.Schedule,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            }
                        },
                    )
                }
                item {
                    PermissionCard(
                        title = "忽略电池优化",
                        granted = snapshot.ignoreBatteryOptimizations,
                        description = "减少 ROM 杀后台后漏触发。",
                        icon = Icons.Default.Warning,
                        onClick = {
                            activity.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .setData(Uri.parse("package:${activity.packageName}")),
                            )
                        },
                    )
                }
                item {
                    PermissionCard(
                        title = "通知",
                        granted = snapshot.notificationsGranted,
                        description = "用于展示执行失败和运行中通知。",
                        icon = Icons.Default.Notifications,
                        onClick = {
                            activity.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
                            )
                        },
                    )
                }
                item {
                    PermissionCard(
                        title = "辅助功能",
                        granted = snapshot.accessibilityEnabled,
                        description = "优先用于回到桌面，未开启时会使用系统 Home Intent 兜底。",
                        icon = Icons.Default.PlayArrow,
                        onClick = {
                            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )
                }
                item {
                    PermissionCard(
                        title = "设备管理器",
                        granted = snapshot.deviceAdminEnabled,
                        description = "开启后任务结束可真正锁屏。",
                        icon = Icons.Default.Lock,
                        onClick = {
                            activity.startActivity(
                                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                    .putExtra(
                                        DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                        AutoLauncherDeviceAdminReceiver.componentName(activity),
                                    ),
                            )
                        },
                    )
                }
                item {
                    SectionCard(
                        title = "中国节假日同步",
                        subtitle = "默认自动同步；如果自动失败，可以在这里手动重试。",
                    ) {
                        Button(
                            onClick = onSyncHoliday,
                            enabled = !holidaySyncState.isRunning,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (holidaySyncState.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(if (holidaySyncState.isRunning) "同步中..." else "立即同步中国节假日")
                        }
                        holidaySyncState.message?.takeIf { holidaySyncState.status != HolidaySyncStatus.IDLE }?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = syncMessageColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ElevatedSectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (granted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                    tint = if (granted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = if (granted) "已开启" else "去设置",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        })
    }
}

@Composable
private fun ElevatedSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun EmptyStateCard(title: String, description: String) {
    ElevatedSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoBanner(title: String, message: String, actionLabel: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            OutlinedButton(onClick = onClick) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun WarningBanner(title: String, message: String, onOpenPermissions: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            OutlinedButton(onClick = onOpenPermissions) {
                Text("去开启")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun criticalPermissionIssues(snapshot: PermissionSnapshot): List<String> {
    val issues = mutableListOf<String>()
    if (!snapshot.exactAlarmsGranted) {
        issues += "精确闹钟未开启，任务可能无法准时触发"
    }
    if (!snapshot.ignoreBatteryOptimizations) {
        issues += "忽略电池优化未开启，后台可能被系统清理导致漏执行"
    }
    if (!snapshot.deviceAdminEnabled) {
        issues += "设备管理器未开启，任务结束后无法真正锁屏"
    }
    return issues
}


private fun taskTriggerSummary(task: TaskEntity): String {
    if (task.enabled && task.repeatRule == RepeatRule.WORKDAY_CN && task.nextTriggerAtMillis == null) {
        return "需先同步中国节假日数据"
    }
    return formatNextTrigger(task.nextTriggerAtMillis)
}

private fun scheduleWindowSuffix(randomWindowMinutes: Int): String {
    return if (randomWindowMinutes <= 0) {
        " · 固定触发"
    } else {
        " · 随机 0-$randomWindowMinutes 分钟"
    }
}

private fun previewDetailText(day: SchedulePreviewDay): String {
    val windowText = day.windows.joinToString(" / ") { window -> formatPreviewWindow(window) }
    return when (day.status) {
        SchedulePreviewStatus.SCHEDULED -> if (windowText.isBlank()) "将执行" else "可能 $windowText"
        SchedulePreviewStatus.SKIPPED -> if (windowText.isBlank()) "已跳过" else "已跳过 $windowText"
        SchedulePreviewStatus.NO_TASK -> "当天不触发"
        SchedulePreviewStatus.WAITING_HOLIDAY_DATA -> "中国节假日数据待同步"
        SchedulePreviewStatus.DISABLED -> "任务已停用"
    }
}

private fun formatPreviewWindow(window: ScheduleWindow): String {
    val startText = window.startsAt.format(timeFormatter)
    if (window.startsAt == window.endsAt) return startText
    val endText = if (window.endsAt.toLocalDate().isAfter(window.startsAt.toLocalDate())) {
        "次日 ${window.endsAt.format(timeFormatter)}"
    } else {
        window.endsAt.format(timeFormatter)
    }
    return "$startText-$endText"
}

@Composable
private fun previewStatusStyle(status: SchedulePreviewStatus): Triple<String, Color, Color> {
    return when (status) {
        SchedulePreviewStatus.SCHEDULED -> Triple(
            "将执行",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        SchedulePreviewStatus.SKIPPED -> Triple(
            "已跳过",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        SchedulePreviewStatus.NO_TASK -> Triple(
            "无任务",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SchedulePreviewStatus.WAITING_HOLIDAY_DATA -> Triple(
            "待同步",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        SchedulePreviewStatus.DISABLED -> Triple(
            "已停用",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun logStatusStyle(status: ExecutionStatus): Triple<String, Color, Color> {
    return when (status) {
        ExecutionStatus.STARTED -> Triple(
            "执行中",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ExecutionStatus.SUCCESS -> Triple(
            "成功",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ExecutionStatus.FAILED -> Triple(
            "失败",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        ExecutionStatus.SKIPPED -> Triple(
            "跳过",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

private fun formatClock(hour: Int, minute: Int): String = String.format(Locale.SIMPLIFIED_CHINESE, "%02d:%02d", hour, minute)

private val skipDateChipFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd E", Locale.SIMPLIFIED_CHINESE)
private val skipDateMessageFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.SIMPLIFIED_CHINESE)
private val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd E", Locale.SIMPLIFIED_CHINESE)
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.SIMPLIFIED_CHINESE)

private fun dayLabel(day: DayOfWeek): String = when (day.value) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
