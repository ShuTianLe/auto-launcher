# Auto Launcher Console

Remote-control console for Auto Launcher.

Tasks stay on the Android device. The console stores the latest device snapshot in SQLite and sends pending operations for the app to apply during polling.

## Local

```bash
npm install
npm run dev
```

Open `http://127.0.0.1:18130`.

Use the device code shown in the Android app settings page.

## Production

Set `SESSION_SECRET` and optionally `AUTO_LAUNCHER_DATA_DIR` in the runtime environment, then run:

```bash
npm run build
npm run start
```
