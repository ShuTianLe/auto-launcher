import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import Database from "better-sqlite3";
import { createDemoState } from "@/lib/demoData";
import { todayString } from "@/lib/schedule";
import type {
  CommandLog,
  ConsoleState,
  DevicePermissions,
  DeviceSnapshot,
  ExecutionLog,
  InstalledApp,
  RemoteCommand,
  RemoteCommandStatus,
  RemoteCommandType,
  Task,
} from "@/lib/types";

type Db = Database.Database;

type DeviceRow = {
  deviceCode: string;
  secretHash: string;
  displayName: string | null;
  online: number;
  charging: number;
  batteryPercent: number;
  appVersion: string | null;
  lastSeenAtMillis: number;
  timezone: string | null;
  permissionsJson: string | null;
  installedAppsJson: string | null;
  createdAtMillis: number;
  updatedAtMillis: number;
};

type StateRow = {
  deviceCode: string;
  tasksJson: string;
  executionLogsJson: string;
  updatedAtMillis: number;
};

type CommandRow = {
  id: string;
  deviceCode: string;
  type: RemoteCommandType;
  payloadJson: string;
  status: RemoteCommandStatus;
  error: string | null;
  createdAtMillis: number;
  deliveredAtMillis: number | null;
  completedAtMillis: number | null;
};

export type PollCommandResult = {
  id: string;
  status: "applied" | "failed";
  error?: string | null;
};

export type PollSnapshot = {
  device: DeviceSnapshot;
  tasks: Task[];
  executionLogs: ExecutionLog[];
  commandResults?: PollCommandResult[];
};

let db: Db | null = null;

export function hasRegisteredDevice(deviceCode: string): boolean {
  return Boolean(getDeviceRow(deviceCode));
}

export function registerDevice(input: {
  deviceCode: string;
  secret: string;
  displayName?: string;
  appVersion?: string;
}): { ok: true } | { ok: false; status: 409; message: string } {
  const now = Date.now();
  const existing = getDeviceRow(input.deviceCode);
  const secretHash = hashSecret(input.secret);

  if (existing && existing.secretHash !== secretHash) {
    return { ok: false, status: 409, message: "设备码已注册" };
  }

  const database = getDb();
  if (existing) {
    database.prepare(
      `
      UPDATE devices
      SET displayName = COALESCE(@displayName, displayName),
          appVersion = COALESCE(@appVersion, appVersion),
          updatedAtMillis = @now
      WHERE deviceCode = @deviceCode
      `,
    ).run({
      deviceCode: input.deviceCode,
      displayName: input.displayName ?? null,
      appVersion: input.appVersion ?? null,
      now,
    });
    return { ok: true };
  }

  database.prepare(
    `
    INSERT INTO devices (
      deviceCode, secretHash, displayName, online, charging, batteryPercent,
      appVersion, lastSeenAtMillis, timezone, permissionsJson, installedAppsJson,
      createdAtMillis, updatedAtMillis
    )
    VALUES (
      @deviceCode, @secretHash, @displayName, 0, 0, 0,
      @appVersion, 0, NULL, NULL, NULL, @now, @now
    )
    `,
  ).run({
    deviceCode: input.deviceCode,
    secretHash,
    displayName: input.displayName ?? "Auto Launcher 设备",
    appVersion: input.appVersion ?? null,
    now,
  });

  database.prepare(
    `
    INSERT INTO device_state (deviceCode, tasksJson, executionLogsJson, updatedAtMillis)
    VALUES (@deviceCode, '[]', '[]', @now)
    `,
  ).run({ deviceCode: input.deviceCode, now });
  return { ok: true };
}

export function authenticateDevice(deviceCode: string, secret: string): boolean {
  const row = getDeviceRow(deviceCode);
  if (!row) return false;
  return safeEqual(row.secretHash, hashSecret(secret));
}

