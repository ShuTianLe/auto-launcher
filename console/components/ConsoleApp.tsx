"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Activity,
  AlarmClock,
  AppWindow,
  BatteryCharging,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
  ListChecks,
  LogOut,
  MonitorSmartphone,
  PauseCircle,
  PlayCircle,
  Plus,
  RefreshCw,
  Save,
  Smartphone,
  Trash2,
  Wifi,
  WifiOff,
  X,
  XCircle,
} from "lucide-react";
import {
  addDays,
  buildSevenDayPreview,
  dateToString,
  formatClock,
  formatDateTimeZh,
  formatDateZh,
  formatWindow,
  futureSkipDates,
  nextPreviewLabel,
  parseDate,
  repeatSummary,
  todayString,
} from "@/lib/schedule";
import type { CommandLog, ConsoleState, ExecutionLog, InstalledApp, PreviewStatus, RepeatRule, Task } from "@/lib/types";

type Section = "overview" | "tasks" | "logs" | "device";

type TaskDraft = {
  name: string;
  hour: number;
  minute: number;
  randomWindowMinutes: number;
  repeatRule: RepeatRule;
  weeklyDays: number[];
  targetPackage: string;
  waitDurationSeconds: number;
  enabled: boolean;
};

const navItems: Array<{ id: Section; label: string; icon: typeof Activity }> = [
  { id: "overview", label: "总览", icon: Activity },
  { id: "tasks", label: "任务", icon: ListChecks },
  { id: "logs", label: "日志", icon: CalendarDays },
  { id: "device", label: "设备", icon: Smartphone },
];

const statusLabel: Record<PreviewStatus, string> = {
  SCHEDULED: "将执行",
  SKIPPED: "已跳过",
  NO_TASK: "无任务",
  WAITING_HOLIDAY_DATA: "等待节假日数据",
  DISABLED: "已停用",
};

const weekOptions = [
  { value: 1, label: "一" },
  { value: 2, label: "二" },
  { value: 3, label: "三" },
  { value: 4, label: "四" },
  { value: 5, label: "五" },
  { value: 6, label: "六" },
  { value: 7, label: "日" },
];

