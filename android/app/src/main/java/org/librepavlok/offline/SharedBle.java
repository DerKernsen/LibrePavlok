package org.librepavlok.offline;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One GATT queue shared by the screen and optional background monitor. Main thread only. */
final class SharedBle implements BleController.Listener {
    private static SharedBle instance;
    static SharedBle get(Context context){ if(instance==null)instance=new SharedBle(context); return instance; }
    final BleController controller;
    final ArrayList<BleController.Listener> listeners=new ArrayList<>();
    private final Map<UUID,byte[]> values=new LinkedHashMap<>();
    private String state="Disconnected";
    boolean ready, sessionReady, connecting, suspended, todoSending, monitor;
    interface BusyState { boolean getAsBoolean(); }
    BusyState activityBusy;
    private boolean handshaking;
    private SharedBle(Context context){ controller=new BleController(context,this); }
    void attach(BleController.Listener listener){ if(!listeners.contains(listener))listeners.add(listener); }
    void replay(BleController.Listener listener) {
        listener.onState(state,ready);
        for(Map.Entry<UUID,byte[]> e:values.entrySet())listener.onValue(e.getKey(),e.getValue().clone());
    }
    void detach(BleController.Listener listener){
        listeners.remove(listener);
        if(listeners.isEmpty()){ controller.close(); instance=null; }
    }
    void connectRemembered(Context context) {
        if(suspended||ready||connecting)return;
        String address=context.getSharedPreferences("companion",0).getString("deviceAddress","");
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        try { if(adapter!=null&&adapter.isEnabled()&&BluetoothAdapter.checkBluetoothAddress(address))controller.connect(adapter.getRemoteDevice(address)); }
        catch(SecurityException ignored){}
    }
    void ensureSession() {
        String firmware=controller.getFirmware();
        if(!ready||sessionReady||handshaking||!LegacyProtocol.isSupportedLegacyFirmware(firmware))return;
        if(!LegacyProtocol.isShockClockFirmware(firmware)){ sessionReady=true; return; }
        if(!controller.isIdle())return;
        try {
            byte[] handshake=LegacyProtocol.connectionHandshake(controller.getSerial());
            handshaking=true; controller.write(UUID.fromString(LegacyProtocol.CONNECTION),handshake,"Local handshake");
        } catch(IllegalArgumentException ignored){}
    }
    boolean available(){ return ready&&sessionReady&&controller.isLegacyReady()&&controller.isIdle()&&!todoSending&&(activityBusy==null||!activityBusy.getAsBoolean()); }
    @Override public void onLog(String text){ for(BleController.Listener l:new ArrayList<>(listeners))l.onLog(text); }
    @Override public void onDevice(BluetoothDevice device,int rssi){ for(BleController.Listener l:new ArrayList<>(listeners))l.onDevice(device,rssi); }
    @Override public void onState(String text,boolean ready) {
        state=text; this.ready=ready; connecting=text.startsWith("Connecting")||text.startsWith("Connected");
        if(!ready){ sessionReady=false; handshaking=false; if(!connecting)values.clear(); }
        for(BleController.Listener l:new ArrayList<>(listeners))l.onState(text,ready);
    }
    @Override public void onValue(UUID id,byte[] value) {
        values.put(id,value.clone());
        if(monitor)ensureSession();
        for(BleController.Listener l:new ArrayList<>(listeners))l.onValue(id,value.clone());
    }
    @Override public void onWriteComplete(UUID id,String label,boolean success) {
        if(label.equals("Local handshake")){ handshaking=false; sessionReady=success; }
        if(!success)sessionReady=false;
        for(BleController.Listener l:new ArrayList<>(listeners))l.onWriteComplete(id,label,success);
    }
}
