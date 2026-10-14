package org.librepavlok.offline;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.UUID;

/** Run with javac/java; no Android runtime, device, or network is needed. */
public final class LegacyProtocolTest {
    private static int checks;

    public static void main(String[] args) {
        UUID.fromString(LegacyProtocol.VIBRATION);
        UUID.fromString(LegacyProtocol.BEEP);
        UUID.fromString(LegacyProtocol.SHOCK);
        UUID.fromString(LegacyProtocol.RTC);
        equal("81 37 01 00", LegacyProtocol.vibration(1, 1, 20, 0));
        equal("BF FF FF FF", LegacyProtocol.beep(100, 63, 5100, 5100));
        equal("82 87 20 20", LegacyProtocol.vibration(50, 2, 640, 640));
        int[] vibration = {55,75,95,115,135,155,175,195,215,255};
        int[] shock = {32,64,85,112,128,144,160,176,192,255};
        for (int i = 0; i < 10; i++) {
            int lower = i * 10 + 1, upper = (i + 1) * 10;
            equalInt(vibration[i], LegacyProtocol.vibrationBeepIntensity(lower));
            equalInt(vibration[i], LegacyProtocol.vibrationBeepIntensity(upper));
            equalInt(shock[i], LegacyProtocol.shockIntensity(lower, "A4.4.85"));
            equalInt(shock[i], LegacyProtocol.shockIntensity(upper, "a4.4.86"));
        }
        equal("81 20 01 00", LegacyProtocol.shock(10, "A4.4.85"));
        equal("81 FF 01 00", LegacyProtocol.shock(100, "A4.4.86"));
        equal("EE 84 48 BB", LegacyProtocol.connectionHandshake("AA:bb:CC:DD:ee:FF"));
        equal("00 84 48 FF", LegacyProtocol.connectionHandshake("00:FF:00:00:00:00"));
        Calendar time = date(2026, 10, 14, 7, 30, 0);
        equal("05 7F 59 59 23 14 10 2A 61 71 71 A8 0A 00",LegacyProtocol.rolloverAlarm("2.4.99",time));
        equal("05 7F 59 59 23 14 10 2A 61 71 71",LegacyProtocol.rolloverAlarm("A4.4.85",time));
        equalInt(7,time.get(Calendar.HOUR_OF_DAY));
        byte[] scheduled=LegacyProtocol.beepAlarm("2.4.99",128,time,100,3);
        equalInt(1,LegacyProtocol.nextAlarmIndex(scheduled,date(2026,10,14,7,29,59)));
        equalInt(2,LegacyProtocol.nextAlarmIndex(scheduled,date(2026,10,14,7,30,0)));
        equalInt(2,LegacyProtocol.nextAlarmIndex(scheduled,date(2026,10,14,17,30,0)));
        equalInt(2,LegacyProtocol.alarmInventoryCount(new byte[]{9,2,1}));
        equalInt(1,LegacyProtocol.alarmInventoryIndex(new byte[]{9,2,1}));
        equalInt(255,LegacyProtocol.alarmInventoryCount(new byte[]{9,(byte)255,1}));
        equalInt(-1,LegacyProtocol.alarmInventoryCount(null));
        equalInt(-1,LegacyProtocol.alarmInventoryCount(new byte[]{9,2}));
        equalInt(-1,LegacyProtocol.alarmInventoryIndex(new byte[]{6,2,1}));
        equalInt(1, LegacyProtocol.alarmState(new byte[]{8,1,0,0}));
        equalInt(0, LegacyProtocol.alarmState(new byte[]{8,0,0,2}));
        equalInt(2, LegacyProtocol.alarmState(new byte[]{8,2,0,0}));
        equalInt(-1, LegacyProtocol.alarmState(null));
        equalInt(-1, LegacyProtocol.alarmState(new byte[]{8,1}));
        equalInt(-1, LegacyProtocol.alarmState(new byte[]{6,1,0}));
        equalInt(-1, LegacyProtocol.alarmState(new byte[]{8,(byte)255,0}));
        equal("01 00 30 07 04 14 10 26", LegacyProtocol.clock(time));
        equal("05 7F 00 30 07 14 10 15 61 35 71",
                LegacyProtocol.vibrationAlarm("A4.4.85", 127, time, 10, 5));
        equal("05 80 00 30 07 14 10 2A 61 71 C7 A8 0A 00",
                LegacyProtocol.beepAlarm("A4.4.86", 128, time, 100, 7));
        equal("05 20 00 30 07 14 10 B5 C1 C7 71 A8 03 40",
                LegacyProtocol.alarm("A4.5.0", 32, time, 100, 7, 0, 0, 100, true, 3, true, false));
        equal("05 01 00 30 07 14 10 3F 29 71 71",
                LegacyProtocol.alarm("A4.4.85", 1, time, 0, 0, 0, 0, 10, false, 10, true, false));
        verifyReadback(LegacyProtocol.vibrationAlarm("A4.4.85", 127, time, 10, 5), 12,
                raw(6,12,127,0,0x30,7,0x14,0x10,0x15,0x61,0x35,0x71));
        verifyReadback(LegacyProtocol.beepAlarm("A4.4.86", 128, time, 100, 7), 255,
                raw(6,255,128,0,0x30,7,0x14,0x10,0x2a,0x61,0x71,0xc7,0xa8,10,0));
        truth(!LegacyProtocol.matchesAlarmReadback(null, 1, raw(6)));
        truth(!LegacyProtocol.matchesAlarmReadback(raw(5), 1, null));
        truth(!LegacyProtocol.matchesAlarmReadback(raw(), 1, raw()));
        truth(!LegacyProtocol.matchesAlarmReadback(raw(5), 1, raw(6,1)));
        truth(!LegacyProtocol.matchesAlarmReadback(new byte[10], 1, new byte[11]));
        truth(!LegacyProtocol.matchesAlarmReadback(new byte[12], 1, new byte[13]));
        truth(LegacyProtocol.matchesAlarmReadback(
                LegacyProtocol.vibrationAlarm("2.4.99",128,date(2026,10,14,16,52,0),100,3),1,
                raw(6,1,0x80,0,0x52,0x16,0x14,0x10,0x15,0x61,0xc3,0x71,0xa8,10,0,0)));
        equal("0A", LegacyProtocol.clearAlarms());
        equal("07 02 02", LegacyProtocol.clearRtc());
        equal("08", LegacyProtocol.readAlarmStatus());
        equal("09", LegacyProtocol.readAlarmInventory());
        equal("02 12", LegacyProtocol.selectAlarm(12));
        equal("06 0C", LegacyProtocol.readAlarm(12));
        equal("01 59 59 23 05 31 12 99", LegacyProtocol.clock(date(2099, 12, 31, 23, 59, 59)));
        truth(LegacyProtocol.isShockClockFirmware("A4.4.86"));
        truth(!LegacyProtocol.isShockClockFirmware("2.4.86"));
        truth(LegacyProtocol.isSupportedLegacyFirmware("A4.4.99"));
        truth(LegacyProtocol.isSupportedLegacyFirmware("2.4.99"));
        truth(!LegacyProtocol.isSupportedLegacyFirmware("2.3.99"));
        truth(!LegacyProtocol.isSupportedLegacyFirmware("2.5.99"));
        truth(!LegacyProtocol.isSupportedLegacyFirmware(null));
        truth(!LegacyProtocol.hasExtendedAlarms("2.4.85"));
        truth(LegacyProtocol.hasExtendedAlarms("2.4.86"));
        truth(LegacyProtocol.hasExtendedAlarms("2.4.99"));
        equal("81 20 01 00", LegacyProtocol.shock(10, "2.4.99"));
        equal("81 FF 01 00", LegacyProtocol.shock(100, "2.4.86"));
        equal(LegacyProtocol.hex(LegacyProtocol.shock(50, "A4.4.99")), LegacyProtocol.shock(50, "2.4.99"));
        equal(LegacyProtocol.hex(LegacyProtocol.vibrationAlarm("A4.4.99", 127, time, 10, 5)),
                LegacyProtocol.vibrationAlarm("2.4.99", 127, time, 10, 5));
        equal("05 7F 00 30 07 14 10 15 61 35 71",
                LegacyProtocol.vibrationAlarm("2.4.85", 127, time, 10, 5));
        truth(!LegacyProtocol.hasExtendedAlarms("A4.4.85"));
        truth(LegacyProtocol.hasExtendedAlarms("A4.4.86"));
        truth(LegacyProtocol.hasExtendedAlarms("A4.5.0"));
        rejects(() -> LegacyProtocol.vibration(0, 1, 20, 20));
        rejects(() -> LegacyProtocol.beep(101, 1, 20, 20));
        rejects(() -> LegacyProtocol.beep(50, 0, 20, 20));
        rejects(() -> LegacyProtocol.beep(50, 64, 20, 20));
        rejects(() -> LegacyProtocol.beep(50, 1, 0, 20));
        rejects(() -> LegacyProtocol.beep(50, 1, 21, 20));
        rejects(() -> LegacyProtocol.beep(50, 1, 20, -20));
        rejects(() -> LegacyProtocol.beep(50, 1, 5120, 20));
        rejects(() -> LegacyProtocol.shock(0, "A4.4.86"));
        rejects(() -> LegacyProtocol.shock(101, "A4.4.86"));
        rejects(() -> LegacyProtocol.shock(10, "2.3.86"));
        rejects(() -> LegacyProtocol.shock(10, "2.5.86"));
        rejects(() -> LegacyProtocol.shock(10, "6.10.0"));
        rejects(() -> LegacyProtocol.shock(10, null));
        rejects(() -> LegacyProtocol.shock(10, "A4.4.86junk"));
        rejects(() -> LegacyProtocol.connectionHandshake("AA:BB:CC:DD:EE"));
        rejects(() -> LegacyProtocol.connectionHandshake("AA:BB:CC:DD:EE:GG"));
        rejects(() -> LegacyProtocol.connectionHandshake("AAA:BB:CC:DD:EE:FF"));
        rejects(() -> LegacyProtocol.connectionHandshake(null));
        rejects(() -> LegacyProtocol.clock(date(1999, 12, 31, 0, 0, 0)));
        rejects(() -> LegacyProtocol.clock(date(2100, 1, 1, 0, 0, 0)));
        rejects(() -> LegacyProtocol.clock(date(2026, 2, 30, 0, 0, 0)));
        rejects(() -> LegacyProtocol.clock(null));
        rejects(() -> LegacyProtocol.selectAlarm(0));
        rejects(() -> LegacyProtocol.selectAlarm(100));
        rejects(() -> LegacyProtocol.readAlarm(256));
        rejects(() -> LegacyProtocol.vibrationAlarm("2.3.86", 127, time, 10, 5));
        rejects(() -> LegacyProtocol.vibrationAlarm("2.5.86", 127, time, 10, 5));
        rejects(() -> LegacyProtocol.vibrationAlarm("A4.4.86", 0, time, 10, 5));
        rejects(() -> LegacyProtocol.vibrationAlarm("A4.4.86", 129, time, 10, 5));
        rejects(() -> LegacyProtocol.vibrationAlarm("A4.4.86", 127, time, 11, 5));
        rejects(() -> LegacyProtocol.vibrationAlarm("A4.4.86", 127, time, 10, 0));
        rejects(() -> LegacyProtocol.vibrationAlarm("A4.4.86", 127, time, 10, 8));
        rejects(() -> LegacyProtocol.alarm("A4.4.86", 127, time, 0, 1, 10, 1, 0, false, 10, false, false));
        rejects(() -> LegacyProtocol.alarm("A4.4.86", 127, time, 10, 1, 10, 1, 0, false, 10, false, false));
        rejects(() -> LegacyProtocol.alarm("A4.4.85", 127, time, 0, 0, 0, 0, 10, true, 10, false, false));
        rejects(() -> LegacyProtocol.alarm("A4.4.86", 127, time, 10, 1, 0, 0, 0, false, 10, true, false));
        rejects(() -> LegacyProtocol.alarm("A4.4.86", 127, time, 0, 0, 0, 0, 10, false, 10, true, true));
        rejects(() -> LegacyProtocol.alarm("A4.4.85", 127, time, 10, 1, 0, 0, 0, false, 10, false, true));
        System.out.println("LegacyProtocolTest: " + checks + " checks passed");
    }

