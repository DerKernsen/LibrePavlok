# Optional to-do deadlines

Version 0.3.0 adds **To-dos** as a fifth navigation tab. It starts paused. Existing Remote, Alarm and Play users do not need to enable it, grant its permissions, or keep a monitoring service running.

Version 0.3.1 adds per-task vibration/beep/shock selection, pulse duration for vibration/beep and repeat counts for all signals. Existing 0.3.0 tasks retain one shock at the original strength. Read [pulse duration and burst behavior](pulse-timing.md).

## Using the list

1. Open **To-dos → Add to-do** and enter a title.
2. Tap the date to choose a deadline date and time. Deadlines must be in the future.
3. Enter an advance reminder in minutes: `30` means 30 minutes before the deadline, `0` disables it. The maximum is 10080 minutes (one week). An advance reminder must also be in the future when saving.
4. Choose vibration, shock or beep if unfinished, strength from 10% to 100%, and 1–7 repetitions. Vibration/beep also offer 20–5100 ms per pulse. Shock width is fixed by the wearable. These settings belong to that task and do not change other features.
5. Review the title, deadline, reminder and full signal settings, then save.
6. Connect a supported wearable through Device, return to To-dos and enable monitoring. Android may ask for Bluetooth, notification and exact-alarm access. After granting access, tap Enable monitoring again and confirm background deadline signals, which can include repeated shocks.
7. Mark a task done before its deadline to cancel its pending reminder and deadline action. Delete removes the task. Edit lets you set a new future deadline and reopens the task after confirmation.

Reminders are phone notifications; they do not shock the wearer. Notification taps open To-dos. Monitoring shows a persistent notification with a **Pause monitoring** action. The same control is available inside the tab. Pausing retains the list. Adding tasks while paused does not turn on monitoring.

## Delivery and interruptions

To-do deadlines run on the **phone**, through Android alarms and a connected-device foreground service. They do not replace, write or clear the wearable's onboard wake-up alarm. The app keeps one shared Bluetooth connection and one serialized GATT queue for foreground controls and to-do monitoring. Monitoring can reconnect the last wearable; explicitly disconnecting in Device suspends automatic background reconnection until the user reconnects or enables monitoring again.

At an unfinished task's deadline, the monitor durably marks its action as handled before attempting one BLE burst command with the task's chosen signal, strength and timing. A duplicate broadcast, restart or failed write cannot repeat the command. Tasks due together are submitted one at a time; firmware controls execution after submission. The app reports transport completion separately from physical delivery, which cannot be proven by a Bluetooth write acknowledgement.

- The wearable must be powered on, nearby, connected, on supported legacy firmware and ready for commands. Bluetooth permission and the phone's Bluetooth switch must remain enabled.
- If disconnected or occupied by another foreground action at the deadline, the task records a missed deadline with no shock. Reconnecting does not replay it.
- Android scheduling has a maximum one-minute lateness allowance in this implementation. Older events are recorded as missed without a shock. This allowance does not retry a failed or disconnected command.
- Pause, reboot, clock edits and UI relaunch after monitoring stopped expire past deadlines without catch-up shocks. Future events are rescheduled on boot or app update when Android permits it. A force-stopped app cannot resume until opened again.
- Revoked exact-alarm access, storage errors or a rejected foreground-service start stop monitoring. Check the tab and enable it again after fixing permissions.
- Android idle-alarm quotas, vendor battery management, user service termination and Bluetooth failures can delay or prevent delivery. A foreground service and exact-alarm access improve scheduling; they do not guarantee it. On MIUI, check the app's background/autostart restrictions if monitoring is stopped by the system.
- Completing or deleting a task cancels pending work. A burst command that has already been submitted cannot be recalled.

Tasks are stored locally in separate private `todos` preferences. The app still has no Internet permission. Dates are saved as absolute timestamps; display follows the phone's timezone.

## Implementation

| Component | Responsibility |
| --- | --- |
| `TodoItem` | Validated task, reminder/deadline predicates, one-shot state and lateness boundary; pure Java. |
| `TodoStore` | Synchronous JSON persistence, default-disabled monitoring and stable display ordering. |
| `TodoPanel` | Task list, completion, editing/deletion, per-task strength and explicit enable/save confirmations. |
| `TodoScheduler` | One exact wakeup for the next pending event, cancellation and foreground-service lifecycle. |
| `TodoReceiver` | Alarm delivery, boot/app-update recovery, clock changes and permission broadcasts. |
| `TodoService` | Optional foreground monitoring, notifications, single-use dispatch and bounded wake lock during BLE submission. |
| `SharedBle` | Shared controller, cached connection/session state, listener lifecycle and foreground-action exclusion. |
| `TodoNotifications` | Reminder/monitor channels, direct tab links and pause action. |

All mutations and BLE dispatch run on the main thread. Durable event claims occur before external side effects. A crash between the claim and BLE submission can lose an action but cannot duplicate it. The service does not automatically retry actuator commands. It releases its listener and notification when monitoring stops or no pending tasks remain; an open activity retains its connection independently.

Android references: [exact alarms](https://developer.android.com/develop/background-work/services/alarms), [connected-device foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device), [background BLE](https://developer.android.com/develop/connectivity/bluetooth/ble/background).

## Validation

See [0.3.1 validation and release notes](release-0.3.1.md) for the executed checks and their limits. The UI and emulator suites do not intentionally connect to a wearable or submit an actuator command. On a phone, previously enabled monitoring remains governed by the user's saved tasks. Physical scheduled signal delivery requires separate wearer-supervised validation.
