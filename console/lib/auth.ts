import crypto from "node:crypto";

export const AUTH_COOKIE = "auto_launcher_console";
export const SESSION_MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

const fallbackSecret = "auto-launcher-console-demo-session-secret";
const fallbackDeviceCode = "AUTO-DEMO-19930630";

export function getExpectedDeviceCode(): string {
  return process.env.DEVICE_CODE?.trim() || fallbackDeviceCode;
}

export function normalizeDeviceCode(value: string): string {
  return value.trim().toUpperCase().replace(/\s+/g, "");
}

export function shouldUseSecureCookie(request: Request): boolean {
  const forwardedProto = request.headers.get("x-forwarded-proto");
  if (forwardedProto) return forwardedProto.split(",")[0]?.trim() === "https";
  return new URL(request.url).protocol === "https:";
}

export function createSessionToken(nowMillis = Date.now()): string {
  const issuedAt = nowMillis.toString();
  const expiresAt = (nowMillis + SESSION_MAX_AGE_SECONDS * 1000).toString();
  const payload = `${issuedAt}.${expiresAt}`;
  return `${payload}.${sign(payload)}`;
}

export function isValidSessionToken(token: string | undefined): boolean {
  if (!token) return false;
  const parts = token.split(".");
  if (parts.length !== 3) return false;

  const [issuedAt, expiresAt, signature] = parts;
  if (!/^\d+$/.test(issuedAt) || !/^\d+$/.test(expiresAt)) return false;

  const payload = `${issuedAt}.${expiresAt}`;
  const expected = sign(payload);
  if (!safeEqual(signature, expected)) return false;

  return Number(expiresAt) > Date.now();
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
