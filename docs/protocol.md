# Legacy Bluetooth protocol

This reference summarizes interoperability facts observed in old app artifacts and implemented independently in `LegacyProtocol.java`. It is for the legacy profile; generation-2/3 clock and actuator layouts are separate protocols.

## Characteristics

| Function | UUID |
| --- | --- |
| Alert service | `75dbc164-476f-44ec-8894-23fd0bb2f081` |
| Vibration | `7eca7033-fc71-4a58-8775-225e813a03fb` |
| Beep | `efd6fd9d-681b-4f19-9121-59900f57a401` |
| Shock | `6e9d7a34-ddc0-4b47-9df4-fc45d2891827` |
| RTC / stored alarms | `e6b17021-195d-4e84-b216-034caeb05498` |
| Local session | `b641abcd-62e0-4cea-983e-498550c27305` |

Device serial, firmware, hardware and battery use standard Device Information / Battery characteristics. The A4 branch sends a serial-derived local handshake: serial octet 4, `84 48`, serial octet 1 (zero-based octets). The inspected 2.4 branch uses direct commands without this handshake. The replacement requires a matching profile and recognized firmware before exposing controls.

## Intensity

Immediate vibration/beep map 10–100% to decimal bytes `55,75,95,115,135,155,175,195,215,255`. Immediate shock maps them to `32,64,85,112,128,144,160,176,192,255`. A single shock packet is `81 intensity 01 00` in hexadecimal. Stored alarms use different nibble mappings; immediate intensity bytes must not be substituted there.

## Immediate pulse timing

Vibration/beep use `80|count intensity on/20 off/20`: each time is an unsigned byte in 20 ms units. This app exposes 20–5100 ms on-time, a 640 ms off interval and 1–7 repeats. Immediate shock uses `81 intensity count 00`; its count is separate from the opcode and there is no verified adjustable shock-width field. The UI therefore exposes shock repeats rather than milliseconds.

Onboard records expose repetitions in their existing signal fields, including shock; they have no verified per-pulse millisecond field. See [pulse timing](pulse-timing.md) for observed sources, examples, defaults and runtime limits.

## RTC commands

All bytes in this table are hexadecimal. `BCD(26)` is `26`; weekday is Sunday=1 through Saturday=7.

| Action | Payload |
| --- | --- |
| Set local clock | `01 BCD(second) BCD(minute) BCD(hour) BCD(weekday) BCD(day) BCD(month) BCD(year-2000)` |
| Append stored alarm | `05` plus record fields below |
| Query state | `08` |
| Query stored count/current slot | `09` |
| Read stored slot | `06 index` (one-based binary index) |
| Clear stored list | `0A` |
| Reset active RTC selection | `07 02 02` |
| Activate stored slot | `02 BCD(index)` |
| Snooze | `07 01 01` |
| Stop | `07 02 01` |

RTC readback comes through notifications, rather than a characteristic read. On the tested device `09 count index` reports inventory; state response `08 00` means stopped, `08 01` armed, and `08 02` firing. Extra state bytes are preserved in diagnostics. The parser validates response lengths before interpreting them.

## Alarm record

The base record is 11 bytes; firmware at/after legacy version 2.4.86 uses 14. A4 firmware comparison uses its minor/patch fields, matching the original app's branch.

| Offset | Field |
| --- | --- |
| 0 | Append opcode `05` |
| 1 | Repeat mask: one-shot `80`, daily `7F` |
| 2–6 | BCD second, minute, hour, day, month |
| 7 | Selector: vibration `15`, beep `2A`, shock `3F` |
| 8–10 | Shock, vibration and beep configuration |
| 11–13 | Extended suffix used here: `A8 0A 00` |

Recurring weekday bits are Sunday `40`, Monday `20`, Tuesday `10`, Wednesday `08`, Thursday `04`, Friday `02`, Saturday `01`. The interface currently exposes once and every day. Midnight alarms retain the old app's second=4 convention.

Each signal configuration combines an intensity high nibble with a repetition count in the lowest three bits. Shock 10–100% nibbles are `2,3,4,5,6,7,8,9,A,C`; vibration/beep nibbles are `3,4,5,6,7,8,9,A,B,C`. Unselected signal fields retain app-equivalent defaults. This does not cause a command to an unselected actuator.

The original app additionally appends the daily end-of-day record:

```text
05 7F 59 59 23 BCD(day) BCD(month) 2A 61 71 71 [A8 0A 00]
```

The suffix is present for extended firmware. Its selector is beep, so the replacement does not invent a silent substitute. Its exact firmware role and midnight sound remain unproven; see [the hardware investigation](alarm-investigation.md).

## Verified save transaction

1. Enable RTC notifications.
2. Clear stored list; synchronize the local clock.
3. Append the user alarm and the original end-of-day record.
4. Query inventory and require exactly two records.
5. Read both slots and compare every stored field with the sent records.
6. Reset the active RTC selection, then wait 600 ms after the acknowledged reset.
7. Activate the next slot: user slot 1 for a future time today, end-of-day slot 2 after today's chosen time has passed.
8. Verify count/current slot and require armed state before reporting success.

Each write advances through a completion callback. Failure, disconnection or timeout aborts queued work and delayed activation. The app does not perform EEPROM record reads after activation; state/inventory diagnostics are separate. Clearing alarms stops, resets RTC selection, clears stored records, and removes the local saved-summary preference.

## Sources

The main packet sources were operator-supplied `Pavlok Shock Clock` 1.1 and `Pavlok Core Beta` 1.0 Android APKs, inspected locally using [REA](https://github.com/morluto/rea) and [JADX](https://github.com/skylot/jadx). Raw artifacts and decompiled source are not distributed here. Hardware confirmation is summarized in [alarm-investigation.md](alarm-investigation.md).

Public comparisons:

- [flagist0/pypavlok](https://github.com/flagist0/pypavlok): legacy stimulus UUIDs and immediate commands; no stored-alarm implementation in the inspected code.
- [ztrayl3/PyPav2](https://github.com/ztrayl3/PyPav2): generation-2 protocol, including a different clock characteristic; it cannot validate this RTC stream.
- [hairyfred/LibreShock](https://github.com/hairyfred/LibreShock): modern Pavlok 3 alarm work; a separate hardware/firmware branch.
- [McSwitchy/Pavlok-Nordic-HW-Test](https://github.com/McSwitchy/Pavlok-Nordic-HW-Test): public generation-2 firmware/RTC implementation, not proof of a legacy BLE clock-start command.
