# Why the standalone alarm started working

On 14 October 2026, a legacy wearable reporting model `Pavlok-1`, hardware `v2.0.0` and firmware `2.4.99` was tested through a Mi 9T running Android 11. This report omits personal device identifiers; raw transcripts remain local.

## Observations

| Test | Connection at alarm time | Result |
| --- | --- | --- |
| One-record 10% vibration, 17:20 | Disconnected | No vibration noticed; audibility/perception was uncertain. |
| Original save sequence with 100% beep, 17:28 | Disconnected | No beep reported. |
| Connected 100% beep, 17:44 | Connected | Beep heard; spontaneous firmware state 2 at the target time. |
| Production one-record 100% beep, 17:49 | Disconnected | No beep. After the target time the firmware still reported armed, count 1/current slot 1 and an exact stored record. |
| Clean two-record 100% beep with original end-of-day record, 17:52 | Disconnected | Beep heard. |
| Final 0.1.5 production Save path, 100% beep, 18:02 | Disconnected | Beep heard; reconnection also returned firmware firing state 2. |

A further beep at 17:58 was reported. Its cause was not isolated; it could have been repetition/snooze from the earlier test. Stop/reset/clear completed before the final 18:02 test. Final cleanup confirmed stopped state 0 and an empty inventory.

## What changed

The early replacement saved only the user alarm. The original app also writes a daily 23:59:59 record, then activates the next stored slot. Restoring that record to the clean test made the disconnected alarm work. The final app additionally verifies both exact stored records before activation, waits after resetting RTC selection, verifies the selected slot, and checks armed state.

This is evidence for the complete working sequence on this device. It does **not** isolate whether the firmware needs those precise helper fields or simply a following stored record. No firmware binary was available to prove its internal handling. Calling the record a rollover helper is an inference from the old app's behavior.

The fix did not add a phone timer or keep Bluetooth connected in the background. The successful tests disconnected after saving, so the beep came from the wearable's onboard alarm. A successful storage readback and armed state alone had been insufficient in the one-record test.

## Further hardware work

Same-day disconnected beep firing is established. Overnight/daily rollover, next-day one-shot firing, midnight helper sound, snooze behavior and scheduled shock firing need separate physical tests. Version 0.2.0 preserves the 0.1.5 transaction and gives Alarm its own strength control.

The [protocol reference](protocol.md) lists the packets and callback sequence. The local artifact comparison used the old apps with REA/JADX and reviewed the public repositories linked there; modern-generation alarm implementations use different protocols.
