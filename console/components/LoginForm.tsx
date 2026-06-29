"use client";

import { FormEvent, useState } from "react";
import { LockKeyhole, Loader2, ShieldCheck } from "lucide-react";

export function LoginForm() {
  const [deviceCode, setDeviceCode] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSubmitting(true);

    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ deviceCode }),
    });

    setSubmitting(false);
    if (!response.ok) {
      setError("设备码不正确，请检查后再试。");
      return;
    }

    window.location.href = "/";
  }

  return (
    <main className="login-page">
      <section className="login-panel" aria-label="设备码登录">
        <div className="login-brand">
          <div className="brand-mark" aria-hidden="true">
            <ShieldCheck size={20} />
          </div>
          <div>
            <h1>Auto Launcher 控制台</h1>
            <p>输入设备码后进入远程管理台</p>
          </div>
        </div>

        <form className="login-form" onSubmit={submit}>
          <label htmlFor="deviceCode">设备码</label>
          <div className="login-input-row">
            <LockKeyhole size={18} aria-hidden="true" />
            <input
              id="deviceCode"
              autoFocus
              autoCapitalize="characters"
              autoComplete="off"
              value={deviceCode}
              onChange={(event) => setDeviceCode(event.target.value)}
              placeholder="输入设备码"
            />
          </div>
          {error ? <p className="form-error">{error}</p> : null}
          <button className="primary-button full-width" type="submit" disabled={submitting || !deviceCode.trim()}>
            {submitting ? <Loader2 className="spin" size={16} /> : <ShieldCheck size={16} />}
            进入控制台
          </button>
        </form>

        <p className="login-footnote">设备码可在 Android App 的“权限与系统设置”页查看。</p>
      </section>
    </main>
  );
}
