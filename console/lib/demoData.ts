import type { CommandLog, ConsoleState, ExecutionLog, Task } from "@/lib/types";
import { addDays, todayString } from "@/lib/schedule";

export const storageVersion = 2;

const demoInstalledApps = [
  { label: "飞书", packageName: "com.ss.android.lark" },
  { label: "微信", packageName: "com.tencent.mm" },
  { label: "钉钉", packageName: "com.alibaba.android.rimet" },
  { label: "企业微信", packageName: "com.tencent.wework" },
  { label: "支付宝", packageName: "com.eg.android.AlipayGphone" },
  { label: "Auto Launcher", packageName: "com.stl.autolauncher" },
];

export function createDemoState(now = new Date()): ConsoleState {
  const today = todayString(now);
  const nowMillis = now.getTime();

  const tasks: Task[] = [
    {
      id: "task-lark-morning",
      name: "飞书极速打卡",
      hour: 9,
      minute: 18,
      randomWindowMinutes: 12,
      repeatRule: "WORKDAY_CN",
      weeklyDays: [],
      targetPackage: "com.ss.android.lark",
      targetAppLabel: "飞书",
      waitDurationSeconds: 20,
      enabled: true,
      skipDates: [addDays(today, -1), addDays(today, 1), addDays(today, 4)],
      createdAtMillis: nowMillis - 12 * 24 * 60 * 60 * 1000,
      updatedAtMillis: nowMillis - 4 * 60 * 1000,
    },
    {
      id: "task-evening-check",
      name: "下班确认",
      hour: 18,
      minute: 25,
      randomWindowMinutes: 20,
      repeatRule: "WEEKLY",
      weeklyDays: [1, 2, 3, 4, 5],
      targetPackage: "com.ss.android.lark",
      targetAppLabel: "飞书",
      waitDurationSeconds: 15,
      enabled: true,
      skipDates: [addDays(today, 2)],
      createdAtMillis: nowMillis - 9 * 24 * 60 * 60 * 1000,
      updatedAtMillis: nowMillis - 18 * 60 * 1000,
    },
    {
      id: "task-weekly-report",
      name: "周报提醒",
      hour: 17,
      minute: 35,
      randomWindowMinutes: 0,
      repeatRule: "WEEKLY",
      weeklyDays: [5],
      targetPackage: "com.tencent.mm",
      targetAppLabel: "微信",
      waitDurationSeconds: 10,
      enabled: false,
      skipDates: [],
      createdAtMillis: nowMillis - 20 * 24 * 60 * 60 * 1000,
      updatedAtMillis: nowMillis - 2 * 24 * 60 * 60 * 1000,
    },
  ];

  const executionLogs: ExecutionLog[] = [
    {
      id: "log-1",
      taskId: "task-lark-morning",
      taskName: "飞书极速打卡",
      status: "SUCCESS",
      detail: "已启动飞书并停留 20 秒，随后返回 Auto Launcher。",
      createdAtMillis: nowMillis - 2 * 60 * 60 * 1000,
    },
    {
      id: "log-2",
      taskId: "task-evening-check",
      taskName: "下班确认",
      status: "SKIPPED",
      detail: `命中跳过日期 ${today}，未启动目标应用。`,
      createdAtMillis: nowMillis - 15 * 60 * 60 * 1000,
    },
    {
      id: "log-3",
      taskId: "task-lark-morning",
      taskName: "飞书极速打卡",
      status: "STARTED",
      detail: "服务已开始执行，正在拉起目标应用。",
      createdAtMillis: nowMillis - 26 * 60 * 60 * 1000,
    },
  ];

  const commandLogs: CommandLog[] = [
    {
      id: "cmd-1",
      taskId: "task-lark-morning",
      taskName: "飞书极速打卡",
      action: "添加跳过日期",
      detail: `已添加 ${addDays(today, 1)}、${addDays(today, 4)}`,
      createdAtMillis: nowMillis - 6 * 60 * 1000,
    },
    {
      id: "cmd-2",
      taskId: "task-evening-check",
      taskName: "下班确认",
      action: "添加跳过日期",
      detail: `已添加 ${addDays(today, 2)}`,
      createdAtMillis: nowMillis - 18 * 60 * 1000,
    },
  ];

  return {
    schemaVersion: storageVersion,
    device: {
      deviceCode: "AUTO-DEMO-19930630",
      displayName: "公司固定打卡机",
      online: true,
      charging: true,
      batteryPercent: 86,
      appVersion: "1.1.0",
      lastSyncAtMillis: nowMillis - 90 * 1000,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Shanghai",
      installedApps: demoInstalledApps,
      permissions: {
        exactAlarmsGranted: true,
        ignoreBatteryOptimizations: true,
        notificationsGranted: true,
        accessibilityEnabled: true,
        deviceAdminEnabled: true,
      },
    },
    tasks,
    executionLogs,
    commandLogs,
    updatedAtMillis: nowMillis,
  };
}
