package org.librepavlok.offline;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Local, explicit BLE operations only. A GATT success never proves a physical action. */
public final class BleController {
    public interface Listener {
        void onLog(String text);
        void onDevice(BluetoothDevice device, int rssi);
        void onState(String state, boolean ready);
        void onValue(UUID characteristic, byte[] value);
        default void onWriteComplete(UUID characteristic, String label, boolean success) { }
    }

    private static UUID uuid(String value) { return UUID.fromString(value); }
    private static UUID standard(String value) {
        return uuid("0000" + value + "-0000-1000-8000-00805f9b34fb");
    }
    private static final UUID VIBRATION = uuid("7eca7033-fc71-4a58-8775-225e813a03fb");
    private static final UUID BEEP = uuid("efd6fd9d-681b-4f19-9121-59900f57a401");
    private static final UUID SHOCK = uuid("6e9d7a34-ddc0-4b47-9df4-fc45d2891827");
    private static final UUID RTC = uuid("e6b17021-195d-4e84-b216-034caeb05498");
    private static final UUID CONNECTION = uuid("b641abcd-62e0-4cea-983e-498550c27305");
    private static final UUID HAND_SETTINGS = uuid("b641c9b0-62e0-4cea-983e-498550c27305");
    private static final UUID LED = uuid("0102a282-7f71-4d53-85d4-c5f039491de5");
    private static final UUID BATTERY = standard("2a19");
    private static final UUID FIRMWARE = standard("2a26");
    private static final UUID HARDWARE = standard("2a27");
    private static final UUID SERIAL = standard("2a25");
    private static final UUID MODEL = standard("2a24");
    private static final UUID MANUFACTURER = standard("2a29");
    private static final UUID CCCD = standard("2902");
    private static final UUID PAVLOK_S_SERVICE = uuid("156e0000-a300-4fea-897b-86f698d74461");
    private static final Set<UUID> ALLOWED_WRITES = new HashSet<>(Arrays.asList(
            VIBRATION, BEEP, SHOCK, RTC, CONNECTION, LED));
    private static final Set<UUID> ADVERTISED_SERVICES = new HashSet<>(Arrays.asList(
            uuid("f211b484-50cd-4dbd-97ef-baf1cba7b36a"),
            uuid("75dbc164-476f-44ec-8894-23fd0bb2f081"),
            uuid("f589ace3-962c-43cd-ad55-e62d2fd891a7"),
            uuid("156e0000-a300-4fea-897b-86f698d74461")));
    private static final int READ = 1, WRITE = 2, NOTIFY = 3;
    private static final long OP_TIMEOUT_MS = 10000;
    private static final long CONNECT_TIMEOUT_MS = 20000;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;
    private final ArrayDeque<Operation> queue = new ArrayDeque<>();
    private final Map<UUID, BluetoothGattCharacteristic> characteristics = new HashMap<>();
    private final Map<UUID, byte[]> values = new HashMap<>();
    private final Set<UUID> duplicates = new HashSet<>();
    private final ArrayDeque<String> logLines = new ArrayDeque<>();
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private BluetoothGatt gatt;
    private Operation active;
    private boolean connected, discovered, ready, closed, newerServiceFound;
    private String serial = "", firmware = "", inventory = "No service inventory yet.\n";
    private String deviceIdentity = "No device connected";
    private Runnable operationTimer, connectionTimer, scanTimer;