    private static Calendar date(int year, int month, int day, int hour, int minute, int second) {
        Calendar value = new GregorianCalendar(TimeZone.getTimeZone("Europe/Berlin"));
        value.clear();
        value.setLenient(false);
        value.set(year, month - 1, day, hour, minute, second);
        return value;
    }

    private static void verifyReadback(byte[] record, int index, byte[] notification) {
        truth(LegacyProtocol.matchesAlarmReadback(record, index, notification));
        for (int i = 0; i < notification.length; i++) {
            byte[] changed = notification.clone();
            changed[i] ^= 1;
            truth(!LegacyProtocol.matchesAlarmReadback(record, index, changed));
        }
        for (int i = 0; i < record.length; i++) {
            byte[] changed = record.clone();
            changed[i] ^= 1;
            truth(!LegacyProtocol.matchesAlarmReadback(changed, index, notification));
        }
        truth(!LegacyProtocol.matchesAlarmReadback(record, index, java.util.Arrays.copyOf(notification, notification.length - 1)));
        byte[] padded = java.util.Arrays.copyOf(notification, 16);
        truth(LegacyProtocol.matchesAlarmReadback(record, index, padded));
        for (int i=notification.length; i<16; i++) {
            byte[] invalid=padded.clone(); invalid[i]=1;
            truth(!LegacyProtocol.matchesAlarmReadback(record,index,invalid));
        }
        truth(!LegacyProtocol.matchesAlarmReadback(record,index,java.util.Arrays.copyOf(notification,17)));
        truth(!LegacyProtocol.matchesAlarmReadback(record, 0, notification));
        truth(!LegacyProtocol.matchesAlarmReadback(record, 256, notification));
        truth(!LegacyProtocol.matchesAlarmReadback(record, index == 255 ? 254 : index + 1, notification));
    }

    private static byte[] raw(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }

    private static void equal(String expected, byte[] value) {
        checks++;
        String actual = LegacyProtocol.hex(value);
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void equalInt(int expected, int actual) {
        checks++;
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void truth(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Expected true");
    }

    private static void rejects(Runnable action) {
        checks++;
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }
}