export function ConsoleApp({ initialState }: { initialState: ConsoleState }) {
  const [state, setState] = useState<ConsoleState>(initialState);
  const [section, setSection] = useState<Section>("overview");
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [selectedSkipDates, setSelectedSkipDates] = useState<string[]>([]);
  const [calendarMonth, setCalendarMonth] = useState(() => todayString().slice(0, 7));
  const [taskDraft, setTaskDraft] = useState<TaskDraft | null>(null);
  const [filter, setFilter] = useState<"all" | "execution" | "command">("all");
  const [syncing, setSyncing] = useState(false);
  const [errorText, setErrorText] = useState<string | null>(null);

  const refreshState = useCallback(async (showSpinner = true) => {
    if (showSpinner) setSyncing(true);
    const response = await fetch("/api/console/state", { cache: "no-store" }).catch(() => null);
    if (!response?.ok) {
      setErrorText("无法读取设备快照，请稍后重试。");
      if (showSpinner) setSyncing(false);
      return;
    }
    const body = (await response.json()) as { state?: ConsoleState };
    if (body.state) {
      setState(body.state);
      setErrorText(null);
    }
    if (showSpinner) setSyncing(false);
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => {
      void refreshState(false);
    }, 5_000);
    return () => window.clearInterval(timer);
  }, [refreshState]);

  const selectedTask = useMemo(() => {
    return state.tasks.find((task) => task.id === selectedTaskId) ?? state.tasks[0] ?? null;
  }, [selectedTaskId, state]);

  const today = todayString();
  const enabledTasks = state.tasks.filter((task) => task.enabled);
  const futureSkips = state.tasks.flatMap((task) => futureSkipDates(task).map((date) => ({ task, date })));
  const dueToday = state.tasks.filter((task) =>
    buildSevenDayPreview(task, today)[0]?.status === "SCHEDULED",
  );

  function selectTask(taskId: string) {
    setSelectedTaskId(taskId);
    setSelectedSkipDates([]);
    setCalendarMonth(todayString().slice(0, 7));
  }

  async function queueCommand(type: string, payload: Record<string, unknown>) {
    setSyncing(true);
    const response = await fetch("/api/console/commands", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ type, payload }),
    }).catch(() => null);
    if (!response?.ok) {
      setErrorText("命令下发失败，请确认控制台服务正常。");
      setSyncing(false);
      return false;
    }
    await refreshState(false);
    setSyncing(false);
    return true;
  }

  function toggleTask(task: Task) {
    void queueCommand("SET_TASK_ENABLED", {
      taskId: task.id,
      taskName: task.name,
      enabled: !task.enabled,
    });
  }

  function openCreateTask() {
    setTaskDraft(createDefaultTaskDraft(state.device.installedApps));
    setSection("tasks");
  }

  async function createTask(draft: TaskDraft) {
    const app = state.device.installedApps.find((candidate) => candidate.packageName === draft.targetPackage);
    const name = draft.name.trim();
    const weeklyDays = normalizeWeeklyDays(draft.weeklyDays);

    if (!app || !name || (draft.repeatRule === "WEEKLY" && weeklyDays.length === 0)) return;

    const ok = await queueCommand("CREATE_TASK", {
      name,
      hour: clampInteger(draft.hour, 0, 23),
      minute: clampInteger(draft.minute, 0, 59),
      randomWindowMinutes: clampInteger(draft.randomWindowMinutes, 0, 240),
      repeatRule: draft.repeatRule,
      weeklyDays: draft.repeatRule === "WEEKLY" ? weeklyDays : [],
      targetPackage: app.packageName,
      targetAppLabel: app.label,
      waitDurationSeconds: clampInteger(draft.waitDurationSeconds, 1, 3600),
      enabled: draft.enabled,
    });
    if (ok) {
      setSelectedSkipDates([]);
      setCalendarMonth(todayString().slice(0, 7));
      setTaskDraft(null);
      setSection("logs");
      setFilter("command");
    }
  }

  function toggleSkipDateSelection(date: string) {
    if (date < today) return;
    setSelectedSkipDates((current) =>
      current.includes(date) ? current.filter((candidate) => candidate !== date) : [...current, date].sort(),
    );
  }

  function addSkipDates(task: Task, datesToAdd: string[]) {
    const dates = datesToAdd.filter((date) => date >= today);
    if (dates.length === 0) return;

    void queueCommand("ADD_SKIP_DATES", {
      taskId: task.id,
      taskName: task.name,
      dates,
    });
    setSelectedSkipDates([]);
  }

  function removeSkipDate(task: Task, date: string) {
    void queueCommand("REMOVE_SKIP_DATE", {
      taskId: task.id,
      taskName: task.name,
      date,
    });
  }

  async function logout() {
    await fetch("/api/auth/logout", { method: "POST" });
    window.location.href = "/login";
  }

  return (
    <main className="shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true">
            <MonitorSmartphone size={19} />
          </div>
          <div>
            <strong>Auto Launcher</strong>
            <span>远程控制台</span>
          </div>
        </div>

        <nav className="nav" aria-label="主导航">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                className={section === item.id ? "active" : ""}
                type="button"
                onClick={() => setSection(item.id)}
              >
                <Icon size={17} />
                {item.label}
              </button>
            );
          })}
        </nav>

        <div className="sidebar-status">
          <span className={state.device.online ? "dot online" : "dot offline"} />
          <div>
            <strong>{state.device.online ? "设备在线" : "设备离线"}</strong>
            <span>{formatDateTimeZh(state.device.lastSyncAtMillis)}</span>
          </div>
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">设备 {state.device.deviceCode}</p>
            <h1>{pageTitle(section)}</h1>
          </div>
          <div className="topbar-actions">
            {section === "tasks" ? (
              <button className="primary-button" type="button" onClick={openCreateTask}>
                <Plus size={16} />
                新建任务
              </button>
            ) : null}
            <button className="ghost-button" type="button" onClick={() => void refreshState(true)} disabled={syncing}>
              <RefreshCw className={syncing ? "spin" : ""} size={16} />
              刷新
            </button>
            <button className="icon-button" type="button" aria-label="退出" onClick={logout} title="退出">
              <LogOut size={18} />
            </button>
          </div>
        </header>

        <nav className="mobile-nav" aria-label="移动端导航">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                className={section === item.id ? "active" : ""}
                type="button"
                onClick={() => setSection(item.id)}
              >
                <Icon size={16} />
                {item.label}
              </button>
            );
          })}
        </nav>

        {errorText ? <div className="error-strip">{errorText}</div> : null}

        {section === "overview" ? (
          <OverviewSection
            state={state}
            dueToday={dueToday}
            enabledTasks={enabledTasks}
            futureSkips={futureSkips}
            onOpenTask={(taskId) => {
              selectTask(taskId);
              setSection("tasks");
            }}
          />
        ) : null}

        {section === "tasks" ? (
          <TasksSection
            tasks={state.tasks}
            installedApps={state.device.installedApps}
            selectedTask={selectedTask}
            selectedSkipDates={selectedSkipDates}
            calendarMonth={calendarMonth}
            onCalendarMonthChange={setCalendarMonth}
            onToggleSkipDate={toggleSkipDateSelection}
            onClearSkipSelection={() => setSelectedSkipDates([])}
            onSelectTask={selectTask}
            onOpenCreateTask={openCreateTask}
            onToggleTask={toggleTask}
            onAddSkipDates={addSkipDates}
            onRemoveSkipDate={removeSkipDate}
          />
        ) : null}

        {section === "logs" ? (
          <LogsSection
            executionLogs={state.executionLogs}
            commandLogs={state.commandLogs}
            filter={filter}
            onFilterChange={setFilter}
          />
        ) : null}

        {section === "device" ? <DeviceSection state={state} /> : null}
      </section>

      {taskDraft ? (
        <TaskCreateDialog
          apps={state.device.installedApps}
          draft={taskDraft}
          onClose={() => setTaskDraft(null)}
          onDraftChange={setTaskDraft}
          onSubmit={createTask}
        />
      ) : null}
    </main>
  );
}

