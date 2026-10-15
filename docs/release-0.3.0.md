# 0.3.0 — optional to-do deadline preview

This preview is developed on `feature/todo-deadlines`, separate from `main`. The APK uses version code 9 and the existing local debug signing key, so it can update the supplied 0.2.1 installation without clearing preferences.

## Changes

- Optional fifth To-dos tab, paused by default, with locally persisted tasks.
- Date/time deadline per task, configurable advance phone reminder, and independent 10–100% shock strength.
- Save and enable confirmations; completion, editing, deletion and persistent-notification pause action.
- Phone exact alarms and connected-device foreground monitoring; shared GATT connection with existing controls.
- Durable single-use event claims, explicit missed/failed outcomes, no automatic shock retries and no catch-up shocks after interruption.
- Existing wearable wake-up alarm remains a separate feature.

Read [the user guide and delivery limits](todos.md) before enabling monitoring.

## Validation

Validation date: 2026-10-15.

- Pure Java: 213 protocol checks, 21 Play countdown checks, 36 to-do checks.
- Android 10 emulator (API 29): all **28 UI checks passed**, covering first-launch acknowledgement, five navigation tabs, to-do review/save/completion/persistence and independent 100% strength, plus existing alarm/Play behavior.
- Android 10 emulator: all **12 monitoring integration checks passed**, covering advance reminder, disconnected/stale/completed deadlines, duplicate service starts, a background deadline while device idle is requested, notification pause and clock-change recovery. No Bluetooth address was selected.
- Mi 9T / Android 11: upgraded the existing installation to 0.3.0 (code 9), opened all five tabs with the non-mutating UI screenshot mode, and visually inspected To-dos and bottom navigation. Existing settings and acknowledgement were preserved. This is visual device QA, not the 28-check emulator suite.
- APK: Java/DEX compilation and v1/v2/v3 signature verification.

The installed Android 14 emulator profile could not start app instrumentation because its user storage was credential-locked. An isolated replacement data image did not reach an online ADB state. Therefore **Android 12+ exact-alarm access and Android 13+ notification-permission flows have not been runtime-validated** in this preview. The implementation targets API 35 and follows the documented API guards, but this does not replace device testing.

Final APK SHA-256:

```text
704a859afc7b2a8384ca4445b83fb93e57fddc0aa179968fd7d10c2cf5840625
```

Physical scheduled shock delivery is not claimed as verified. UI QA does not issue wearable commands. Android/vendor background restrictions and BLE failures can prevent deadline execution; the app displays missed/unknown outcomes rather than retrying a shock.
