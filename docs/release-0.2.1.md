# LibrePavlok 0.2.1

- Added a first-launch liability notice. Acknowledge it to continue or exit without initializing Bluetooth or controls.
- The acknowledgement is remembered locally; review the notice again under Device.
- Added the repository liability disclaimer and cleaned up unrelated documentation.
- Keeps all strength controls selectable through 100% and preserves the existing alarm transaction.

Install `LibrePavlok-debug.apk` on Android 6+. This update uses the existing signing key and preserves local settings. The first launch after updating shows the notice even on an existing installation.

The APK's SHA-256 is supplied in `LibrePavlok-debug.apk.sha256`.

Validation: 213 protocol checks, 21 countdown checks, 22 emulator UI checks, signed APK verification and phone layout inspection.
