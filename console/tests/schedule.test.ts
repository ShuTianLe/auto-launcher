import { describe, expect, it } from "vitest";
import { addDays, buildSevenDayPreview, futureSkipDates, isDemoChineseWorkday, nextPreviewLabel } from "@/lib/schedule";
import type { Task } from "@/lib/types";

const baseTask: Task = {
  id: "task",
  name: "测试任务",
  hour: 9,
  minute: 0,
  randomWindowMinutes: 30,
  repeatRule: "DAILY",
  weeklyDays: [],
  targetPackage: "com.example",
  targetAppLabel: "Example",
  waitDurationSeconds: 10,
  enabled: true,
  skipDates: [],
  createdAtMillis: 0,
  updatedAtMillis: 0,
};

describe("schedule preview", () => {
  it("marks multiple skip dates independently", () => {
    const task = {
      ...baseTask,
      skipDates: ["2026-06-26", "2026-06-28"],
    };

    const preview = buildSevenDayPreview(task, "2026-06-25");

    expect(preview.map((day) => [day.date, day.status])).toContainEqual(["2026-06-26", "SKIPPED"]);
    expect(preview.map((day) => [day.date, day.status])).toContainEqual(["2026-06-28", "SKIPPED"]);
    expect(preview.map((day) => [day.date, day.status])).toContainEqual(["2026-06-27", "SCHEDULED"]);
  });

  it("hides past skip dates from reminders", () => {
    const task = {
      ...baseTask,
      skipDates: ["2026-06-24", "2026-06-25", "2026-06-26"],
    };

    expect(futureSkipDates(task, "2026-06-25")).toEqual(["2026-06-25", "2026-06-26"]);
  });

  it("uses the actual trigger date when a random window crosses midnight", () => {
    const task = {
      ...baseTask,
      hour: 23,
      minute: 50,
      randomWindowMinutes: 40,
      skipDates: ["2026-06-26"],
    };

    const preview = buildSevenDayPreview(task, "2026-06-25");

    expect(preview.find((day) => day.date === "2026-06-26")?.status).toBe("SKIPPED");
  });

  it("returns disabled preview for disabled tasks", () => {
    const task = { ...baseTask, enabled: false };

    expect(buildSevenDayPreview(task, "2026-06-25").every((day) => day.status === "DISABLED")).toBe(true);
    expect(nextPreviewLabel(task, "2026-06-25")).toBe("已停用");
  });

  it("respects weekly selected days", () => {
    const task = {
      ...baseTask,
      repeatRule: "WEEKLY" as const,
      weeklyDays: [1, 3],
    };

    const preview = buildSevenDayPreview(task, "2026-06-22");

    expect(preview[0].status).toBe("SCHEDULED");
    expect(preview[1].status).toBe("NO_TASK");
    expect(preview[2].status).toBe("SCHEDULED");
  });

  it("applies demo Chinese holiday and makeup workday data", () => {
    expect(isDemoChineseWorkday("2026-10-01")).toBe(false);
    expect(isDemoChineseWorkday("2026-10-10")).toBe(true);
  });

  it("adds days using local calendar dates", () => {
    expect(addDays("2026-06-25", 2)).toBe("2026-06-27");
  });
});
