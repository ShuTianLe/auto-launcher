# Changelog

## v1.1.0

- Added per-task one-time skip dates, with support for multiple dates per task.
- Added a task schedule page for managing skip dates and previewing the next 7 days.
- Updated scheduling so skipped dates are checked against the actual trigger date.
- Added execution-time skip protection so stale alarms cannot launch a skipped task.
- Added schedule calculation unit tests for skip dates, cross-midnight windows, and preview states.

## v1.0.0

- Initial public release.
