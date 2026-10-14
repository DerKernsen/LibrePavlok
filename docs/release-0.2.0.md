# LibrePavlok 0.2.0

Offline Android companion for the original Shock Clock / Pavlok-1 legacy Bluetooth profile.

- New Remote, Alarm, Play and Device screens with clear navigation and persistent connection status.
- Independent Remote, Alarm and Play strengths, each selectable through 100%; no extra Play intensity cap.
- Nap shortcuts, remembered alarm drafts, last verified save, and fixed Save/Start/Cancel actions.
- Delayed vibration/shock Play mode with fixed or random countdown, confirmation and cancellation.
- One-tap reconnect, battery/firmware details, and local diagnostic export.
- Preserves the two-record alarm transaction that fired 100% beep with the phone disconnected on tested firmware 2.4.99.

Install `LibrePavlok-debug.apk` on Android 6+. The APK uses the same local debug signing key as the previous phone build. Source builds signed with another key require a fresh installation.

Validation: 213 protocol checks, 21 countdown checks, 12 emulator UI checks, APK signature verification, and layout inspection on Mi 9T / Android 11. Other Pavlok generations are not implemented in this release. Overnight rollover and the end-of-day record's midnight behavior remain separate hardware work; details are in the repository's alarm investigation.

The APK's SHA-256 is supplied in `LibrePavlok-debug.apk.sha256`.
