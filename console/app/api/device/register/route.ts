import { NextResponse } from "next/server";
import { normalizeDeviceCode } from "@/lib/auth";
import { registerDevice } from "@/lib/server/remoteStore";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  const deviceCode = normalizeDeviceCode(typeof body?.deviceCode === "string" ? body.deviceCode : "");
  const secret = typeof body?.secret === "string" ? body.secret.trim() : "";

  if (!deviceCode || secret.length < 24) {
    return NextResponse.json({ ok: false, message: "设备注册参数无效" }, { status: 400 });
  }

  const result = registerDevice({
    deviceCode,
    secret,
    displayName: typeof body?.displayName === "string" ? body.displayName : undefined,
    appVersion: typeof body?.appVersion === "string" ? body.appVersion : undefined,
  });

  if (!result.ok) {
    return NextResponse.json({ ok: false, message: result.message }, { status: result.status });
  }

  return NextResponse.json({ ok: true, deviceCode });
}
