import { NextResponse } from "next/server";
import { normalizeDeviceCode } from "@/lib/auth";
import { authenticateDevice, savePollSnapshot } from "@/lib/server/remoteStore";
import type { PollSnapshot } from "@/lib/server/remoteStore";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const deviceCode = normalizeDeviceCode(request.headers.get("x-device-code") || "");
  const auth = request.headers.get("authorization") || "";
  const secret = auth.startsWith("Bearer ") ? auth.slice("Bearer ".length).trim() : "";

  if (!deviceCode || !secret || !authenticateDevice(deviceCode, secret)) {
    return NextResponse.json({ ok: false, message: "设备鉴权失败" }, { status: 401 });
  }

  const body = (await request.json().catch(() => null)) as PollSnapshot | null;
  if (!body?.device || !Array.isArray(body.tasks) || !Array.isArray(body.executionLogs)) {
    return NextResponse.json({ ok: false, message: "同步数据格式无效" }, { status: 400 });
  }

  const commands = savePollSnapshot(deviceCode, body);
  return NextResponse.json({
    ok: true,
    serverTimeMillis: Date.now(),
    nextPollSeconds: 30,
    commands,
  });
}
