package org.librepavlok.offline;

import java.util.Calendar;
import java.util.Locale;

/**
 * Independently written encoders for the legacy Shock Clock GATT profile.
 * Byte layouts were observed in the original Android apps; hardware verification
 * is still required. This class has no Bluetooth, network, or scheduling code.
 */
public final class LegacyProtocol {
    public static final String ALERT_SERVICE = "75dbc164-476f-44ec-8894-23fd0bb2f081";
    public static final String VIBRATION = "7eca7033-fc71-4a58-8775-225e813a03fb";
    public static final String BEEP = "efd6fd9d-681b-4f19-9121-59900f57a401";
    public static final String SHOCK = "6e9d7a34-ddc0-4b47-9df4-fc45d2891827";
    public static final String RTC = "e6b17021-195d-4e84-b216-034caeb05498";
    public static final String CONNECTION = "b641abcd-62e0-4cea-983e-498550c27305";
    public static final String SERIAL = "00002a25-0000-1000-8000-00805f9b34fb";
    public static final String FIRMWARE = "00002a26-0000-1000-8000-00805f9b34fb";
    public static final String HARDWARE = "00002a27-0000-1000-8000-00805f9b34fb";
    public static final String BATTERY = "00002a19-0000-1000-8000-00805f9b34fb";
    public static final String DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb";
    public static final String CCCD = "00002902-0000-1000-8000-00805f9b34fb";

    public static final int SUNDAY = 0x40;
    public static final int MONDAY = 0x20;
    public static final int TUESDAY = 0x10;
    public static final int WEDNESDAY = 0x08;
    public static final int THURSDAY = 0x04;
    public static final int FRIDAY = 0x02;
    public static final int SATURDAY = 0x01;
    public static final int EVERY_DAY = 0x7f;
    public static final int ONE_SHOT = 0x80;

    private static final int[] VIBE_BEEP_LEVELS = {55, 75, 95, 115, 135, 155, 175, 195, 215, 255};
    private static final int[] SHOCK_LEVELS = {32, 64, 85, 112, 128, 144, 160, 176, 192, 255};

    private LegacyProtocol() { }

    /** A4 is the original app's Shock Clock firmware family marker. */
    public static boolean isShockClockFirmware(String firmware) {
        return firmware != null && firmware.matches("(?i)A4\\.[0-9]{1,3}\\.[0-9]{1,3}");
    }

