export type RepeatRule = "DAILY" | "WORKDAY_CN" | "WEEKLY";

export type ExecutionStatus = "STARTED" | "SUCCESS" | "FAILED" | "SKIPPED";

export type PreviewStatus = "SCHEDULED" | "SKIPPED" | "NO_TASK" | "WAITING_HOLIDAY_DATA" | "DISABLED";

export interface DevicePermissions {
  exactAlarmsGranted: boolean;
  ignoreBatteryOptimizations: boolean;
  notificationsGranted: boolean;
  accessibilityEnabled: boolean;
  deviceAdminEnabled: boolean;
}

export interface DeviceSnapshot {
  deviceCode: string;
  displayName: string;
  online: boolean;
  charging: boolean;
  batteryPercent: number;
  appVersion: string;
  lastSyncAtMillis: number;
  timezone: string;
  permissions: DevicePermissions;
}

export interface Task {
  id: string;
  name: string;
  hour: number;
  minute: number;
  randomWindowMinutes: number;
  repeatRule: RepeatRule;
  weeklyDays: number[];
  targetPackage: string;
  targetAppLabel: string;
  waitDurationSeconds: number;
  enabled: boolean;
  skipDates: string[];
  createdAtMillis: number;
  updatedAtMillis: number;
}

export interface ScheduleWindow {
  scheduledDate: string;
  startsAtMillis: number;
  endsAtMillis: number;
}

export interface SchedulePreviewDay {
  date: string;
  status: PreviewStatus;
  windows: ScheduleWindow[];
}

export interface ExecutionLog {
  id: string;
  taskId: string | null;
  taskName: string;
  status: ExecutionStatus;
  detail: string;
  createdAtMillis: number;
}

export interface CommandLog {
  id: string;
  taskId: string | null;
  taskName: string;
  action: string;
  detail: string;
  createdAtMillis: number;
}

export interface ConsoleState {
  schemaVersion: 1;
  device: DeviceSnapshot;
  tasks: Task[];
  executionLogs: ExecutionLog[];
  commandLogs: CommandLog[];
  updatedAtMillis: number;
}
