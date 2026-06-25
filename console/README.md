# Auto Launcher Console

Remote-control console prototype for Auto Launcher.

The first version uses a fake device code and browser-local mock data. It does not connect to the Android app yet.

## Local

```bash
npm install
npm run dev
```

Open `http://127.0.0.1:18130`.

Default demo device code:

```text
AUTO-DEMO-19930630
```

## Production

Set `DEVICE_CODE` and `SESSION_SECRET` in the runtime environment, then run:

```bash
npm run build
npm run start
```
