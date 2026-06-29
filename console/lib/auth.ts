import crypto from "node:crypto";
import { hasRegisteredDevice } from "@/lib/server/remoteStore";

export const AUTH_COOKIE = "auto_launcher_console";
export const SESSION_MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

const fallbackSecret = "auto-launcher-console-demo-session-secret";
const fallbackDeviceCode = "AUTO-DEMO-19930630";

export function normalizeDeviceCode(value: string): string {
  return value.trim().toUpperCase().replace(/\s+/g, "");
}

export function shouldUseSecureCookie(request: Request): boolean {
  const forwardedProto = request.headers.get("x-forwarded-proto");
  if (forwardedProto) return forwardedProto.split(",")[0]?.trim() === "https";
  return new URL(request.url).protocol === "https:";
}

export function isKnownDeviceCode(deviceCode: string): boolean {
  const normalized = normalizeDeviceCode(deviceCode);
  if (hasRegisteredDevice(normalized)) return true;
  return process.env.NODE_ENV !== "production" && normalized === normalizeDeviceCode(process.env.DEVICE_CODE || fallbackDeviceCode);
}

export function createSessionToken(deviceCode: string, nowMillis = Date.now()): string {
  const issuedAt = nowMillis.toString();
  const expiresAt = (nowMillis + SESSION_MAX_AGE_SECONDS * 1000).toString();
  const encodedDeviceCode = Buffer.from(normalizeDeviceCode(deviceCode), "utf8").toString("base64url");
  const payload = `${issuedAt}.${expiresAt}.${encodedDeviceCode}`;
  return `${payload}.${sign(payload)}`;
}

export function isValidSessionToken(token: string | undefined): boolean {
  return getSessionDeviceCode(token) !== null;
}

export function getSessionDeviceCode(token: string | undefined): string | null {
  if (!token) return null;
  const parts = token.split(".");
  if (parts.length !== 4) return null;

  const [issuedAt, expiresAt, encodedDeviceCode, signature] = parts;
  if (!/^\d+$/.test(issuedAt) || !/^\d+$/.test(expiresAt)) return null;

  const payload = `${issuedAt}.${expiresAt}.${encodedDeviceCode}`;
  const expected = sign(payload);
  if (!safeEqual(signature, expected)) return null;

  if (Number(expiresAt) <= Date.now()) return null;
  return Buffer.from(encodedDeviceCode, "base64url").toString("utf8");
}

function sign(payload: string): string {
  const secret = process.env.SESSION_SECRET?.trim();
  if (!secret && process.env.NODE_ENV === "production") {
    throw new Error("SESSION_SECRET is required in production");
  }
  return crypto.createHmac("sha256", secret ?? fallbackSecret).update(payload).digest("base64url");
}

function safeEqual(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  return leftBuffer.length === rightBuffer.length && crypto.timingSafeEqual(leftBuffer, rightBuffer);
}
