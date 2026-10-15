# Development and validation

## Toolchain

- Windows PowerShell 5 or newer
- Android SDK: platform `android-35`, build-tools `35.0.0`, platform-tools for device tests
- Android Studio JBR / JDK 21 for the supplied scripts

The standalone builder compiles Java, packages resources with AAPT, converts classes with D8, aligns the APK and signs it with a local key. It downloads no Gradle dependencies. `android/` is also an Android Studio project using Android Gradle Plugin 8.9.2; there is no bundled Gradle wrapper.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-protocol.ps1 -Jdk 'C:\path\to\jdk'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1 -Sdk 'C:\path\to\sdk' -Jdk 'C:\path\to\jdk'
```

Output: `dist/LibrePavlok-debug.apk`. The builder creates `android/build/standalone/debug.keystore` on the first run and reuses it for updates. Keep your key if your builds should update an existing installation. A different key requires uninstalling the previous build, which removes its local preferences. The repository excludes the signing key.

## Architecture

Version 0.3.0 adds the optional to-do monitor and one shared BLE controller for activity/service ownership. See [to-do architecture and behavior](todos.md#implementation). Monitoring remains disabled until explicitly enabled in its own tab.

| File | Responsibility |
| --- | --- |
| `LegacyProtocol.java` | UUIDs, version detection, intensity mapping, RTC/record encoding and notification validation. No Android dependency. |
| `BleController.java` | Scan/connect, characteristic discovery, device information and serialized GATT queue. |
| `PlayCountdown.java` | Single-use countdown, strength preservation, cancellation and current-state checks. No Bluetooth calls. |
| `MainActivity.java` | Screens, preferences, session lifecycle, command dispatch and verified alarm transaction. |
| `AppDisclaimer.java` | Offline notice text and acknowledgement version. MainActivity gates initialization until acknowledgement. |
| `AppUi.java` | Palette, cards, buttons, typography and Canvas icons. |

All three strength preferences persist independently. Saving captures the alarm settings, then stores the verified summary only after the wearable reports armed. Competing commands are disabled during saving. Failure or interruption cancels queued work and delayed activation. Actuator commands are never automatically retried after reconnecting.

## Logic tests

The current script also runs `TodoItemTest`: 36 checks for reminder/deadline boundaries, completion, one-shot claims, lateness, independent tasks and input validation. The existing 213 protocol and 21 countdown checks remain included.

`scripts/test-protocol.ps1` runs 213 protocol checks and 21 countdown checks. Countdown checks cover 100% shock preservation, one-use dispatch, cancellation, disconnection, locked controls, revoked agreement and conflicting work.

The pure Java tests also run on a non-Windows JDK:

```sh
mkdir -p android/build/protocol-tests
javac -encoding UTF-8 -d android/build/protocol-tests \
  android/app/src/main/java/org/librepavlok/offline/LegacyProtocol.java \
  android/app/src/main/java/org/librepavlok/offline/PlayCountdown.java \
  android/tests/org/librepavlok/offline/LegacyProtocolTest.java \
  android/tests/org/librepavlok/offline/PlayCountdownTest.java
