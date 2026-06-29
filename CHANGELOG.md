# Changelog

## Unreleased

## v1.2.0

- Added real remote-console sync while keeping Android App as the task source of truth.
- Added App-generated device codes and device-secret authentication for App-to-console sync.
- Added SQLite-backed console storage for device snapshots and queued Web commands.
- Added 30-second Android polling for remote commands, with immediate result reporting after commands are applied.
- Added Web command flow for creating tasks, toggling tasks, adding skip dates, and removing skip dates.
- Added remote sync status and device code display in the Android permissions/settings page.
- Updated the console UI to show real App snapshots, command queue state, and App-sourced installed apps.

## v1.1.0

- Added per-task one-time skip dates, with support for multiple dates per task.
- Added a task schedule page for managing skip dates and previewing the next 7 days.
- Updated scheduling so skipped dates are checked against the actual trigger date.
- Added execution-time skip protection so stale alarms cannot launch a skipped task.
- Added schedule calculation unit tests for skip dates, cross-midnight windows, and preview states.

## v1.0.0

- Initial public release.
