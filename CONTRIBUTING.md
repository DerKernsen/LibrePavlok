# Contributing

Open an issue with the wearable model, hardware version, firmware, Android version and the behavior you observed. Separate a successful Bluetooth write from actual physical alarm firing. For alarm reports, include the selected time, repeat option, signal, strength and whether the phone remained connected.

Device reports contain identifiers and command logs. Remove serial numbers, Bluetooth addresses and personal data before attaching a report to a public issue. A screenshot of the Device summary is usually enough to establish firmware and hardware versions.

## Code changes

Keep packet encoding in `LegacyProtocol`, GATT queueing in `BleController`, countdown state in `PlayCountdown`, and UI/session orchestration in `MainActivity`. `AppUi` contains the native palette and shared components.

Run the [logic tests](docs/development.md) for protocol or countdown changes. Build the APK for Android changes. Check layout on a phone or emulator for UI changes. Extend tests when a changed packet, race or state transition needs coverage.

Do not add proprietary APKs, decompiled code, raw Bluetooth logs, credentials, signing keys or downloaded tool distributions. Describe packet observations in your own words and cite the source or device evidence. Distinguish observed app behavior, verified hardware behavior and hypotheses.

Keep unsupported generations behind explicit profile detection, rather than applying legacy commands to an unfamiliar characteristic.
