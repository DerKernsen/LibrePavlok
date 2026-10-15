# Pulse duration and longer bursts

Version 0.3.1 adds timing controls throughout Remote, Alarm, Play and To-dos. Existing settings retain their previous behavior unless changed: direct vibration/beep stay at 640 ms and one pulse, shocks stay at one pulse, and onboard vibration/beep alarms stay at three pulses per cycle.

| Feature | Vibration and beep | Shock |
| --- | --- | --- |
| Remote | Independent duration and repeat count for each signal. Buttons show the selected milliseconds × repetitions. | Repeat count, included in the send confirmation. |
| Alarm | Repeat count per alarm cycle, stored independently for vibration/beep/shock. | Repeat count per alarm cycle, included in save confirmation and the verified summary. |
| Play | Vibration, shock or beep; independent remembered settings for each signal. Vibration/beep have duration and repeat count. | Repeat count; wearer agreement and unlock still required. |
| To-dos | Select vibration, shock or beep per task; save duration and repeat count with the task. | Repeat count saved per task and shown in its confirmation. |

## What duration means

Direct vibration and beep accept **20–5100 ms per pulse**, in **20 ms steps**. The repeat slider selects **1–7 pulses**. There is a 640 ms off interval between vibration/beep pulses. For example, three 2000 ms vibration pulses have 6000 ms of requested on-time, with two 640 ms gaps between them; physical execution has not been timed in hardware QA.

Shock pulse width and repetition cadence are firmware controlled. Longer shock bursts therefore use a **count of 1–7 shocks**, not a made-up duration in seconds. This does not make any individual electrical pulse wider. The original application's direct command places the count in byte 2 and keeps byte 3 zero.

The legacy **onboard alarm record has repetition counts, not per-pulse milliseconds**. Alarm controls expose **1–7 repetitions per alarm cycle** for the selected signal. Width, spacing, continued ringing and snooze behavior belong to the wearable. Alarm-tool tests use the selected alarm count, and Snooze uses the alarm already stored on the wearable. The protocol-required end-of-day helper record remains unchanged.

Each burst is submitted as one native BLE command or stored alarm record. The app does not emulate a long shock by scheduling a loop of phone-side commands, and it does not automatically retry failed bursts. Once submitted, the wearable runs the burst; leaving a screen or pausing monitoring cannot recall it. Play can be cancelled **before** the countdown finishes.

## Persistence and migration

Remote, Alarm and Play save timing independently for every signal. Play captures an immutable snapshot before starting the countdown, so later edits cannot change the chosen burst. Alarm saving captures the selected count and verifies the stored bytes, then records that count in its summary.

Each to-do stores its selected signal, duration and repetition count. Tasks created by 0.3.0 migrate to **one shock at their existing strength**, without increasing repetitions or altering deadlines. Completed/claimed tasks remain completed/claimed. Phone advance reminders remain ordinary notifications and do not trigger wearable pulses. To-do monitoring remains optional and paused by default.

## Protocol evidence and limits

The packet changes follow static inspection of the operator-supplied Shock Clock 1.1 APK, SHA-256 `7280a3036469da8478a58ae1fcf6532dc49bb92482c3e48247001936c528546a`:

- `BluetoothLeService.takeDeviceAction`, REA evidence `ev_548cee1232965858795bfb63969d2477203427bef23f98b4cfa406fa9a927119`: vibration/beep encode on/off milliseconds divided by 20; immediate shock uses a separate count byte. The retained local `direct-actions.json` is the source record.
- `Alarm.intForByte`, REA evidence `ev_5b3947404478d109b26d3b989af54a97d1d3f052fbfa99b9f431fa49fe15c164`: signal counts 1–7 occupy the low three bits, including shock at record offset 8. The independent encoder preserves intensity and extended-alarm flags.

These are static observations, not proof of firmware timing or physical delivery. The app deliberately uses a common 1–7 repeat range for all signals. No proprietary APK, decompiled source, device identifier or raw diagnostic log is published. See [0.3.1 validation](release-0.3.1.md).