function OverviewSection({
  state,
  dueToday,
  enabledTasks,
  futureSkips,
  onOpenTask,
}: {
  state: ConsoleState;
  dueToday: Task[];
  enabledTasks: Task[];
  futureSkips: Array<{ task: Task; date: string }>;
  onOpenTask: (taskId: string) => void;
}) {
  return (
    <div className="section-stack">
      <div className="metric-grid">
        <MetricCard icon={state.device.online ? Wifi : WifiOff} label="设备状态" value={state.device.online ? "在线" : "离线"} detail="最近同步判断" />
        <MetricCard icon={BatteryCharging} label="电量" value={`${state.device.batteryPercent}%`} detail={state.device.charging ? "接入电源" : "未接入电源"} />
        <MetricCard icon={AlarmClock} label="启用任务" value={`${enabledTasks.length}/${state.tasks.length}`} detail="手机执行" />
        <MetricCard icon={CalendarDays} label="今日执行" value={`${dueToday.length}`} detail={dueToday.length > 0 ? dueToday.map((task) => task.name).join("、") : "今天没有已安排任务"} />
      </div>

      <section className="band">
        <div className="section-head">
          <div>
            <h2>未来任务</h2>
            <p>跳过日期会体现在预览里。</p>
          </div>
        </div>
        <div className="timeline-list">
          {state.tasks.map((task) => {
            const preview = buildSevenDayPreview(task);
            return (
              <button className="timeline-row" type="button" key={task.id} onClick={() => onOpenTask(task.id)}>
                <div className="timeline-main">
                  <strong>{task.name}</strong>
                  <span>{nextPreviewLabel(task)}</span>
                </div>
                <div className="mini-days" aria-label={`${task.name} 未来 7 天状态`}>
                  {preview.map((day) => (
                    <span key={day.date} className={`mini-day ${day.status.toLowerCase()}`} title={`${day.date} ${statusLabel[day.status]}`}>
                      {parseInt(day.date.slice(-2), 10)}
                    </span>
                  ))}
                </div>
              </button>
            );
          })}
        </div>
      </section>

      <section className="band">
        <div className="section-head">
          <div>
            <h2>跳过提醒</h2>
            <p>只显示今天及之后的日期，过期日期会自动隐藏。</p>
          </div>
        </div>
        {futureSkips.length > 0 ? (
          <div className="chip-list">
            {futureSkips.map(({ task, date }) => (
              <button className="date-chip" key={`${task.id}-${date}`} type="button" onClick={() => onOpenTask(task.id)}>
                <CalendarDays size={14} />
                {task.name} 跳过 {formatDateZh(date)}
              </button>
            ))}
          </div>
        ) : (
          <EmptyState title="暂无未来跳过日期" detail="可以进入任务详情批量添加一次性跳过日期。" />
        )}
      </section>
    </div>
  );
}