java -ea -cp android/build/protocol-tests org.librepavlok.offline.LegacyProtocolTest
java -ea -cp android/build/protocol-tests org.librepavlok.offline.PlayCountdownTest
```

## UI instrumentation

The 0.3.0 smoke suite runs 28 checks and screenshots all five tabs. It also covers a confirmed 100% to-do, completion and persistence without enabling monitoring. Use smoke mode only on an emulator: it resets draft and to-do preferences.

`TodoMonitorTest` is emulator-only. It uses no remembered Bluetooth address and checks phone reminders/deadlines without actuator writes. For Android 12+ grant exact-alarm access; Android 13+ also needs notification permission. For example, on an Android 14 emulator:

```powershell
adb -s emulator-5556 shell pm grant org.librepavlok.offline android.permission.BLUETOOTH_CONNECT
adb -s emulator-5556 shell pm grant org.librepavlok.offline android.permission.POST_NOTIFICATIONS
adb -s emulator-5556 shell appops set org.librepavlok.offline SCHEDULE_EXACT_ALARM allow
adb -s emulator-5556 shell am instrument -w org.librepavlok.offline.tests/org.librepavlok.offline.tests.TodoMonitorTest
```

The monitoring suite briefly forces emulator idle mode for a background deadline, then restores it and clears its task fixture. It checks disconnected/stale/completed tasks, duplicate starts, pause and clock edits. On a physical phone omit smoke mode: the UI suite preserves preferences and captures the five tabs without connecting to Bluetooth. It does not validate physical shock delivery.

Build the companion first, then the separate test package:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-device-test.ps1
adb -s emulator-5554 install -r dist/LibrePavlok-debug.apk
adb -s emulator-5554 install -r android/build/device-tests/device-tests.apk
adb -s emulator-5554 shell am instrument -w -e mode smoke org.librepavlok.offline.tests/org.librepavlok.offline.tests.UiSmokeTest
```

`mode smoke` resets the target's draft preferences and notice acknowledgement, and simulates UI readiness without connecting the GATT controller. It checks that Bluetooth and controls stay uninitialized until acknowledgement, Exit does not acknowledge, acknowledgement persists across relaunch, and the existing independent-strength/countdown behavior works. Use this mode on an emulator. Omitting `-e mode smoke` preserves phone preferences and screenshots the notice when it is pending, or the four tabs otherwise. Both modes send no wearable commands.

Screenshots: `/sdcard/Android/data/org.librepavlok.offline/files/ui-remote.png` and the corresponding `alarm`, `play`, and `device` names. Remove the test package afterward:

```sh
adb uninstall org.librepavlok.offline.tests
```

Version 0.2.1 also writes `ui-disclaimer.png` when the first-launch notice is pending. The UI suite runs 22 checks, including ten checks for the acknowledgement lifecycle. The phone screenshot mode never acknowledges a pending notice on the user's behalf.

## 0.2.1 validation

- 213 protocol, 21 countdown and 22 UI checks passed.
- Exit leaves the notice unacknowledged; a subsequent launch shows it again.
- No Bluetooth controller or normal controls initialize before explicit acknowledgement.
- Acknowledgement persists and skips the notice on relaunch. Full-strength countdown cancellation and independent preferences continue to pass.
- APK compilation/signature verification and first-launch screen inspection on emulator and Mi 9T / Android 11.

## Hardware instrumentation

`AlarmDeviceTest` is a separate, explicitly invoked research helper. Acknowledge the first-launch notice in the app before running it. It requires the expected serial and firmware `2.4.99`; optional `address` selects a known wearable. Default `inspect` connects and queries state/inventory. Other actions replace alarms, run audible tests or clear them. They are separate from UI QA.

Example of the production Save path, setting 100% beep three minutes ahead and disconnecting:

```sh
adb shell am instrument -w -e serial YOUR_DEVICE_SERIAL -e address YOUR_BLUETOOTH_ADDRESS -e action beep_ui_disconnected org.librepavlok.offline.tests/org.librepavlok.offline.tests.AlarmDeviceTest
```

After observing the result, use `-e action clear` with the same parameters to stop/reset/clear test records and query the empty inventory. Instrumentation output contains device identifiers; retain locally or redact before sharing.

## 0.2.0 validation

- 213 protocol and 21 countdown checks passed.
- APK compiled and v1/v2/v3 signatures verified.
- 12 emulator UI checks passed, including preservation/cancellation of a 100% Play shock plan.
- All four screens inspected on emulator and Mi 9T / Android 11. Bottom actions remain accessible while scrolling; automatic color inversion is disabled for the custom light palette.
- Manifest has no Internet permission.
- Same-day disconnected alarm firing was established on firmware 2.4.99 in 0.1.5; 0.2.0 preserves that transaction. No shocks were emitted during redesign QA.