export function savePollSnapshot(deviceCode: string, snapshot: PollSnapshot): RemoteCommand[] {
  const now = Date.now();
  const database = getDb();
  const tx = database.transaction(() => {
    database.prepare(
      `
      UPDATE devices
      SET displayName = @displayName,
          online = 1,
          charging = @charging,
          batteryPercent = @batteryPercent,
          appVersion = @appVersion,
          lastSeenAtMillis = @lastSeenAtMillis,
          timezone = @timezone,
          permissionsJson = @permissionsJson,
          installedAppsJson = @installedAppsJson,
          updatedAtMillis = @now
      WHERE deviceCode = @deviceCode
      `,
    ).run({
      deviceCode,
      displayName: snapshot.device.displayName,
      charging: snapshot.device.charging ? 1 : 0,
      batteryPercent: clamp(snapshot.device.batteryPercent, 0, 100),
      appVersion: snapshot.device.appVersion,
      lastSeenAtMillis: now,
      timezone: snapshot.device.timezone,
      permissionsJson: JSON.stringify(snapshot.device.permissions),
      installedAppsJson: JSON.stringify(snapshot.device.installedApps),
      now,
    });

    database.prepare(
      `
      INSERT INTO device_state (deviceCode, tasksJson, executionLogsJson, updatedAtMillis)
      VALUES (@deviceCode, @tasksJson, @executionLogsJson, @now)
      ON CONFLICT(deviceCode) DO UPDATE SET
        tasksJson = excluded.tasksJson,
        executionLogsJson = excluded.executionLogsJson,
        updatedAtMillis = excluded.updatedAtMillis
      `,
    ).run({
      deviceCode,
      tasksJson: JSON.stringify(normalizeTasks(snapshot.tasks)),
      executionLogsJson: JSON.stringify(snapshot.executionLogs.slice(0, 200)),
      now,
    });

    for (const result of snapshot.commandResults ?? []) {
      if (!["applied", "failed"].includes(result.status)) continue;
      database.prepare(
        `
        UPDATE commands
        SET status = @status,
            error = @error,
            completedAtMillis = @now
        WHERE deviceCode = @deviceCode AND id = @id
        `,
      ).run({
        deviceCode,
        id: result.id,
        status: result.status,
        error: result.error ?? null,
        now,
      });
    }
  });
  tx();
  return listPendingCommands(deviceCode);
}

export function listPendingCommands(deviceCode: string): RemoteCommand[] {
  const now = Date.now();
  const rows = getDb().prepare(
    `
    SELECT * FROM commands
    WHERE deviceCode = @deviceCode
      AND status IN ('queued', 'delivered')
    ORDER BY createdAtMillis ASC
    LIMIT 25
    `,
  ).all({ deviceCode }) as CommandRow[];

  const commands = rows.map(commandFromRow);
  const markDelivered = getDb().prepare(
    `
    UPDATE commands
    SET status = 'delivered',
        deliveredAtMillis = COALESCE(deliveredAtMillis, @now)
    WHERE id = @id
    `,
  );
  for (const command of commands) {
    if (command.status === "queued") markDelivered.run({ id: command.id, now });
  }
  return commands;
}

export function enqueueCommand(deviceCode: string, type: RemoteCommandType, payload: unknown): RemoteCommand {
  const command: RemoteCommand = {
    id: `cmd-${Date.now()}-${crypto.randomBytes(4).toString("hex")}`,
    type,
    payload,
    status: "queued",
    error: null,
    createdAtMillis: Date.now(),
    deliveredAtMillis: null,
    completedAtMillis: null,
  };
  getDb().prepare(
    `
    INSERT INTO commands (
      id, deviceCode, type, payloadJson, status, error,
      createdAtMillis, deliveredAtMillis, completedAtMillis
    )
    VALUES (
      @id, @deviceCode, @type, @payloadJson, @status, @error,
      @createdAtMillis, @deliveredAtMillis, @completedAtMillis
    )
    `,
  ).run({
    ...command,
    deviceCode,
    payloadJson: JSON.stringify(command.payload),
  });
  return command;
}

export function getConsoleState(deviceCode: string): ConsoleState {
  const row = getDeviceRow(deviceCode);
  if (!row) return createDemoState();

  const stateRow = getDb().prepare("SELECT * FROM device_state WHERE deviceCode = ?").get(deviceCode) as StateRow | undefined;
  const tasks = normalizeTasks(readJson<Task[]>(stateRow?.tasksJson, []));
  const executionLogs = readJson<ExecutionLog[]>(stateRow?.executionLogsJson, []);
  const updatedAtMillis = stateRow?.updatedAtMillis ?? row.updatedAtMillis;
  return {
    schemaVersion: 2,
    device: deviceFromRow(row),
    tasks,
    executionLogs,
    commandLogs: commandLogsForDevice(deviceCode),
    updatedAtMillis,
  };
}

export function commandLogsForDevice(deviceCode: string): CommandLog[] {
  const rows = getDb().prepare(
    `
    SELECT * FROM commands
    WHERE deviceCode = @deviceCode
    ORDER BY createdAtMillis DESC
    LIMIT 120
    `,
  ).all({ deviceCode }) as CommandRow[];
  return rows.map((row) => {
    const payload = readJson<Record<string, unknown>>(row.payloadJson, {});
    return {
      id: row.id,
      taskId: typeof payload.taskId === "string" || typeof payload.taskId === "number" ? String(payload.taskId) : null,
      taskName: typeof payload.name === "string" ? payload.name : typeof payload.taskName === "string" ? payload.taskName : "远程命令",
      action: commandActionLabel(row.type),
      detail: commandDetail(row, payload),
      status: row.status,
      createdAtMillis: row.createdAtMillis,
    };
  });
}

function getDb(): Db {
  if (db) return db;
  const dataDir = process.env.AUTO_LAUNCHER_DATA_DIR?.trim() || path.join(process.cwd(), ".data");
  fs.mkdirSync(dataDir, { recursive: true });
  db = new Database(path.join(dataDir, "remote-console.sqlite"));
  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");
  migrate(db);
  return db;
}