function TasksSection({
  tasks,
  installedApps,
  selectedTask,
  selectedSkipDates,
  calendarMonth,
  onCalendarMonthChange,
  onToggleSkipDate,
  onClearSkipSelection,
  onSelectTask,
  onOpenCreateTask,
  onToggleTask,
  onAddSkipDates,
  onRemoveSkipDate,
}: {
  tasks: Task[];
  installedApps: InstalledApp[];
  selectedTask: Task | null;
  selectedSkipDates: string[];
  calendarMonth: string;
  onCalendarMonthChange: (month: string) => void;
  onToggleSkipDate: (date: string) => void;
  onClearSkipSelection: () => void;
  onSelectTask: (taskId: string) => void;
  onOpenCreateTask: () => void;
  onToggleTask: (task: Task) => void;
  onAddSkipDates: (task: Task, dates: string[]) => void;
  onRemoveSkipDate: (task: Task, date: string) => void;
}) {
  return (
    <div className="task-layout">
      <section className="task-list" aria-label="任务列表">
        <div className="task-list-head">
          <div>
            <strong>{tasks.length} 个任务</strong>
            <span>{installedApps.length} 个 App 上报应用</span>
          </div>
          <button className="icon-button" type="button" onClick={onOpenCreateTask} aria-label="新建任务" title="新建任务">
            <Plus size={17} />
          </button>
        </div>
        {tasks.map((task) => {
          const skips = futureSkipDates(task);
          return (
            <button
              className={`task-card ${selectedTask?.id === task.id ? "selected" : ""}`}
              key={task.id}
              type="button"
              onClick={() => onSelectTask(task.id)}
            >
              <div className="task-card-top">
                <div>
                  <strong>{task.name}</strong>
                  <span>{task.targetAppLabel} · {repeatSummary(task)}</span>
                </div>
                <StatusPill enabled={task.enabled} />
              </div>
              <div className="task-meta-grid">
                <span><Clock3 size={14} />{formatClock(task.hour, task.minute)} + {task.randomWindowMinutes} 分钟</span>
                <span><AlarmClock size={14} />{nextPreviewLabel(task)}</span>
              </div>
              {skips.length > 0 ? (
                <div className="skip-inline">
                  {skips.slice(0, 3).map((date) => (
                    <span key={date}>跳过 {formatDateZh(date)}</span>
                  ))}
                  {skips.length > 3 ? <span>+{skips.length - 3}</span> : null}
                </div>
              ) : null}
            </button>
          );
        })}
        {tasks.length === 0 ? <EmptyState title="暂无任务" detail="先新建一个任务，再查看 7 天时间表。" compact /> : null}
      </section>

      {selectedTask ? (
        <section className="task-detail">
          <div className="detail-head">
            <div>
              <p className="eyebrow">{selectedTask.targetPackage}</p>
              <h2>{selectedTask.name}</h2>
            </div>
            <button className="ghost-button" type="button" onClick={() => onToggleTask(selectedTask)}>
              {selectedTask.enabled ? <PauseCircle size={16} /> : <PlayCircle size={16} />}
              {selectedTask.enabled ? "停用" : "启用"}
            </button>
          </div>

          <div className="detail-grid">
            <InfoTile label="触发时间" value={`${formatClock(selectedTask.hour, selectedTask.minute)} + ${selectedTask.randomWindowMinutes} 分钟`} />
            <InfoTile label="重复规则" value={repeatSummary(selectedTask)} />
            <InfoTile label="目标应用" value={selectedTask.targetAppLabel} />
            <InfoTile label="停留时间" value={`${selectedTask.waitDurationSeconds} 秒`} />
          </div>

          <section className="detail-section">
            <div className="section-head tight">
              <div>
                <h3>跳过日期</h3>
                <p>可一次选择多个一次性日期，过期后不再显示在任务卡片里。</p>
              </div>
            </div>
            <div className="skip-editor calendar-editor">
              <SkipDateCalendar
                existingDates={futureSkipDates(selectedTask)}
                month={calendarMonth}
                selectedDates={selectedSkipDates}
                onMonthChange={onCalendarMonthChange}
                onToggleDate={onToggleSkipDate}
              />
              <div className="calendar-actions">
                <span>{selectedSkipDates.length > 0 ? `已选 ${selectedSkipDates.length} 天` : "未选择日期"}</span>
                <button className="ghost-button" type="button" onClick={onClearSkipSelection} disabled={selectedSkipDates.length === 0}>
                  <X size={16} />
                  清空
                </button>
                <button
                  className="primary-button"
                  type="button"
                  onClick={() => onAddSkipDates(selectedTask, selectedSkipDates)}
                  disabled={selectedSkipDates.length === 0}
                >
                  <Plus size={16} />
                  添加选中日期
                </button>
              </div>
            </div>

            <div className="chip-list">
              {futureSkipDates(selectedTask).length > 0 ? (
                futureSkipDates(selectedTask).map((date) => (
                  <button className="date-chip removable" key={date} type="button" onClick={() => onRemoveSkipDate(selectedTask, date)}>
                    <CalendarDays size={14} />
                    跳过 {formatDateZh(date)}
                    <Trash2 size={13} />
                  </button>
                ))
              ) : (
                <EmptyState title="暂无未来跳过日期" detail="添加后会立即影响任务卡片和 7 天时间表。" compact />
              )}
            </div>
          </section>

          <section className="detail-section">
            <div className="section-head tight">
              <div>
                <h3>未来 7 天时间表</h3>
                <p>随机窗口显示为可触发时间段，跨天窗口按实际日期判断跳过。</p>
              </div>
            </div>
            <div className="preview-table">
              {buildSevenDayPreview(selectedTask).map((day) => (
                <div className="preview-row" key={day.date}>
                  <div>
                    <strong>{formatDateZh(day.date)}</strong>
                    <span>{day.date}</span>
                  </div>
                  <PreviewBadge status={day.status} />
                  <p>{day.windows.length > 0 ? day.windows.map(formatWindow).join("；") : "无触发窗口"}</p>
                </div>
              ))}
            </div>
          </section>
        </section>
      ) : null}
    </div>
  );
}

