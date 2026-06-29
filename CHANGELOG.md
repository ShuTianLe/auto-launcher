# Changelog

## Unreleased

## v1.2.1

- Fixed remote console charging status. The Android app now reports whether the device is connected to power.
- Remember the last successfully used device code on the console login page.
- Removed noisy explanatory copy from the console and release notes.

## v1.2.0

- Added the remote Web console.
- Added app-generated device codes and device-secret authentication.
- Added console storage for device snapshots and pending operations.
- Added Android polling for remote operations and result reporting.
- Added Web controls for creating tasks, toggling tasks, and managing skip dates.
- Added remote sync status and device code display in the Android permissions/settings page.

## v1.1.0

- Added per-task one-time skip dates, with support for multiple dates per task.
- Added a task schedule page for managing skip dates and previewing the next 7 days.
- Updated scheduling so skipped dates are checked against the actual trigger date.
- Added execution-time skip protection so stale alarms cannot launch a skipped task.
- Added schedule calculation unit tests for skip dates, cross-midnight windows, and preview states.

## v1.0.0

- Initial public release.
