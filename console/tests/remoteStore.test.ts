import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import type { PollSnapshot } from "@/lib/server/remoteStore";

process.env.AUTO_LAUNCHER_DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), "auto-launcher-store-"));

describe("remote store", async () => {
  const store = await import("@/lib/server/remoteStore");

  it("registers a device, queues commands, and records poll results", () => {
    const deviceCode = "AL-TEST-0001";
    const secret = "secret-secret-secret-secret";

    expect(store.registerDevice({ deviceCode, secret, displayName: "测试机" })).toEqual({ ok: true });
    expect(store.authenticateDevice(deviceCode, secret)).toBe(true);
    expect(store.authenticateDevice(deviceCode, "wrong-secret-secret-secret")).toBe(false);

    const command = store.enqueueCommand(deviceCode, "ADD_SKIP_DATES", {
      taskId: "42",
      taskName: "测试任务",
      dates: ["2026-07-01", "2026-07-02"],
    });

    const snapshot: PollSnapshot = {
      device: {
        deviceCode,
        displayName: "测试机",
        online: true,
        charging: true,
        batteryPercent: 88,
        appVersion: "1.2.0",
        lastSyncAtMillis: Date.now(),
        timezone: "Asia/Shanghai",
        installedApps: [{ label: "飞书", packageName: "com.ss.android.lark" }],
        permissions: {
          exactAlarmsGranted: true,
          ignoreBatteryOptimizations: true,
          notificationsGranted: true,
          accessibilityEnabled: true,
          deviceAdminEnabled: true,
        },
      },
      tasks: [
        {
          id: "42",
          name: "测试任务",
          hour: 9,
          minute: 0,
          randomWindowMinutes: 10,
          repeatRule: "DAILY",
          weeklyDays: [],
          targetPackage: "com.ss.android.lark",
          targetAppLabel: "飞书",
          waitDurationSeconds: 20,
          enabled: true,
          skipDates: [],
          createdAtMillis: 1,
          updatedAtMillis: 1,
        },
      ],
      executionLogs: [],
    };

    const pending = store.savePollSnapshot(deviceCode, snapshot);
    expect(pending.map((item) => item.id)).toEqual([command.id]);

    store.savePollSnapshot(deviceCode, {
      ...snapshot,
      commandResults: [{ id: command.id, status: "applied" }],
    });

    const state = store.getConsoleState(deviceCode);
    expect(state.device.displayName).toBe("测试机");
    expect(state.tasks).toHaveLength(1);
    expect(state.commandLogs[0].status).toBe("applied");
  });
});