function TaskCreateDialog({
  apps,
  draft,
  onClose,
  onDraftChange,
  onSubmit,
}: {
  apps: InstalledApp[];
  draft: TaskDraft;
  onClose: () => void;
  onDraftChange: (draft: TaskDraft) => void;
  onSubmit: (draft: TaskDraft) => void;
}) {
  const selectedApp = apps.find((app) => app.packageName === draft.targetPackage);
  const weeklyInvalid = draft.repeatRule === "WEEKLY" && draft.weeklyDays.length === 0;
  const canSubmit = draft.name.trim().length > 0 && Boolean(selectedApp) && !weeklyInvalid;

  function update(partial: Partial<TaskDraft>) {
    onDraftChange({ ...draft, ...partial });
  }

  function toggleWeekday(day: number) {
    const weeklyDays = draft.weeklyDays.includes(day)
      ? draft.weeklyDays.filter((candidate) => candidate !== day)
      : [...draft.weeklyDays, day].sort((a, b) => a - b);
    update({ weeklyDays });
  }

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="task-dialog" role="dialog" aria-modal="true" aria-labelledby="create-task-title">
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (canSubmit) onSubmit(draft);
          }}
        >
          <div className="dialog-head">
            <div>
                <p className="eyebrow">REMOTE COMMAND</p>
              <h2 id="create-task-title">新建任务</h2>
            </div>
            <button className="icon-button" type="button" onClick={onClose} aria-label="关闭" title="关闭">
              <X size={18} />
            </button>
          </div>

          <div className="form-grid">
            <label className="form-field full">
              <span>任务名称</span>
              <input
                value={draft.name}
                onChange={(event) => update({ name: event.target.value })}
                placeholder="例如：上午打卡"
                autoFocus
              />
            </label>

            <label className="form-field">
              <span>目标应用</span>
              <select value={draft.targetPackage} onChange={(event) => update({ targetPackage: event.target.value })}>
                {apps.map((app) => (
                  <option key={app.packageName} value={app.packageName}>
                    {app.label} · {app.packageName}
                  </option>
                ))}
              </select>
            </label>

            <label className="form-field">
              <span>触发时间</span>
              <input
                type="time"
                value={formatClock(draft.hour, draft.minute)}
                onChange={(event) => {
                  const [hour, minute] = event.target.value.split(":").map(Number);
                  update({ hour: clampInteger(hour, 0, 23), minute: clampInteger(minute, 0, 59) });
                }}
              />
            </label>

            <label className="form-field">
              <span>随机窗口（分钟）</span>
              <input
                type="number"
                min={0}
                max={240}
                value={draft.randomWindowMinutes}
                onChange={(event) => update({ randomWindowMinutes: clampInteger(Number(event.target.value), 0, 240) })}
              />
            </label>

            <label className="form-field">
              <span>停留时间（秒）</span>
              <input
                type="number"
                min={1}
                max={3600}
                value={draft.waitDurationSeconds}
                onChange={(event) => update({ waitDurationSeconds: clampInteger(Number(event.target.value), 1, 3600) })}
              />
            </label>

            <label className="form-field full">
              <span>重复规则</span>
              <select
                value={draft.repeatRule}
                onChange={(event) => update({ repeatRule: event.target.value as RepeatRule })}
              >
                <option value="WORKDAY_CN">中国工作日</option>
                <option value="DAILY">每天</option>
                <option value="WEEKLY">指定周几</option>
              </select>
            </label>

            {draft.repeatRule === "WEEKLY" ? (
              <div className="form-field full">
                <span>选择周几</span>
                <div className="weekday-picker" role="group" aria-label="选择周几">
                  {weekOptions.map((day) => (
                    <button
                      key={day.value}
                      className={draft.weeklyDays.includes(day.value) ? "weekday-toggle active" : "weekday-toggle"}
                      type="button"
                      onClick={() => toggleWeekday(day.value)}
                    >
                      {day.label}
                    </button>
                  ))}
                </div>
                {weeklyInvalid ? <small>至少选择一天。</small> : null}
              </div>
            ) : null}

            <label className="checkbox-row full">
              <input
                type="checkbox"
                checked={draft.enabled}
                onChange={(event) => update({ enabled: event.target.checked })}
              />
              创建后立即启用
            </label>
          </div>

          <div className="dialog-actions">
            <button className="ghost-button" type="button" onClick={onClose}>
              取消
            </button>
            <button className="primary-button" type="submit" disabled={!canSubmit}>
              <Save size={16} />
              保存任务
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function SkipDateCalendar({
  existingDates,
  month,
  selectedDates,
  onMonthChange,
  onToggleDate,
}: {
  existingDates: string[];
  month: string;
  selectedDates: string[];
  onMonthChange: (month: string) => void;
  onToggleDate: (date: string) => void;
}) {
  const today = todayString();
  const existingSet = new Set(existingDates);
  const selectedSet = new Set(selectedDates);
  const days = buildCalendarDays(month);

  return (
    <div className="calendar-card">
      <div className="calendar-header">
        <button className="icon-button" type="button" onClick={() => onMonthChange(shiftMonth(month, -1))} aria-label="上个月" title="上个月">
          <ChevronLeft size={17} />
        </button>
        <strong>{formatMonthLabel(month)}</strong>
        <button className="icon-button" type="button" onClick={() => onMonthChange(shiftMonth(month, 1))} aria-label="下个月" title="下个月">
          <ChevronRight size={17} />
        </button>
      </div>

      <div className="calendar-grid">
        {weekOptions.map((day) => (
          <span className="calendar-weekday" key={day.value}>
            {day.label}
          </span>
        ))}
        {days.map((day) => {
          const isPast = day.date < today;
          const isExisting = existingSet.has(day.date);
          const isSelected = selectedSet.has(day.date);
          const className = [
            "calendar-day",
            day.outside ? "outside" : "",
            day.date === today ? "today" : "",
            isExisting ? "existing" : "",
            isSelected ? "selected" : "",
          ]
            .filter(Boolean)
            .join(" ");

          return (
            <button
              key={day.date}
              className={className}
              type="button"
              disabled={isPast || isExisting}
              onClick={() => onToggleDate(day.date)}
              title={isExisting ? `${day.date} 已在跳过列表` : day.date}
            >
              <span>{day.dayNumber}</span>
            </button>
          );
        })}
      </div>

      <div className="calendar-legend">
        <span><i className="legend-selected" />选中</span>
        <span><i className="legend-existing" />已跳过</span>
        <span><i className="legend-today" />今天</span>
      </div>
    </div>
  );
}

