"use client";

import { useEffect, useMemo, useState } from "react";
import {
  Activity,
  AlarmClock,
  BatteryCharging,
  CalendarDays,
  CheckCircle2,
  Clock3,
  ListChecks,
  LogOut,
  MonitorSmartphone,
  PauseCircle,
  PlayCircle,
  Plus,
  RefreshCw,
  Smartphone,
  Trash2,
  Wifi,
  WifiOff,
  XCircle,
} from "lucide-react";
import { appendCommandLog, loadConsoleState, resetConsoleState, saveConsoleState } from "@/lib/storage";
import {
  buildSevenDayPreview,
  formatClock,
  formatDateTimeZh,
  formatDateZh,
  formatWindow,
  futureSkipDates,
  nextPreviewLabel,
  repeatSummary,
  todayString,
} from "@/lib/schedule";
import type { CommandLog, ConsoleState, ExecutionLog, PreviewStatus, Task } from "@/lib/types";

type Section = "overview" | "tasks" | "logs" | "device";

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

export function ConsoleApp() {
  const [state, setState] = useState<ConsoleState>(() => loadConsoleState());
  const [section, setSection] = useState<Section>("overview");
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [dateInput, setDateInput] = useState("");
  const [filter, setFilter] = useState<"all" | "execution" | "command">("all");

  useEffect(() => {
    saveConsoleState(state);
  }, [state]);

  const selectedTask = useMemo(() => {
    return state.tasks.find((task) => task.id === selectedTaskId) ?? state.tasks[0] ?? null;
  }, [selectedTaskId, state]);

  const today = todayString();
  const enabledTasks = state.tasks.filter((task) => task.enabled);
  const futureSkips = state.tasks.flatMap((task) => futureSkipDates(task).map((date) => ({ task, date })));
  const dueToday = state.tasks.filter((task) =>
    buildSevenDayPreview(task, today)[0]?.status === "SCHEDULED",
  );

  function commit(updater: (current: ConsoleState) => ConsoleState) {
    setState((current) => updater(current));
  }

  function updateTask(taskId: string, updater: (task: Task) => Task, action: string, detail: string) {
    commit((current) => {
      const task = current.tasks.find((candidate) => candidate.id === taskId) ?? null;
      const next = {
        ...current,
        tasks: current.tasks.map((candidate) =>
          candidate.id === taskId ? updater(candidate) : candidate,
        ),
        updatedAtMillis: Date.now(),
      };
      return appendCommandLog(next, task, action, detail);
    });
  }

  function toggleTask(task: Task) {
    updateTask(
      task.id,
      (current) => ({ ...current, enabled: !current.enabled, updatedAtMillis: Date.now() }),
      task.enabled ? "停用任务" : "启用任务",
      task.enabled ? "任务已停用，未来时间表不再安排执行。" : "任务已启用，已重新计算未来时间表。",
    );
  }

  function addSkipDates(task: Task) {
    const dates = parseDateInput(dateInput).filter((date) => date >= today);
    if (dates.length === 0) return;

    updateTask(
      task.id,
      (current) => ({
        ...current,
        skipDates: Array.from(new Set([...current.skipDates, ...dates])).sort(),
        updatedAtMillis: Date.now(),
      }),
      "添加跳过日期",
      `已添加 ${dates.join("、")}`,
    );
    setDateInput("");
  }

  function removeSkipDate(task: Task, date: string) {
    updateTask(
      task.id,
      (current) => ({
        ...current,
        skipDates: current.skipDates.filter((candidate) => candidate !== date),
        updatedAtMillis: Date.now(),
      }),
      "删除跳过日期",
      `已删除 ${date}`,
    );
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
            <button className="ghost-button" type="button" onClick={() => setState(resetConsoleState())}>
              <RefreshCw size={16} />
              重置演示数据
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

        {section === "overview" ? (
          <OverviewSection
            state={state}
            dueToday={dueToday}
            enabledTasks={enabledTasks}
            futureSkips={futureSkips}
            onOpenTask={(taskId) => {
              setSelectedTaskId(taskId);
              setSection("tasks");
            }}
          />
        ) : null}

        {section === "tasks" ? (
          <TasksSection
            tasks={state.tasks}
            selectedTask={selectedTask}
            dateInput={dateInput}
            onDateInputChange={setDateInput}
            onSelectTask={setSelectedTaskId}
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
        <MetricCard icon={state.device.online ? Wifi : WifiOff} label="设备状态" value={state.device.online ? "在线" : "离线"} detail="每 90 秒模拟同步一次" />
        <MetricCard icon={BatteryCharging} label="电量" value={`${state.device.batteryPercent}%`} detail={state.device.charging ? "正在充电" : "未充电"} />
        <MetricCard icon={AlarmClock} label="启用任务" value={`${enabledTasks.length}/${state.tasks.length}`} detail="本地调度仍由手机执行" />
        <MetricCard icon={CalendarDays} label="今日执行" value={`${dueToday.length}`} detail={dueToday.length > 0 ? dueToday.map((task) => task.name).join("、") : "今天没有已安排任务"} />
      </div>

      <section className="band">
        <div className="section-head">
          <div>
            <h2>未来任务</h2>
            <p>按当前假设备数据计算，跳过日期会直接体现在预览里。</p>
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
            <p>这里只显示今天及之后的日期，过期日期会自动隐藏。</p>
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
  selectedTask,
  dateInput,
  onDateInputChange,
  onSelectTask,
  onToggleTask,
  onAddSkipDates,
  onRemoveSkipDate,
}: {
  tasks: Task[];
  selectedTask: Task | null;
  dateInput: string;
  onDateInputChange: (value: string) => void;
  onSelectTask: (taskId: string) => void;
  onToggleTask: (task: Task) => void;
  onAddSkipDates: (task: Task) => void;
  onRemoveSkipDate: (task: Task, date: string) => void;
}) {
  return (
    <div className="task-layout">
      <section className="task-list" aria-label="任务列表">
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
                <p>支持一次添加多个日期，格式为 YYYY-MM-DD，可用逗号、空格或换行分隔。</p>
              </div>
            </div>
            <div className="skip-editor">
              <textarea
                value={dateInput}
                onChange={(event) => onDateInputChange(event.target.value)}
                placeholder={`${todayString()}\n${nextTomorrowPlaceholder()}`}
                rows={3}
              />
              <button className="primary-button" type="button" onClick={() => onAddSkipDates(selectedTask)}>
                <Plus size={16} />
                添加日期
              </button>
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
                <strong>{log.action} · {log.taskName}</strong>
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
            <p className="eyebrow">假设备码</p>
            <h2>{state.device.displayName}</h2>
            <p>{state.device.deviceCode}</p>
          </div>
          <StatusPill enabled={state.device.online} onlineLabel="在线" offlineLabel="离线" />
        </div>
      </section>

      <div className="metric-grid">
        <MetricCard icon={BatteryCharging} label="电量" value={`${state.device.batteryPercent}%`} detail={state.device.charging ? "正在充电" : "未充电"} />
        <MetricCard icon={Clock3} label="最后同步" value={formatDateTimeZh(state.device.lastSyncAtMillis)} detail={state.device.timezone} />
        <MetricCard icon={MonitorSmartphone} label="App 版本" value={state.device.appVersion} detail="控制台原型未连接真机" />
      </div>

      <section className="band">
        <div className="section-head">
          <div>
            <h2>权限状态</h2>
            <p>正式联动后由手机端上报，这里先用假数据展示。</p>
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

function parseDateInput(value: string): string[] {
  const datePattern = /^\d{4}-\d{2}-\d{2}$/;
  return value
    .split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter((item) => datePattern.test(item));
}

function nextTomorrowPlaceholder(): string {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function pageTitle(section: Section): string {
  if (section === "tasks") return "任务控制";
  if (section === "logs") return "日志";
  if (section === "device") return "设备";
  return "总览";
}
