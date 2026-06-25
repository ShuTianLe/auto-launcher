import type { PreviewStatus, SchedulePreviewDay, ScheduleWindow, Task } from "@/lib/types";

const dayMillis = 24 * 60 * 60 * 1000;

const demoCnHolidays = new Set([
  "2026-01-01",
  "2026-02-16",
  "2026-02-17",
  "2026-02-18",
  "2026-02-19",
  "2026-02-20",
  "2026-04-06",
  "2026-05-01",
  "2026-06-19",
  "2026-09-25",
  "2026-10-01",
  "2026-10-02",
  "2026-10-05",
  "2026-10-06",
  "2026-10-07",
]);

const demoCnMakeupWorkdays = new Set(["2026-02-14", "2026-02-15", "2026-09-20", "2026-10-10"]);

export function todayString(now = new Date()): string {
  return dateToString(startOfLocalDay(now));
}

export function addDays(date: string, amount: number): string {
  return dateToString(new Date(parseDate(date).getTime() + amount * dayMillis));
}

export function dateToString(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function parseDate(date: string): Date {
  const [year, month, day] = date.split("-").map(Number);
  return new Date(year, month - 1, day);
}

export function formatClock(hour: number, minute: number): string {
  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

export function formatDateZh(date: string): string {
  const parsed = parseDate(date);
  return `${parsed.getMonth() + 1}月${parsed.getDate()}日 ${weekdayLabel(parsed.getDay())}`;
}

export function formatDateTimeZh(millis: number): string {
  const date = new Date(millis);
  return `${dateToString(date)} ${formatClock(date.getHours(), date.getMinutes())}`;
}

export function formatWindow(window: ScheduleWindow): string {
  const start = new Date(window.startsAtMillis);
  const end = new Date(window.endsAtMillis);
  const startLabel = `${dateToString(start)} ${formatClock(start.getHours(), start.getMinutes())}`;
  const endLabel = `${dateToString(end)} ${formatClock(end.getHours(), end.getMinutes())}`;
  return startLabel === endLabel ? startLabel : `${startLabel} - ${endLabel}`;
}

export function repeatSummary(task: Task): string {
  if (task.repeatRule === "DAILY") return "每天";
  if (task.repeatRule === "WORKDAY_CN") return "中国工作日";

  const labels = task.weeklyDays
    .slice()
    .sort((a, b) => a - b)
    .map((day) => ["周一", "周二", "周三", "周四", "周五", "周六", "周日"][day - 1])
    .filter(Boolean);
  return labels.length > 0 ? labels.join(" / ") : "指定周几";
}

export function futureSkipDates(task: Task, startDate = todayString()): string[] {
  return task.skipDates
    .filter((date) => date >= startDate)
    .sort((a, b) => a.localeCompare(b));
}

export function buildSevenDayPreview(task: Task, startDate = todayString()): SchedulePreviewDay[] {
  const horizon = Array.from({ length: 7 }, (_, index) => addDays(startDate, index));
  if (!task.enabled) {
    return horizon.map((date) => ({ date, status: "DISABLED", windows: [] }));
  }

  const windowsByDate = new Map<string, ScheduleWindow[]>();
  const lookbackDays = Math.floor(Math.max(task.randomWindowMinutes, 0) / (24 * 60)) + 1;
  const firstBaseDate = addDays(startDate, -lookbackDays);
  const skipSet = new Set(futureSkipDates(task, startDate));

  for (let offset = 0; offset < 7 + lookbackDays; offset += 1) {
    const baseDate = addDays(firstBaseDate, offset);
    if (!matchesRepeatRule(task, baseDate)) continue;

    const window = windowForDate(task, baseDate);
    for (const actualDate of horizon) {
      if (intersectsDate(window, actualDate)) {
        const current = windowsByDate.get(actualDate) ?? [];
        current.push(window);
        windowsByDate.set(actualDate, current);
      }
    }
  }

  return horizon.map((date) => {
    const windows = (windowsByDate.get(date) ?? []).sort((a, b) => a.startsAtMillis - b.startsAtMillis);
    const status: PreviewStatus = windows.length === 0 ? "NO_TASK" : skipSet.has(date) ? "SKIPPED" : "SCHEDULED";
    return { date, status, windows };
  });
}

export function nextPreviewLabel(task: Task, startDate = todayString()): string {
  const preview = buildSevenDayPreview(task, startDate);
  const next = preview.find((day) => day.status === "SCHEDULED" && day.windows.length > 0);
  if (!next) return task.enabled ? "未来 7 天未安排" : "已停用";
  return formatWindow(next.windows[0]);
}

export function matchesRepeatRule(task: Task, date: string): boolean {
  if (task.repeatRule === "DAILY") return true;
  if (task.repeatRule === "WORKDAY_CN") return isDemoChineseWorkday(date);

  const day = parseDate(date).getDay();
  const appDay = day === 0 ? 7 : day;
  return task.weeklyDays.includes(appDay);
}

export function isDemoChineseWorkday(date: string): boolean {
  if (demoCnMakeupWorkdays.has(date)) return true;
  if (demoCnHolidays.has(date)) return false;

  const day = parseDate(date).getDay();
  return day >= 1 && day <= 5;
}

function windowForDate(task: Task, date: string): ScheduleWindow {
  const start = parseDate(date);
  start.setHours(task.hour, task.minute, 0, 0);
  const startsAtMillis = start.getTime();
  return {
    scheduledDate: date,
    startsAtMillis,
    endsAtMillis: startsAtMillis + Math.max(task.randomWindowMinutes, 0) * 60 * 1000,
  };
}

function intersectsDate(window: ScheduleWindow, date: string): boolean {
  const start = parseDate(date).getTime();
  const end = start + dayMillis;
  return window.startsAtMillis < end && window.endsAtMillis >= start;
}

function startOfLocalDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function weekdayLabel(day: number): string {
  return ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][day] ?? "";
}
