# Changelog

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
