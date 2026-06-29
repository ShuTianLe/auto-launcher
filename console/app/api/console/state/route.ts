import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { AUTH_COOKIE, getSessionDeviceCode } from "@/lib/auth";
import { getConsoleState } from "@/lib/server/remoteStore";

export const runtime = "nodejs";

export async function GET() {
  const token = (await cookies()).get(AUTH_COOKIE)?.value;
  const deviceCode = getSessionDeviceCode(token);
  if (!deviceCode) {
    return NextResponse.json({ ok: false, message: "未登录" }, { status: 401 });
  }

  return NextResponse.json({ ok: true, state: getConsoleState(deviceCode) });
}
