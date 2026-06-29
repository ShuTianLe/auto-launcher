import { NextResponse } from "next/server";
import {
  AUTH_COOKIE,
  SESSION_MAX_AGE_SECONDS,
  createSessionToken,
  isKnownDeviceCode,
  normalizeDeviceCode,
  shouldUseSecureCookie,
} from "@/lib/auth";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  const submitted = normalizeDeviceCode(
    typeof body?.deviceCode === "string" ? body.deviceCode : "",
  );

  if (!isKnownDeviceCode(submitted)) {
    return NextResponse.json(
      { ok: false, message: "设备码不存在，请先在 Android App 中打开远程控制。" },
      { status: 401 },
    );
  }

  const response = NextResponse.json({ ok: true });
  response.cookies.set({
    name: AUTH_COOKIE,
    value: createSessionToken(submitted),
    httpOnly: true,
    sameSite: "lax",
    secure: shouldUseSecureCookie(request),
    path: "/",
    maxAge: SESSION_MAX_AGE_SECONDS,
  });
  return response;
}
