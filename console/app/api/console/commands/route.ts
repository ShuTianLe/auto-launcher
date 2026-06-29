import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { AUTH_COOKIE, getSessionDeviceCode } from "@/lib/auth";
import { enqueueCommand } from "@/lib/server/remoteStore";
import type { RemoteCommandType } from "@/lib/types";

export const runtime = "nodejs";

const allowedTypes = new Set<RemoteCommandType>([
  "CREATE_TASK",
  "SET_TASK_ENABLED",
  "ADD_SKIP_DATES",
  "REMOVE_SKIP_DATE",
]);

export async function POST(request: Request) {
  const token = (await cookies()).get(AUTH_COOKIE)?.value;
  const deviceCode = getSessionDeviceCode(token);
  if (!deviceCode) {
    return NextResponse.json({ ok: false, message: "未登录" }, { status: 401 });
  }

  const body = await request.json().catch(() => null);
  const type = body?.type as RemoteCommandType;
  if (!allowedTypes.has(type) || typeof body?.payload !== "object" || body.payload === null) {
    return NextResponse.json({ ok: false, message: "命令格式无效" }, { status: 400 });
  }

  const command = enqueueCommand(deviceCode, type, body.payload);
  return NextResponse.json({ ok: true, command });
}
