# 0.3.1 — pulse timing preview

Published from `feature/todo-deadlines`, separate from `main`. APK version code: **10**. Existing supplied installations can update using the same local debug signing key.

## Behavior

- Remote has independent vibration/beep duration and repeat settings, plus shock repeat count. Direct-action buttons show vibration/beep milliseconds × repetitions; shock confirmation names the chosen count.
- Alarm has independent repetition counts for each signal, included in the confirmation, stored record, byte-for-byte verification and saved summary. Alarm-tool tests use the chosen count; snooze follows the saved onboard configuration.
- Play offers vibration, shock or beep with independent remembered settings. Its immutable countdown snapshot preserves the chosen strength, duration and repeats. Shock still requires unlock and wearer agreement.
- To-dos offer per-task vibration, shock or beep, with duration for vibration/beep and repeat count for all three. Existing 0.3.0 tasks migrate to one shock at their previous strength. Deadline claims and no-retry behavior remain in place.

Direct vibration/beep: **20–5100 ms per pulse**, in 20 ms steps, with 640 ms off intervals. All signals expose **1–7 repeats**. Shock pulse width and cadence are firmware controlled. Onboard alarms expose repetition count per cycle rather than per-pulse milliseconds. See [the timing guide](pulse-timing.md) for packet evidence, examples and limits.

## Validation — 2026-10-15

- **436 pure Java checks passed:** 213 protocol, 21 countdown, 36 to-do, 166 pulse-plan checks. New coverage includes every 1–7 alarm count for all three signals across the firmware branches, stored readback, long/short pulse encoding, invalid values, immutable Play snapshots and legacy task defaults.
- **39 UI checks passed** on the Android 10 emulator: independent per-signal timing, persistence across relaunch, explicit Remote/Alarm repeat confirmations, beep countdown settings, frozen running-countdown settings and saved task timing, plus existing controls/acknowledgement coverage.
- **15 background checks passed** on the Android 10 emulator: legacy JSON migration, retained vibration/beep task settings, reminders, disconnected/completed/stale deadlines, duplicate starts, background/idle scheduling, pause and clock-change handling.
- Mi 9T / Android 11: updated to 0.3.1 and traversed all five tabs with the non-mutating UI test. Existing phone preferences were preserved. The phone returned black screenshots, including after wake/dismiss attempts, so physical-device visual layout verification was not possible in this pass. Visual QA used emulator captures. No test actuator command was submitted; the separate test package was removed afterward.
- APK compiled and v1/v2/v3 signatures verified.

Final APK SHA-256:

```text
9b586ed5a33e2f90c1b84c3e8f360ba0e92ab8c55e8ebb7e83f58263ddf917e3
```

Physical duration, native shock burst cadence, repeated onboard alarm firing and physical deadline-signal delivery remain unverified. Android 12+ exact-alarm and Android 13+ notification-permission runtime coverage remains outstanding as described in [0.3.0 validation](release-0.3.0.md). Native bursts already submitted to the wearable cannot be recalled by cancelling a countdown or pausing monitoring afterward.