    /** Supported legacy GATT families; A4 alone uses the alarm-device handshake. */
    public static boolean isSupportedLegacyFirmware(String firmware) {
        try {
            firmwareParts(firmware);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static boolean hasExtendedAlarms(String firmware) {
        int[] version = firmwareParts(firmware);
        return version[0] > 4 || (version[0] == 4 && version[1] >= 86);
    }

    public static int vibrationBeepIntensity(int percent) {
        range("intensity percent", percent, 1, 100);
        return VIBE_BEEP_LEVELS[(percent - 1) / 10];
    }

    /** Mapping is identical in both inspected apps; the firmware gate avoids other profiles. */
    public static int shockIntensity(int percent, String firmware) {
        firmwareParts(firmware);
        range("shock percent", percent, 1, 100);
        return SHOCK_LEVELS[(percent - 1) / 10];
    }

    public static byte[] vibration(int percent, int count, int onMs, int offMs) {
        return pulse(percent, count, onMs, offMs);
    }

    public static byte[] beep(int percent, int count, int onMs, int offMs) {
        return pulse(percent, count, onMs, offMs);
    }

    /** Backward-compatible single shock. Shock pulse width is firmware controlled. */
    public static byte[] shock(int percent, String firmware) {
        return shock(percent,firmware,1);
    }
    /** One native burst, with no stored button assignment or phone-side retry. */
    public static byte[] shock(int percent,String firmware,int count) {
        range("shock count",count,1,7);
        return bytes(0x81,shockIntensity(percent,firmware),count,0);
    }

    /** Serial is the device information serial, not an assumed advertising address. */
    public static byte[] connectionHandshake(String serialMac) {
        if (serialMac == null || !serialMac.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) {
            throw new IllegalArgumentException("Serial must have six colon-separated hexadecimal octets");
        }
        String[] octets = serialMac.split(":");
        return bytes(Integer.parseInt(octets[4], 16), 0x84, 0x48, Integer.parseInt(octets[1], 16));
    }

    /** Encodes local wall time, including weekday Sunday=1 through Saturday=7. */
    public static byte[] clock(Calendar localTime) {
        Calendar time = checkedTime(localTime);
        return bytes(1, bcd(time.get(Calendar.SECOND)), bcd(time.get(Calendar.MINUTE)),
                bcd(time.get(Calendar.HOUR_OF_DAY)), bcd(time.get(Calendar.DAY_OF_WEEK)),
                bcd(time.get(Calendar.DAY_OF_MONTH)), bcd(time.get(Calendar.MONTH) + 1),
                bcd(time.get(Calendar.YEAR) - 2000));
    }

    public static byte[] vibrationAlarm(String firmware, int repeatMask, Calendar time, int percent, int count) {
        return alarm(firmware, repeatMask, time, percent, count, 0, 0, 0, false, 10, false, false);
    }

    public static byte[] beepAlarm(String firmware, int repeatMask, Calendar time, int percent, int count) {
        return alarm(firmware, repeatMask, time, 0, 0, percent, count, 0, false, 10, false, false);
    }

    /**
     * Appends one alarm record. Percent zero disables that stimulus, with count zero.
     * Active alarm strengths must be 10..100 in increments of ten. Alarm shock count
     * uses 1..7 repetitions. A firmware alarm may continue until dismissed; this method
     * does not establish its dismissal behavior. Vibration+beep without shock is
     * rejected because the original app has no verified selector for that pair.
     * Unselected configurations retain the original app's 50-percent/count-one defaults.
     */
    public static byte[] alarm(String firmware, int repeatMask, Calendar localDateTime,
            int vibrationPercent, int vibrationCount, int beepPercent, int beepCount,
            int shockPercent, boolean delayShock, int snoozeCount, boolean snoozeZap, boolean snoozeLock) {
        return alarm(firmware,repeatMask,localDateTime,vibrationPercent,vibrationCount,beepPercent,beepCount,
                shockPercent,shockPercent==0?0:1,delayShock,snoozeCount,snoozeZap,snoozeLock);
    }

    public static byte[] alarm(String firmware,int repeatMask,Calendar localDateTime,
            int vibrationPercent,int vibrationCount,int beepPercent,int beepCount,
            int shockPercent,int shockCount,boolean delayShock,int snoozeCount,boolean snoozeZap,boolean snoozeLock) {
        boolean extended = hasExtendedAlarms(firmware);
        Calendar time = checkedTime(localDateTime);
        if (repeatMask != ONE_SHOT) range("weekly repeat mask", repeatMask, 1, EVERY_DAY);
        int vibe = alarmIntensity(vibrationPercent, vibrationCount, false);
        int beep = alarmIntensity(beepPercent, beepCount, false);
        int zap = alarmIntensity(shockPercent, shockCount, true);
        boolean v = vibrationPercent != 0, b = beepPercent != 0, z = shockPercent != 0;
        int selector;
        if (v && b && z) selector = 0x39;
        else if (v && z) selector = 0x35;
        else if (b && z) selector = 0x3a;
        else if (z) selector = 0x3f;
        else if (v && !b) selector = 0x15;
        else if (b && !v) selector = 0x2a;
        else throw new IllegalArgumentException("Unsupported or empty alarm stimulus combination");
        if (delayShock && (!extended || !z)) {
            throw new IllegalArgumentException("Shock delay requires shock and extended alarm firmware");
        }
        if (snoozeZap && !z) throw new IllegalArgumentException("Snooze shock requires an enabled shock stimulus");
        if (snoozeZap && snoozeLock) throw new IllegalArgumentException("Snooze shock and snooze lock are exclusive");
        range("snooze count", snoozeCount, 1, 255);
        if (!extended && snoozeLock) throw new IllegalArgumentException("Snooze lock requires extended alarms");
        if (!extended && snoozeCount != 10) throw new IllegalArgumentException("Legacy alarm has no snooze-count field");
        if (delayShock) selector |= 0x80;
        if (!extended && snoozeZap) zap |= 0x08;
        byte[] result = new byte[extended ? 14 : 11];
        int[] fields = {5, repeatMask, bcd(time.get(Calendar.SECOND)), bcd(time.get(Calendar.MINUTE)),
                bcd(time.get(Calendar.HOUR_OF_DAY)), bcd(time.get(Calendar.DAY_OF_MONTH)),
                bcd(time.get(Calendar.MONTH) + 1), selector, zap, vibe, beep};
        for (int i = 0; i < fields.length; i++) result[i] = (byte) fields[i];
        if (extended) {
            result[11] = (byte) 0xa8;
            result[12] = (byte) snoozeCount;
            result[13] = (byte) (snoozeZap ? 0x40 : snoozeLock ? 0x20 : 0);
        }
        return result;
    }

    public static byte[] clearAlarms() { return bytes(0x0a); }
    public static byte[] clearRtc() { return bytes(7, 2, 2); }
    public static byte[] readAlarmStatus() { return bytes(8); }
    public static byte[] readAlarmInventory() { return bytes(9); }

    /** Exact end-of-day record used by the original apps' saved-list flow. */
    public static byte[] rolloverAlarm(String firmware, Calendar localTime) {
        Calendar end = checkedTime(localTime);
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        return beepAlarm(firmware, EVERY_DAY, end, 50, 1);
    }

    /** The original app selects the rollover slot when today's alarm time has passed. */
    public static int nextAlarmIndex(byte[] record, Calendar localTime) {
        if (record == null || (record.length != 11 && record.length != 14) || record[0] != 5)
            throw new IllegalArgumentException("Invalid alarm record");
        Calendar now = checkedTime(localTime);
        int alarmSeconds = fromBcd(record[4]) * 3600 + fromBcd(record[3]) * 60 + fromBcd(record[2]);
        int nowSeconds = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);
        return alarmSeconds > nowSeconds ? 1 : 2;
    }

    public static int alarmInventoryCount(byte[] notification) {
        return notification != null && notification.length >= 3 && notification[0] == 9 ? notification[1] & 0xff : -1;
    }

    public static int alarmInventoryIndex(byte[] notification) {
        return notification != null && notification.length >= 3 && notification[0] == 9 ? notification[2] & 0xff : -1;
    }

    /** Original app interprets state 1 as armed, 0 as stopped and 2 as firing. */
    public static int alarmState(byte[] notification) {
        if (notification == null || notification.length < 3 || notification[0] != 8) return -1;
        int state = notification[1] & 0xff;
        return state <= 2 ? state : -1;
    }

    /** Select uses BCD; the read-slot command uses binary. Slots are one-based. */
    public static byte[] selectAlarm(int index) {
        range("alarm index", index, 1, 99);
        return bytes(2, bcd(index));
    }

    public static byte[] readAlarm(int index) {
        range("alarm index", index, 1, 255);
        return bytes(6, index);
    }

    /**
     * Checks an alarm-slot notification against the record just written.
     * A match verifies stored bytes only, not clock accuracy or alarm firing.
     * Malformed or unrelated messages return false without throwing.
     */
    public static boolean matchesAlarmReadback(byte[] writtenRecord, int expectedIndex, byte[] notification) {
        if (writtenRecord == null || notification == null || expectedIndex < 1 || expectedIndex > 255) return false;
        if ((writtenRecord.length != 11 && writtenRecord.length != 14) || writtenRecord[0] != 5) return false;
        int expectedLength = writtenRecord.length + 1;
        if ((notification.length != expectedLength && notification.length != 16) || notification[0] != 6
                || (notification[1] & 0xff) != expectedIndex) return false;
        for (int i = 1; i < writtenRecord.length; i++) {
            if (writtenRecord[i] != notification[i + 1]) return false;
        }
        for (int i = expectedLength; i < notification.length; i++) if (notification[i] != 0) return false;
        return true;
    }

    public static String hex(byte[] payload) {
        if (payload == null) throw new IllegalArgumentException("Payload is null");
        StringBuilder result = new StringBuilder();
        for (byte value : payload) {
            if (result.length() != 0) result.append(' ');
            result.append(String.format(Locale.ROOT, "%02X", value & 0xff));
        }
        return result.toString();
    }

    private static byte[] pulse(int percent, int count, int onMs, int offMs) {
        range("pulse count", count, 1, 63);
        duration("on duration", onMs, 20);
        duration("off duration", offMs, 0);
        return bytes(0x80 | count, vibrationBeepIntensity(percent), onMs / 20, offMs / 20);
    }

    private static void duration(String label, int value, int min) {
        range(label, value, min, 5100);
        if (value % 20 != 0) throw new IllegalArgumentException(label + " must be a multiple of 20 milliseconds");
    }

    private static int alarmIntensity(int percent, int count, boolean shock) {
        if (percent == 0) {
            if (count != 0) throw new IllegalArgumentException("Disabled stimulus must have zero count");
            return shock ? 0x61 : 0x71;
        }
        range("alarm strength", percent, 10, 100);
        if (percent % 10 != 0) throw new IllegalArgumentException("Alarm strength must be a multiple of ten percent");
        range("alarm count", count, 1, 7);
        int nibble = percent == 100 ? 12 : percent / 10 + (shock ? 1 : 2);
        return (nibble << 4) | count;
    }

    private static Calendar checkedTime(Calendar source) {
        if (source == null) throw new IllegalArgumentException("Time is null");
        Calendar time = (Calendar) source.clone();
        time.setLenient(false);
        try { time.getTimeInMillis(); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid calendar date/time", ex); }
        if (time.get(Calendar.ERA) != 1) throw new IllegalArgumentException("Calendar era must be AD");
        range("year", time.get(Calendar.YEAR), 2000, 2099);
        return time;
    }

    private static int[] firmwareParts(String firmware) {
        if (firmware == null || !(isShockClockFirmware(firmware) || firmware.matches("2\\.4\\.[0-9]{1,3}"))) {
            throw new IllegalArgumentException("Unsupported firmware; legacy A4.x.y or 2.4.y required");
        }
        String[] parts = firmware.split("\\.");
        return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static int bcd(int value) {
        range("BCD value", value, 0, 99);
        return (value / 10 << 4) | (value % 10);
    }

    private static int fromBcd(byte value) {
        int hi = (value & 0xff) >> 4, lo = value & 15;
        if (hi > 9 || lo > 9) throw new IllegalArgumentException("Invalid BCD byte");
        return hi * 10 + lo;
    }

    private static void range(String label, int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException(label + " must be between " + min + " and " + max);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }
}