function LogsSection({
  executionLogs,
  commandLogs,
  filter,
  onFilterChange,
}: {
  executionLogs: ExecutionLog[];
  commandLogs: CommandLog[];
  filter: "all" | "execution" | "command";
  onFilterChange: (filter: "all" | "execution" | "command") => void;
}) {
  const merged = [
    ...executionLogs.map((log) => ({ type: "execution" as const, createdAtMillis: log.createdAtMillis, log })),
    ...commandLogs.map((log) => ({ type: "command" as const, createdAtMillis: log.createdAtMillis, log })),
  ]
    .filter((entry) => filter === "all" || entry.type === filter)
    .sort((a, b) => b.createdAtMillis - a.createdAtMillis);

  return (
    <section className="band">
      <div className="section-head">
        <div>
          <h2>日志</h2>
          <p>执行日志和远程操作日志分开记录，方便后续排查同步问题。</p>
        </div>
        <div className="segmented">
          {(["all", "execution", "command"] as const).map((item) => (
            <button key={item} className={filter === item ? "active" : ""} type="button" onClick={() => onFilterChange(item)}>
              {item === "all" ? "全部" : item === "execution" ? "执行" : "操作"}
            </button>
          ))}
        </div>
      </div>

      <div className="log-list">
        {merged.map((entry) => {
          if (entry.type === "execution") {
            const log = entry.log;
            return (
              <div className="log-row" key={`execution-${log.id}`}>
                <ExecutionIcon status={log.status} />
                <div>
                  <strong>{log.taskName} · {executionStatusLabel(log.status)}</strong>
                  <span>{formatDateTimeZh(log.createdAtMillis)}</span>
                  <p>{log.detail}</p>
                </div>
              </div>
            );
          }

          const log = entry.log;
          return (
            <div className="log-row" key={`command-${log.id}`}>
              <RefreshCw size={18} />
              <div>
                <strong>{log.action} · {log.taskName} {log.status ? `· ${commandStatusLabel(log.status)}` : ""}</strong>
                <span>{formatDateTimeZh(log.createdAtMillis)}</span>
                <p>{log.detail}</p>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function DeviceSection({ state }: { state: ConsoleState }) {
  const permissions = [
    ["精确闹钟", state.device.permissions.exactAlarmsGranted],
    ["忽略电池优化", state.device.permissions.ignoreBatteryOptimizations],
    ["通知", state.device.permissions.notificationsGranted],
    ["辅助功能", state.device.permissions.accessibilityEnabled],
    ["设备管理器", state.device.permissions.deviceAdminEnabled],
  ] as const;

  return (
    <div className="section-stack">
      <section className="band">
        <div className="device-header">
          <div className="device-icon">
            <Smartphone size={28} />
          </div>
          <div>
            <p className="eyebrow">设备码</p>
            <h2>{state.device.displayName}</h2>
            <p>{state.device.deviceCode} · {state.device.online ? "最近 90 秒内同步" : "等待 App 同步"}</p>
          </div>
          <StatusPill enabled={state.device.online} onlineLabel="在线" offlineLabel="离线" />
        </div>
      </section>

      <div className="metric-grid">
        <MetricCard icon={BatteryCharging} label="电量" value={`${state.device.batteryPercent}%`} detail={state.device.charging ? "接入电源" : "未接入电源"} />
        <MetricCard icon={Clock3} label="最后同步" value={formatDateTimeZh(state.device.lastSyncAtMillis)} detail={state.device.timezone} />
        <MetricCard icon={MonitorSmartphone} label="App 版本" value={state.device.appVersion} detail="当前安装版本" />
      </div>

      <section className="band">
        <div className="section-head">
          <div>
            <h2>权限状态</h2>
            <p>最近一次同步状态。</p>
          </div>
        </div>
        <div className="permission-grid">
          {permissions.map(([label, granted]) => (
            <div className="permission-item" key={label}>
              {granted ? <CheckCircle2 size={18} /> : <XCircle size={18} />}
              <span>{label}</span>
              <strong>{granted ? "已开启" : "未开启"}</strong>
            </div>
          ))}
        </div>
      </section>

      <section className="band">
        <div className="section-head">
          <div>
            <h2>上报应用</h2>
            <p>创建远程任务时使用 App 最近一次上报的应用列表。</p>
          </div>
        </div>
        <div className="app-list">
          {state.device.installedApps.map((app) => (
            <div className="app-item" key={app.packageName}>
              <AppWindow size={17} />
              <div>
                <strong>{app.label}</strong>
                <span>{app.packageName}</span>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function MetricCard({
  icon: Icon,
  label,
  value,
  detail,
}: {
  icon: typeof Activity;
  label: string;
  value: string;
  detail: string;
}) {
  return (
    <section className="metric-card">
      <div className="metric-icon">
        <Icon size={18} />
      </div>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
        <p>{detail}</p>
      </div>
    </section>
  );
}

function StatusPill({
  enabled,
  onlineLabel = "启用",
  offlineLabel = "停用",
}: {
  enabled: boolean;
  onlineLabel?: string;
  offlineLabel?: string;
}) {
  return <span className={`status-pill ${enabled ? "enabled" : "disabled"}`}>{enabled ? onlineLabel : offlineLabel}</span>;
}

function PreviewBadge({ status }: { status: PreviewStatus }) {
  return <span className={`preview-badge ${status.toLowerCase()}`}>{statusLabel[status]}</span>;
}

function InfoTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="info-tile">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function EmptyState({ title, detail, compact = false }: { title: string; detail: string; compact?: boolean }) {
  return (
    <div className={`empty-state ${compact ? "compact" : ""}`}>
      <strong>{title}</strong>
      <span>{detail}</span>
    </div>
  );
}

function ExecutionIcon({ status }: { status: ExecutionLog["status"] }) {
  if (status === "SUCCESS") return <CheckCircle2 className="success" size={18} />;
  if (status === "FAILED") return <XCircle className="failed" size={18} />;
  if (status === "SKIPPED") return <PauseCircle className="skipped" size={18} />;
  return <PlayCircle className="started" size={18} />;
}

function executionStatusLabel(status: ExecutionLog["status"]): string {
  if (status === "SUCCESS") return "成功";
  if (status === "FAILED") return "失败";
  if (status === "SKIPPED") return "已跳过";
  return "开始执行";
}

function commandStatusLabel(status: NonNullable<CommandLog["status"]>): string {
  if (status === "queued") return "待同步";
  if (status === "delivered") return "已下发";
  if (status === "applied") return "已应用";
  return "失败";
}

function pageTitle(section: Section): string {
  if (section === "tasks") return "任务控制";
  if (section === "logs") return "日志";
  if (section === "device") return "设备";
  return "总览";
}

function createDefaultTaskDraft(apps: InstalledApp[]): TaskDraft {
  return {
    name: "",
    hour: 9,
    minute: 0,
    randomWindowMinutes: 10,
    repeatRule: "WORKDAY_CN",
    weeklyDays: [1, 2, 3, 4, 5],
    targetPackage: apps[0]?.packageName ?? "",
    waitDurationSeconds: 20,
    enabled: true,
  };
}

function clampInteger(value: number, min: number, max: number): number {
  if (!Number.isFinite(value)) return min;
  return Math.min(Math.max(Math.trunc(value), min), max);
}

function normalizeWeeklyDays(days: number[]): number[] {
  return Array.from(new Set(days.filter((day) => day >= 1 && day <= 7))).sort((a, b) => a - b);
}

function buildCalendarDays(month: string): Array<{ date: string; dayNumber: number; outside: boolean }> {
  const [year, monthNumber] = month.split("-").map(Number);
  const firstOfMonth = new Date(year, monthNumber - 1, 1);
  const mondayOffset = (firstOfMonth.getDay() + 6) % 7;
  const firstVisibleDate = addDays(dateToString(firstOfMonth), -mondayOffset);

  return Array.from({ length: 42 }, (_, index) => {
    const date = addDays(firstVisibleDate, index);
    const parsed = parseDate(date);
    return {
      date,
      dayNumber: parsed.getDate(),
      outside: parsed.getMonth() !== monthNumber - 1,
    };
  });
}

function formatMonthLabel(month: string): string {
  const [year, monthNumber] = month.split("-").map(Number);
  return `${year}年${monthNumber}月`;
}

function shiftMonth(month: string, amount: number): string {
  const [year, monthNumber] = month.split("-").map(Number);
  const next = new Date(year, monthNumber - 1 + amount, 1);
  return dateToString(next).slice(0, 7);
}
