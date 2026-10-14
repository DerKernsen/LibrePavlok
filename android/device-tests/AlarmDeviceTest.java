package org.librepavlok.offline.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Explicitly invoked hardware test; not included in the companion APK. */
public final class AlarmDeviceTest extends Instrumentation {
    private Activity activity;
    private String action, expectedSerial, address;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        action=args.getString("action", "inspect");
        expectedSerial=args.getString("serial", "");
        address=args.getString("address", "");
        start();
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Intent intent=new Intent().setClassName("org.librepavlok.offline", "org.librepavlok.offline.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent);
            if(value("ble")==null)throw new IllegalStateException("Open the app and acknowledge the first-launch notice before a hardware test");
            if(expectedSerial.isEmpty()) throw new IllegalArgumentException("Wearable serial is required");
            if(address.isEmpty()) {
                click("Find device");
                long scanDeadline=System.currentTimeMillis()+14000;
                List<Button> candidates=new ArrayList<>();
                do {
                    runOnMainSync(() -> { candidates.clear(); for(Button b:buttons()) if(b.getText().toString().contains("·  Nearby")||b.getText().toString().contains("·  In range")) candidates.add(b); });
                    if(!candidates.isEmpty()) break;
                    Thread.sleep(250);
                } while(System.currentTimeMillis()<scanDeadline);
                if(candidates.size()!=1) throw new IllegalStateException("Expected one discovered wearable; found "+candidates.size());
                runOnMainSync(() -> candidates.get(0).performClick());
            } else {
                runOnMainSync(() -> {
                    android.bluetooth.BluetoothDevice device=android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
                    setValue("selectedDevice",device);
                    try { Object controller=value("ble"); controller.getClass().getMethod("connect",android.bluetooth.BluetoothDevice.class).invoke(controller,device); }
                    catch(Exception e) { throw new RuntimeException(e); }
                });
            }
            long deadline=System.currentTimeMillis()+20000;
            while(!expectedSerial.equals(value("serial"))&&System.currentTimeMillis()<deadline) Thread.sleep(250);
            if(!expectedSerial.equals(value("serial"))) throw new IllegalStateException("Wearable serial mismatch; no alarm write performed");
            while(!"2.4.99".equals(value("firmware"))&&System.currentTimeMillis()<deadline) Thread.sleep(250);
            if(!"2.4.99".equals(value("firmware"))) throw new IllegalStateException("Unexpected wearable firmware; no alarm write performed");
            if(!(Boolean)value("sessionReady"))click("Start local session");
            Thread.sleep(1500);
            if("vibration".equals(action)||"beep_ui_disconnected".equals(action)||"beep_ui_connected".equals(action)) {
                click("Alarm");
                java.util.Calendar testTime=java.util.Calendar.getInstance(); testTime.add(java.util.Calendar.MINUTE,3);
                testTime.set(java.util.Calendar.SECOND,0); testTime.set(java.util.Calendar.MILLISECOND,0);
                if(action.startsWith("beep_ui")) {
                    runOnMainSync(() -> {
                        setValue("hour",testTime.get(java.util.Calendar.HOUR_OF_DAY));
                        setValue("minute",testTime.get(java.util.Calendar.MINUTE));
                        ((android.widget.SeekBar)value("alarmLevel")).setProgress(9);
                        ((android.widget.Spinner)value("repeat")).setSelection(0);
                        ((android.widget.Spinner)value("alarmKind")).setSelection(1);
                        ((TextView)value("alarmTime")).setText(String.format(java.util.Locale.ROOT,"%02d:%02d",testTime.get(java.util.Calendar.HOUR_OF_DAY),testTime.get(java.util.Calendar.MINUTE)));
                    });
                    click("Save alarm to wearable…");
                } else click("Test vibration alarm in 3 minutes…");
                Thread.sleep(400);
                runOnMainSync(() -> {
                    android.app.AlertDialog dialog=latestDialog();
                    if(dialog==null) throw new IllegalStateException("No confirmation dialog");
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                });
                deadline=System.currentTimeMillis()+32000;
                while(busy()&&System.currentTimeMillis()<deadline) Thread.sleep(250);
                if("beep_ui_connected".equals(action)) {
                    while(System.currentTimeMillis()<testTime.getTimeInMillis()+15000) Thread.sleep(1000);
                    Object controller=value("ble");
                    write(controller,new byte[]{8},"Test: query state after alarm time"); Thread.sleep(600);
                    result.putString("session_log_at_trigger",String.valueOf(value("logs")));
                    write(controller,new byte[]{7,2,1},"Test cleanup: stop alarm");
                    write(controller,new byte[]{7,2,2},"Test cleanup: clear RTC selection");
                    write(controller,new byte[]{10},"Test cleanup: clear test records"); Thread.sleep(1000);
                }
            } else if("inspect".equals(action)) {
                runOnMainSync(() -> {
                    try { Object controller=value("ble"); controller.getClass().getMethod("readRtc").invoke(controller); }
                    catch(Exception e) { throw new RuntimeException(e); }
                });
                Thread.sleep(1200);
                click("Details"); click("Read wearable alarm status"); Thread.sleep(1800);
            } else if("stop".equals(action)) {
                click("Alarm"); click("Stop alarm"); Thread.sleep(800);
            } else if("sentinel".equals(action)||"beep".equals(action)||"beep_connected".equals(action)) {
                Object controller=value("ble");
                java.util.Calendar alarm=java.util.Calendar.getInstance(); alarm.add(java.util.Calendar.MINUTE,3);
                alarm.set(java.util.Calendar.SECOND,0); alarm.set(java.util.Calendar.MILLISECOND,0);
                Class<?> protocol=Class.forName("org.librepavlok.offline.LegacyProtocol");
                boolean beep=action.startsWith("beep");
                byte[] record=(byte[])protocol.getMethod(beep?"beepAlarm":"vibrationAlarm",String.class,int.class,java.util.Calendar.class,int.class,int.class).invoke(null,"2.4.99",128,alarm,beep?100:10,3);
                java.util.Calendar end=java.util.Calendar.getInstance();
                byte[] sentinel={5,127,0x59,0x59,0x23,bcd(end.get(java.util.Calendar.DAY_OF_MONTH)),bcd(end.get(java.util.Calendar.MONTH)+1),0x2a,0x61,0x71,0x71,(byte)0xa8,10,0};
                byte[] clock=(byte[])protocol.getMethod("clock",java.util.Calendar.class).invoke(null,java.util.Calendar.getInstance());
                runOnMainSync(() -> { try { controller.getClass().getMethod("enableRtcNotifications").invoke(controller); } catch(Exception e) { throw new RuntimeException(e); } });
                write(controller,new byte[]{10},"Test: clear old records");
                write(controller,clock,"Test: synchronize clock");
                write(controller,record,beep?"Test: append 100% beep alarm":"Test: append vibration alarm");
                write(controller,sentinel,"Test: append original rollover record");
                write(controller,new byte[]{7,2,2},"Test: clear RTC selection");
                Thread.sleep(1800);
                write(controller,new byte[]{2,1},"Test: activate slot 1");
                Thread.sleep(800);
                write(controller,new byte[]{8},"Test: query armed state");
                Thread.sleep(600);
                result.putString("test_time",String.format(java.util.Locale.ROOT,"%02d:%02d",alarm.get(java.util.Calendar.HOUR_OF_DAY),alarm.get(java.util.Calendar.MINUTE)));
                if("beep_connected".equals(action)) {
                    while(System.currentTimeMillis()<alarm.getTimeInMillis()+15000) Thread.sleep(1000);
                    write(controller,new byte[]{8},"Test: query state after alarm time");
                    Thread.sleep(600);
                    result.putString("session_log_at_trigger",String.valueOf(value("logs")));
                    write(controller,new byte[]{7,2,1},"Test cleanup: stop alarm");
                    write(controller,new byte[]{7,2,2},"Test cleanup: clear RTC selection");
                    write(controller,new byte[]{10},"Test cleanup: clear test records");
                    Thread.sleep(1000);
                }
            } else if("clear".equals(action)) {
                Object controller=value("ble");
                runOnMainSync(() -> { try { controller.getClass().getMethod("enableRtcNotifications").invoke(controller); } catch(Exception e) { throw new RuntimeException(e); } });
                write(controller,new byte[]{8},"Test cleanup: query initial state");
                write(controller,new byte[]{7,2,1},"Test cleanup: stop alarm");
                write(controller,new byte[]{7,2,2},"Test cleanup: clear RTC selection");
                write(controller,new byte[]{10},"Test cleanup: clear test records");
                write(controller,new byte[]{8},"Test cleanup: verify stopped state");
                write(controller,new byte[]{9},"Test cleanup: verify empty inventory");
                Thread.sleep(1800);
            } else throw new IllegalArgumentException("Unknown action: "+action);
            result.putString("alarm_status", textValue("alarmStatus"));
            result.putString("alarm_time", textValue("alarmTime"));
            result.putString("session_log", String.valueOf(value("logs")));
            click("Disconnect");
            finish(0,result);
        } catch(Throwable error) {
            result.putString("error",error.toString()); finish(1,result);
        }
    }
    private android.app.AlertDialog latestDialog() {
        // The test uses MainActivity's confirmation dialog, exposed only to instrumentation via reflection.
        return (android.app.AlertDialog)value("alarmConfirmation");
    }
    private byte bcd(int n) { return (byte)((n/10<<4)|(n%10)); }
    private void write(Object controller,byte[] bytes,String label) {
        runOnMainSync(() -> {
            try { controller.getClass().getMethod("write",java.util.UUID.class,byte[].class,String.class).invoke(controller,java.util.UUID.fromString("e6b17021-195d-4e84-b216-034caeb05498"),bytes,label); }
            catch(Exception e) { throw new RuntimeException(e); }
        });
    }
    private boolean busy() { return value("pendingAlarm")!=null||value("expectedAlarm")!=null; }
    private String textValue(String field) {
        final String[] result={""}; runOnMainSync(() -> result[0]=((TextView)value(field)).getText().toString()); return result[0];
    }
    private Object value(String field) {
        final Object[] result={null};
        Runnable read=() -> { try { Field f=activity.getClass().getDeclaredField(field); f.setAccessible(true); result[0]=f.get(activity); } catch(Exception e) { throw new RuntimeException(e); } };
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()) read.run(); else runOnMainSync(read);
        return result[0];
    }
    private void setValue(String field,Object setting) {
        try { Field f=activity.getClass().getDeclaredField(field); f.setAccessible(true); f.set(activity,setting); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
    private List<Button> buttons() { List<Button> result=new ArrayList<>(); collect(activity.getWindow().getDecorView(),result); return result; }
    private void collect(View view,List<Button> result) {
        if(view instanceof Button) result.add((Button)view);
        if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++) collect(group.getChildAt(i),result); }
    }
    private void click(String label) {
        runOnMainSync(() -> {
            String destination=label.equals("Details")?"Device":label;
            View tab=activity.getWindow().getDecorView().findViewWithTag("nav:"+destination);
            if(tab!=null){ tab.performClick(); return; }
            for(Button b:buttons()) if(label.equals(b.getText().toString())) {
                if(!b.isEnabled()) throw new IllegalStateException("Disabled: "+label);
                b.performClick(); return;
            }
            throw new IllegalStateException("Button missing: "+label);
        });
    }
}