function migrate(database: Db): void {
  database.exec(`
    CREATE TABLE IF NOT EXISTS devices (
      deviceCode TEXT PRIMARY KEY,
      secretHash TEXT NOT NULL,
      displayName TEXT,
      online INTEGER NOT NULL DEFAULT 0,
      charging INTEGER NOT NULL DEFAULT 0,
      batteryPercent INTEGER NOT NULL DEFAULT 0,
      appVersion TEXT,
      lastSeenAtMillis INTEGER NOT NULL DEFAULT 0,
      timezone TEXT,
      permissionsJson TEXT,
      installedAppsJson TEXT,
      createdAtMillis INTEGER NOT NULL,
      updatedAtMillis INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS device_state (
      deviceCode TEXT PRIMARY KEY REFERENCES devices(deviceCode) ON DELETE CASCADE,
      tasksJson TEXT NOT NULL,
      executionLogsJson TEXT NOT NULL,
      updatedAtMillis INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS commands (
      id TEXT PRIMARY KEY,
      deviceCode TEXT NOT NULL REFERENCES devices(deviceCode) ON DELETE CASCADE,
      type TEXT NOT NULL,
      payloadJson TEXT NOT NULL,
      status TEXT NOT NULL,
      error TEXT,
      createdAtMillis INTEGER NOT NULL,
      deliveredAtMillis INTEGER,
      completedAtMillis INTEGER
    );

    CREATE INDEX IF NOT EXISTS index_commands_device_status_created
      ON commands(deviceCode, status, createdAtMillis);
  `);
}

function getDeviceRow(deviceCode: string): DeviceRow | undefined {
  return getDb().prepare("SELECT * FROM devices WHERE deviceCode = ?").get(deviceCode) as DeviceRow | undefined;
}

function deviceFromRow(row: DeviceRow): DeviceSnapshot {
  const now = Date.now();
  const online = row.lastSeenAtMillis > 0 && now - row.lastSeenAtMillis < 90_000;
  return {
    deviceCode: row.deviceCode,
    displayName: row.displayName ?? "Auto Launcher 设备",
    online,
    charging: row.charging === 1,
    batteryPercent: clamp(row.batteryPercent, 0, 100),
    appVersion: row.appVersion ?? "未知",
    lastSyncAtMillis: row.lastSeenAtMillis || row.updatedAtMillis,
    timezone: row.timezone ?? "Asia/Shanghai",
    installedApps: readJson<InstalledApp[]>(row.installedAppsJson, []),
    permissions: readJson<DevicePermissions>(row.permissionsJson, {
      exactAlarmsGranted: false,
      ignoreBatteryOptimizations: false,
      notificationsGranted: false,
      accessibilityEnabled: false,
      deviceAdminEnabled: false,
    }),
  };
}

function commandFromRow(row: CommandRow): RemoteCommand {
  return {
    id: row.id,
    type: row.type,
    payload: readJson(row.payloadJson, {}),
    status: row.status,
    error: row.error,
    createdAtMillis: row.createdAtMillis,
    deliveredAtMillis: row.deliveredAtMillis,
    completedAtMillis: row.completedAtMillis,
  };
}

function normalizeTasks(tasks: Task[]): Task[] {
  const today = todayString();
  return tasks.map((task) => ({
    ...task,
    id: String(task.id),
    weeklyDays: Array.isArray(task.weeklyDays) ? task.weeklyDays : [],
    skipDates: Array.from(new Set((task.skipDates ?? []).filter((date) => date >= today))).sort(),
  }));
}

function commandActionLabel(type: RemoteCommandType): string {
  if (type === "CREATE_TASK") return "新建任务";
  if (type === "SET_TASK_ENABLED") return "切换任务";
  if (type === "ADD_SKIP_DATES") return "添加跳过日期";
  return "删除跳过日期";
}

function commandDetail(row: CommandRow, payload: Record<string, unknown>): string {
  const suffix = row.status === "failed" && row.error ? `，失败原因：${row.error}` : "";
  if (row.type === "CREATE_TASK") return `等待 App 创建任务${suffix}`;
  if (row.type === "SET_TASK_ENABLED") return `等待 App ${payload.enabled ? "启用" : "停用"}任务${suffix}`;
  if (row.type === "ADD_SKIP_DATES") return `等待 App 添加 ${(payload.dates as unknown[] | undefined)?.join("、") ?? "跳过日期"}${suffix}`;
  return `等待 App 删除 ${String(payload.date ?? "跳过日期")}${suffix}`;
}

function readJson<T>(value: string | null | undefined, fallback: T): T {
  if (!value) return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

function hashSecret(secret: string): string {
  return crypto.createHash("sha256").update(secret).digest("hex");
}

function safeEqual(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  return leftBuffer.length === rightBuffer.length && crypto.timingSafeEqual(leftBuffer, rightBuffer);
}

function clamp(value: number, min: number, max: number): number {
  if (!Number.isFinite(value)) return min;
  return Math.min(Math.max(Math.trunc(value), min), max);
}
