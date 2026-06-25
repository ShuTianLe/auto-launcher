import { createDemoState, storageVersion } from "@/lib/demoData";
import { todayString } from "@/lib/schedule";
import type { CommandLog, ConsoleState, Task } from "@/lib/types";

export const stateStorageKey = "auto-launcher-console-state";

export function loadConsoleState(): ConsoleState {
  if (typeof window === "undefined") return createDemoState();

  const raw = window.localStorage.getItem(stateStorageKey);
  if (!raw) {
    const seeded = createDemoState();
    saveConsoleState(seeded);
    return seeded;
  }

  const parsed = safeParse(raw);
  if (!parsed || parsed.schemaVersion !== storageVersion || !Array.isArray(parsed.tasks)) {
    const seeded = createDemoState();
    saveConsoleState(seeded);
    return seeded;
  }

  const normalized = normalizeState(parsed);
  saveConsoleState(normalized);
  return normalized;
}

export function saveConsoleState(state: ConsoleState): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(stateStorageKey, JSON.stringify(state));
}

export function resetConsoleState(): ConsoleState {
  const state = createDemoState();
  saveConsoleState(state);
  return state;
}

export function appendCommandLog(
  state: ConsoleState,
  task: Task | null,
  action: string,
  detail: string,
): ConsoleState {
  const log: CommandLog = {
    id: `cmd-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    taskId: task?.id ?? null,
    taskName: task?.name ?? "设备",
    action,
    detail,
    createdAtMillis: Date.now(),
  };

  return {
    ...state,
    commandLogs: [log, ...state.commandLogs].slice(0, 120),
    updatedAtMillis: Date.now(),
    device: {
      ...state.device,
      lastSyncAtMillis: Date.now(),
    },
  };
}

function normalizeState(state: ConsoleState): ConsoleState {
  const today = todayString();
  return {
    ...state,
    tasks: state.tasks.map((task) => ({
      ...task,
      skipDates: Array.from(new Set(task.skipDates.filter((date) => date >= today))).sort(),
    })),
  };
}

function safeParse(value: string): ConsoleState | null {
  try {
    return JSON.parse(value) as ConsoleState;
  } catch {
    return null;
  }
}
