# Changelog

## 0.3.1 — 2026-10-15 (pulse timing preview)

- Added independent vibration/beep pulse durations (20–5100 ms, in 20 ms steps) and 1–7 repeat controls throughout Remote, Play and To-dos.
- Added shock repeat counts for longer firmware-managed bursts, with explicit counts in confirmations; shock pulse width remains fixed by the device.
- Added per-signal onboard alarm counts (1–7 per cycle), verified readback and saved-summary counts; alarm tests and snooze retain the stored protocol behavior.
- Added beep to Play and selectable vibration/beep/shock to To-dos, with immutable countdown snapshots and backward-compatible task migration.
- Added packet, boundary, settings persistence, confirmation and task migration coverage. Documented protocol limits in `docs/pulse-timing.md`.

## 0.3.0 — 2026-10-15 (to-do preview)

- Added an optional, default-paused To-dos tab with local tasks, individual date/time deadlines and configurable advance phone reminders.
- Added a confirmed, independently chosen 10–100% single shock for each unfinished task; completion and deletion cancel pending events.
- Added connected-device foreground monitoring with a persistent pause action, exact phone alarms and recovery for future events after reboot/update.
- Shared the Bluetooth connection with existing controls; preserved the separate wearable alarm workflow.
- Claimed events durably before dispatch, recorded missed/failed outcomes and prevented automatic retries or catch-up shocks after interruptions.
- Added 36 pure Java to-do checks, task UI coverage and emulator-only monitoring integration tests.
- Documented usage, permissions, delivery limits and validation in `docs/todos.md` and `docs/release-0.3.0.md`.

## 0.2.1 — 2026-10-14

- Added a first-launch liability notice with explicit acknowledgement or exit before Bluetooth or controls initialize.
- Saved acknowledgement locally and made the notice available from Device.
- Added a repository disclaimer covering warranties, injury, damage and loss to the maximum extent permitted by law, while preserving mandatory legal rights.
- Cleaned up unrelated documentation.

## 0.2.0 — 2026-10-14

- Replaced the long utility screen with Remote, Alarm, Play and Device tabs, shared connection status, native outline icons and a warm green/ivory palette.
- Added separate remembered Remote, Alarm and Play strength controls, all selectable through 100%, plus 10/50/100% Remote presets.
- Added nap shortcuts, an editable offline alarm draft and the last verified save summary.
- Kept Save, Play Start and Cancel visible above navigation while content scrolls.
- Added a fixed/random countdown for one vibration or shock, with explicit start, cancellation and wearer agreement for shock. No additional intensity cap.
- Added one-tap reconnect and automatic local session initialization for the supported legacy firmware branch.
- Moved diagnostics and alarm tests behind secondary controls; shortened confirmations and removed playful slogans.
- Prevented automatic system color inversion from changing the custom palette; inspected on Xiaomi Mi 9T and emulator.
- Added 21 countdown checks and 12 UI checks, including full-strength preservation and cancelling before dispatch.

## 0.1.5 — 2026-10-14

- Restored the original app's end-of-day record to the onboard alarm save sequence.
- Verified record count, both exact records, selected slot and armed state before reporting a saved alarm.
- Preserved callback sequencing and delayed RTC activation; cancelled interrupted transactions.
- Confirmed same-day 100% beep firing with the app disconnected, followed by firmware firing state and test-record cleanup.
- Added an audible three-minute test and read-only alarm state/inventory diagnostics.