    public BleController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        BluetoothManager manager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
    }

    private void onMain(Runnable work) {
        if (Looper.myLooper() == Looper.getMainLooper()) work.run(); else main.post(work);
    }
    private void log(String text) {
        if (logLines.size() >= 200) logLines.removeFirst();
        logLines.addLast(text);
        listener.onLog(text);
    }
    private void state(String text) { listener.onState(text, ready); }

    public void scan() { onMain(this::startScan); }
    private void startScan() {
        if (closed) return;
        stopScanInternal();
        if (adapter == null) { log("Bluetooth LE adapter unavailable."); return; }
        try {
            if (!adapter.isEnabled()) { log("Turn on Bluetooth before scanning."); return; }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { log("Bluetooth LE scanner unavailable."); return; }
            scanCallback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    main.post(() -> {
                        if (scanCallback != this || closed) return;
                        if (matches(result)) listener.onDevice(result.getDevice(), result.getRssi());
                    });
                }
                @Override public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult result : results) onScanResult(0, result);
                }
                @Override public void onScanFailed(int errorCode) {
                    main.post(() -> {
                        if (scanCallback != this) return;
                        log("Bluetooth scan failed: " + errorCode);
                        stopScanInternal();
                    });
                }
            };
            scanner.startScan(null, new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            log("Scanning for Pavlok / Shock Clock advertisements for 12 seconds.");
            scanTimer = () -> { stopScanInternal(); log("Scan finished."); };
            main.postDelayed(scanTimer, 12000);
        } catch (SecurityException ex) {
            stopScanInternal(); log("Bluetooth scan permission is required.");
        } catch (IllegalStateException ex) {
            stopScanInternal(); log("Bluetooth scanner is not available: " + ex.getMessage());
        }
    }

    private boolean matches(ScanResult result) {
        ScanRecord record = result.getScanRecord();
        String name = record == null ? null : record.getDeviceName();
        try { if (name == null) name = result.getDevice().getName(); }
        catch (SecurityException ignored) { }
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("pavlok") || lower.contains("shock")) return true;
        }
        List<ParcelUuid> services = record == null ? null : record.getServiceUuids();
        if (services != null) for (ParcelUuid service : services) {
            if (ADVERTISED_SERVICES.contains(service.getUuid())) return true;
        }
        return false;
    }

    public void stopScan() { onMain(this::stopScanInternal); }
    private void stopScanInternal() {
        if (scanTimer != null) main.removeCallbacks(scanTimer);
        scanTimer = null;
        ScanCallback old = scanCallback;
        scanCallback = null;
        try { if (scanner != null && old != null) scanner.stopScan(old); }
        catch (SecurityException | IllegalStateException ex) { log("Scan stopped locally; Bluetooth unavailable."); }
        scanner = null;
    }

    public void connect(BluetoothDevice device) {
        onMain(() -> {
            if (closed || device == null) return;
            stopScanInternal();
            releaseGatt();
            characteristics.clear(); duplicates.clear(); values.clear();
            serial = ""; firmware = ""; inventory = "Service discovery pending.\n";
            try {
                if (adapter == null || !adapter.isEnabled()) {
                    log("Turn on Bluetooth before connecting."); state("Disconnected"); return;
                }
                deviceIdentity = String.valueOf(device.getName()) + " / " + device.getAddress();
                state("Connecting; read-only discovery");
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
                if (gatt == null) { log("Android could not create a GATT connection."); state("Disconnected"); return; }
                connectionTimer = () -> {
                    log("Connection / service discovery timed out. No command was retried.");
                    releaseGatt(); state("Disconnected");
                };
                main.postDelayed(connectionTimer, CONNECT_TIMEOUT_MS);
            } catch (SecurityException ex) {
                releaseGatt(); log("Bluetooth connect permission is required."); state("Permission required");
            } catch (IllegalArgumentException | IllegalStateException ex) {
                releaseGatt(); log("Cannot connect: " + ex.getMessage()); state("Disconnected");
            }
        });
    }

    public void disconnect() { onMain(() -> { releaseGatt(); state("Disconnected"); }); }
    public void close() { onMain(() -> { stopScanInternal(); releaseGatt(); closed = true; }); }

    private void releaseGatt() {
        if (connectionTimer != null) main.removeCallbacks(connectionTimer);
        if (operationTimer != null) main.removeCallbacks(operationTimer);
        connectionTimer = null; operationTimer = null;
        ArrayDeque<Operation> cancelled = new ArrayDeque<>(queue);
        if (active != null) cancelled.addFirst(active);
        queue.clear(); active = null;
        connected = false; discovered = false; ready = false;
        BluetoothGatt old = gatt;
        gatt = null; // Invalidate callbacks before touching the Android connection.
        if (old != null) {
            try { old.disconnect(); } catch (SecurityException ignored) { }
            try { old.close(); } catch (SecurityException ignored) { }
        }
        for (Operation operation : cancelled) reportWrite(operation, false);
    }

    public boolean isLegacyReady() { return ready; }
    public boolean isIdle() { return active==null && queue.isEmpty(); }
    public String getSerial() { return serial; }
    public String getFirmware() { return firmware; }

    /** A read-only refresh while idle; never queues behind a device command. */
    public void refreshBatteryWhileIdle() {
        onMain(() -> {
            if(!ready || active!=null || !queue.isEmpty()) return;
            BluetoothGattCharacteristic c=characteristics.get(BATTERY);
            if(c!=null && !duplicates.contains(BATTERY) && (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ)!=0)
                enqueue(new Operation(READ,c,null,null,"Refresh battery"));
        });
    }

    public void readInfo() { onMain(this::readInfoInternal); }
    public void readRtc() {
        onMain(() -> {
            BluetoothGattCharacteristic c=characteristics.get(RTC);
            if(!ready||c==null||(c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ)==0) {
                log("RTC characteristic is not readable on this device."); return;
            }
            enqueue(new Operation(READ,c,null,null,"Read RTC value"));
        });
    }
    private void readInfoInternal() {
        if (!connected || !discovered) { log("Connect and discover services before reading device information."); return; }
        for (UUID id : Arrays.asList(SERIAL, FIRMWARE, HARDWARE, BATTERY, MODEL, MANUFACTURER, HAND_SETTINGS)) {
            BluetoothGattCharacteristic characteristic = characteristics.get(id);
            if (characteristic != null && !duplicates.contains(id)
                    && (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                enqueue(new Operation(READ, characteristic, null, null, "Read " + name(id)));
            } else log(name(id) + " is not uniquely readable on this device.");
        }
    }

    public void write(UUID id, byte[] bytes, String label) {
        final byte[] copy = bytes == null ? null : bytes.clone();
        onMain(() -> {
            String operationLabel = label == null ? (id == null ? "Unknown write" : name(id)) : label;
            if (!ready) {
                log("Write rejected: a complete legacy Shock Clock profile is required.");
                listener.onWriteComplete(id, operationLabel, false); return;
            }
            if (id == null || !ALLOWED_WRITES.contains(id) || copy == null || copy.length == 0 || copy.length > 20) {
                log("Write rejected: unsupported legacy characteristic or invalid payload length.");
                listener.onWriteComplete(id, operationLabel, false); return;
            }
            BluetoothGattCharacteristic characteristic = characteristics.get(id);
            if (characteristic == null || duplicates.contains(id) || !writableForProtocol(characteristic)) {
                log("Write rejected: characteristic is not uniquely writable with the required acknowledgement support.");
                listener.onWriteComplete(id, operationLabel, false); return;
            }
            enqueue(new Operation(WRITE, characteristic, null, copy,
                    operationLabel));
        });
    }

    public void enableRtcNotifications() {
        onMain(() -> {
            if (!ready) {
                log("RTC notifications require a recognized legacy profile.");
                listener.onWriteComplete(RTC, "Enable RTC notifications", false); return;
            }
            BluetoothGattCharacteristic characteristic = characteristics.get(RTC);
            int properties = characteristic.getProperties();
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD);
            if (descriptor == null || (properties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY
                    | BluetoothGattCharacteristic.PROPERTY_INDICATE)) == 0) {
                log("RTC notifications unavailable: no notification property / CCCD.");
                listener.onWriteComplete(RTC, "Enable RTC notifications", false); return;
            }
            byte[] value = (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
            enqueue(new Operation(NOTIFY, characteristic, descriptor, value.clone(), "Enable RTC notifications"));
        });
    }

    private static boolean writable(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }
    private static boolean writableForProtocol(BluetoothGattCharacteristic characteristic) {
        UUID id = characteristic.getUuid();
        if (id.equals(RTC) || id.equals(CONNECTION) || id.equals(SHOCK)) {
            return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0;
        }
        return writable(characteristic);
    }
    private void enqueue(Operation operation) {
        if (closed || gatt == null || !connected) {
            log("Operation rejected: disconnected."); reportWrite(operation, false); return;
        }
        if (queue.size() >= 32) {
            log("Queue overflow: cancelling pending operations and closing GATT. Active command outcome may be unknown.");
            releaseGatt(); reportWrite(operation, false); state("Disconnected after queue overflow"); return;
        }
        queue.addLast(operation);
        pump();
    }
    @SuppressWarnings("deprecation")
    private void pump() {
        if (active != null || queue.isEmpty() || gatt == null || !connected) return;
        active = queue.removeFirst();
        final Operation operation = active;
        boolean started = false;
        try {
            if (operation.kind == READ) started = gatt.readCharacteristic(operation.characteristic);
            else if (operation.kind == WRITE) {
                int type = (operation.characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                        ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
                if (Build.VERSION.SDK_INT >= 33) {
                    started = gatt.writeCharacteristic(operation.characteristic, operation.value, type)
                            == BluetoothStatusCodes.SUCCESS;
                } else {
                    operation.characteristic.setWriteType(type);
                    operation.characteristic.setValue(operation.value);
                    started = gatt.writeCharacteristic(operation.characteristic);
                }
            } else if (operation.kind == NOTIFY) {
                if (gatt.setCharacteristicNotification(operation.characteristic, true)) {
                    if (Build.VERSION.SDK_INT >= 33) started = gatt.writeDescriptor(operation.descriptor, operation.value)
                            == BluetoothStatusCodes.SUCCESS;
                    else {
                        operation.descriptor.setValue(operation.value);
                        started = gatt.writeDescriptor(operation.descriptor);
                    }
                }
            }
        } catch (SecurityException ex) { log("Bluetooth permission missing for " + operation.label + "."); }
        catch (IllegalArgumentException | IllegalStateException ex) { log("Cannot start " + operation.label + ": " + ex.getMessage()); }
        if (!started) { complete(false, "Android rejected operation before it started"); return; }
        log("Started " + operation.label + (operation.kind == WRITE ? " bytes=" + hex(operation.value) : ""));
        operationTimer = () -> {
            if (active != operation) return;
            log(operation.label + " timed out; physical outcome unknown. Queued operations cancelled; reconnect to continue.");
            releaseGatt(); state("Disconnected after timeout");
        };
        main.postDelayed(operationTimer, OP_TIMEOUT_MS);
    }

    private void complete(boolean success, String detail) {
        Operation operation = active;
        if (operation == null) return;
        if (operationTimer != null) main.removeCallbacks(operationTimer);
        operationTimer = null;
        active = null;
        if (success) {
            log(operation.label + ": " + detail + (operation.kind == WRITE
                    ? "; physical action / alarm storage is not confirmed." : "."));
        } else {
            ArrayDeque<Operation> cancelled = new ArrayDeque<>();
            if (operation.kind == READ) {
                // Optional information reads must not prevent later serial / firmware reads.
                for (Operation pending : queue) if (pending.kind != READ) cancelled.addLast(pending);
                queue.removeAll(cancelled);
            } else {
                cancelled.addAll(queue);
                queue.clear();
            }
            boolean commandsCancelled = operation.kind != READ;
            for (Operation pending : cancelled) if (pending.kind != READ) commandsCancelled = true;
            if (commandsCancelled) ready = false;
            log(operation.label + " failed: " + detail + ". Queued writes cancelled; no automatic retry.");
            if (operation.kind == NOTIFY && gatt != null) {
                try { gatt.setCharacteristicNotification(operation.characteristic, false); }
                catch (SecurityException ignored) { }
            }
            reportWrite(operation, false);
            for (Operation pending : cancelled) reportWrite(pending, false);
            state(commandsCancelled ? "Operation failed; reconnect before commands"
                    : (ready ? "Legacy profile recognized; some device info unavailable" : "Read-only: device info unavailable"));
        }
        if (success) reportWrite(operation, true);
        pump();
    }

    private void reportWrite(Operation operation, boolean success) {
        if (operation.kind == WRITE || operation.kind == NOTIFY) {
            listener.onWriteComplete(operation.characteristic.getUuid(), operation.label, success);
        }
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt source, int status, int newState) {
            main.post(() -> {
                if (source != gatt || closed) return;
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    log("GATT disconnected, status " + status + "; pending operations cancelled.");
                    releaseGatt(); state("Disconnected"); return;
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connected = true;
                    state("Connected; discovering services (read-only)");
                    try {
                        if (!source.discoverServices()) {
                            log("Android rejected service discovery."); releaseGatt(); state("Disconnected");
                        }
                    } catch (SecurityException ex) {
                        log("Permission required for service discovery."); releaseGatt(); state("Permission required");
                    }
                }
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt source, int status) {
            main.post(() -> {
                if (source != gatt || closed) return;
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    log("Service discovery failed: " + status); releaseGatt(); state("Disconnected"); return;
                }
                if (connectionTimer != null) main.removeCallbacks(connectionTimer);
                connectionTimer = null;
                discovered = true;
                inventoryServices(source);
                ready = hasLegacyProfile();
                state(ready ? "Legacy profile recognized; manual commands available"
                        : "Read-only: unsupported or incomplete legacy profile");
                log("Connection sends no activation, actuator, clock or alarm writes.");
                readInfoInternal();
            });
        }
        @Override public void onCharacteristicRead(BluetoothGatt source, BluetoothGattCharacteristic characteristic, int status) {
            byte[] value = characteristic.getValue();
            handleRead(source, characteristic, value == null ? new byte[0] : value.clone(), status);
        }
        @Override public void onCharacteristicRead(BluetoothGatt source, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            handleRead(source, characteristic, value.clone(), status);
        }
        @Override public void onCharacteristicWrite(BluetoothGatt source, BluetoothGattCharacteristic characteristic, int status) {
            main.post(() -> {
                if (source != gatt || !matchesActive(WRITE, characteristic)) return;
                boolean acknowledged = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0;
                complete(status == BluetoothGatt.GATT_SUCCESS, status == BluetoothGatt.GATT_SUCCESS
                        ? (acknowledged ? "Wearable ATT write response received"
                        : "Android submitted write without ATT response; wearable receipt is unconfirmed")
                        : statusText(status));
            });
        }
        @Override public void onDescriptorWrite(BluetoothGatt source, BluetoothGattDescriptor descriptor, int status) {
            main.post(() -> {
                if (source != gatt || active == null || active.kind != NOTIFY || active.descriptor != descriptor) return;
                complete(status == BluetoothGatt.GATT_SUCCESS, statusText(status));
            });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt source, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            handleNotification(source, characteristic, value == null ? new byte[0] : value.clone());
        }
        @Override public void onCharacteristicChanged(BluetoothGatt source, BluetoothGattCharacteristic characteristic, byte[] value) {
            handleNotification(source, characteristic, value.clone());
        }
    };

    private boolean matchesActive(int kind, BluetoothGattCharacteristic characteristic) {
        return active != null && active.kind == kind && active.characteristic == characteristic;
    }
    private void handleRead(BluetoothGatt source, BluetoothGattCharacteristic characteristic, byte[] bytes, int status) {
        main.post(() -> {
            if (source != gatt || !matchesActive(READ, characteristic)) return;
            if (status == BluetoothGatt.GATT_SUCCESS) recordValue(characteristic.getUuid(), bytes);
            complete(status == BluetoothGatt.GATT_SUCCESS, statusText(status));
        });
    }
    private void handleNotification(BluetoothGatt source, BluetoothGattCharacteristic characteristic, byte[] bytes) {
        main.post(() -> {
            if (source != gatt || !connected || closed) return;
            log("Notification " + name(characteristic.getUuid()) + " bytes=" + hex(bytes));
            recordValue(characteristic.getUuid(), bytes);
        });
    }
    private void recordValue(UUID id, byte[] bytes) {
        values.put(id, bytes.clone());
        if (id.equals(SERIAL)) serial = text(bytes);
        if (id.equals(FIRMWARE)) firmware = text(bytes);
        log(name(id) + " = " + (id.equals(BATTERY) && bytes.length > 0
                ? (bytes[0] & 255) + "%" : hex(bytes) + " [" + text(bytes) + "]"));
        listener.onValue(id, bytes.clone());
    }
    private void inventoryServices(BluetoothGatt source) {
        characteristics.clear(); duplicates.clear();
        newerServiceFound = false;
        StringBuilder result = new StringBuilder();
        for (BluetoothGattService service : source.getServices()) {
            if (service.getUuid().equals(PAVLOK_S_SERVICE)) {
                newerServiceFound = true;
            }
            result.append("Service ").append(service.getUuid()).append('\n');
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                UUID id = characteristic.getUuid();
                if (characteristics.put(id, characteristic) != null) duplicates.add(id);
                result.append("  ").append(id).append(" ").append(name(id))
                        .append(" properties=0x").append(Integer.toHexString(characteristic.getProperties()))
                        .append(" [").append(properties(characteristic.getProperties())).append("]\n");
                for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                    result.append("    Descriptor ").append(descriptor.getUuid()).append('\n');
                }
            }
        }
        inventory = result.toString();
        log("Discovered " + source.getServices().size() + " services and " + characteristics.size() + " distinct characteristics.");
    }
    private boolean hasLegacyProfile() {
        if (newerServiceFound) {
            log("Newer-generation service detected; legacy writes disabled."); return false;
        }
        for (String newer : Arrays.asList("1001", "1002", "1003", "1005", "200a", "4001")) {
            if (characteristics.containsKey(standard(newer))) {
                log("Newer-generation characteristic detected; legacy writes disabled."); return false;
            }
        }
        for (UUID id : Arrays.asList(VIBRATION, BEEP, SHOCK, RTC)) {
            BluetoothGattCharacteristic characteristic = characteristics.get(id);
            if (characteristic == null || duplicates.contains(id) || !writableForProtocol(characteristic)) return false;
        }
        return true;
    }
    private static String statusText(int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) return "GATT transport completed";
        if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
                || status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) {
            return "GATT status " + status + "; Android Bluetooth pairing / encryption may be required";
        }
        return "GATT status " + status;
    }
    private static String properties(int bits) {
        StringBuilder result = new StringBuilder();
        if ((bits & BluetoothGattCharacteristic.PROPERTY_READ) != 0) result.append("read ");
        if ((bits & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) result.append("write ");
        if ((bits & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) result.append("write-no-response ");
        if ((bits & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) result.append("notify ");
        if ((bits & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) result.append("indicate ");
        return result.toString().trim();
    }
    private static String name(UUID id) {
        if (id.equals(VIBRATION)) return "Vibration";
        if (id.equals(BEEP)) return "Beep";
        if (id.equals(SHOCK)) return "Shock";
        if (id.equals(RTC)) return "Legacy RTC / alarm";
        if (id.equals(CONNECTION)) return "Legacy connection";
        if (id.equals(HAND_SETTINGS)) return "Legacy mode settings";
        if (id.equals(LED)) return "LED";
        if (id.equals(BATTERY)) return "Battery";
        if (id.equals(FIRMWARE)) return "Firmware";
        if (id.equals(HARDWARE)) return "Hardware";
        if (id.equals(SERIAL)) return "Serial";
        if (id.equals(MODEL)) return "Model";
        if (id.equals(MANUFACTURER)) return "Manufacturer";
        return id.toString();
    }
    private static String text(byte[] bytes) {
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) end++;
        return new String(bytes, 0, end, StandardCharsets.UTF_8).trim();
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) {
            if (result.length() > 0) result.append(' ');
            result.append(String.format(Locale.ROOT, "%02x", value & 255));
        }
        return result.toString();
    }
    /** Call from the main thread, like the other synchronous getters. */
    public String diagnosticReport() {
        StringBuilder result = new StringBuilder("Libre Pavlok offline diagnostic\n");
        result.append("Device: ").append(deviceIdentity).append('\n')
                .append("Connected: ").append(connected).append("; legacy profile ready: ").append(ready).append('\n')
                .append("Serial: ").append(serial).append("; firmware: ").append(firmware).append('\n')
                .append("No server requests. GATT transport success does not confirm physical action or alarm storage.\n\n")
                .append(inventory).append("\nLast received values:\n");
        for (Map.Entry<UUID, byte[]> value : values.entrySet()) {
            result.append(value.getKey()).append(" ").append(name(value.getKey())).append(" = ")
                    .append(hex(value.getValue())).append('\n');
        }
        result.append("\nRecent local log:\n");
        for (String line : logLines) result.append(line).append('\n');
        return result.toString();
    }
    private static final class Operation {
        final int kind;
        final BluetoothGattCharacteristic characteristic;
        final BluetoothGattDescriptor descriptor;
        final byte[] value;
        final String label;
        Operation(int kind, BluetoothGattCharacteristic characteristic, BluetoothGattDescriptor descriptor,
                  byte[] value, String label) {
            this.kind = kind; this.characteristic = characteristic; this.descriptor = descriptor;
            this.value = value; this.label = label;
        }
    }
}
