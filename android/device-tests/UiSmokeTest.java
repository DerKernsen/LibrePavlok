package org.librepavlok.offline.tests;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.EditText;
import org.json.JSONArray;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** UI-only QA. Never connects to Bluetooth or sends any wearable command. */
public final class UiSmokeTest extends Instrumentation {
    private Activity activity;
    private boolean smoke;
    private int checks;
    @Override public void onCreate(Bundle args){ super.onCreate(args); smoke="smoke".equals(args.getString("mode")); start(); }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            if(smoke)getTargetContext().getSharedPreferences("todos",0).edit().clear().commit();
            if(smoke)getTargetContext().getSharedPreferences("companion",0).edit().putInt("draftHour",7).putInt("draftMinute",0).putInt("draftRepeat",0).putInt("draftKind",1).putInt("draftLevel",6).putInt("remoteLevel",0).putInt("playLevel",0).remove("disclaimerVersion").commit();
            activity=startActivitySync(new Intent().setClassName("org.librepavlok.offline","org.librepavlok.offline.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); Thread.sleep(250);
            if(value("ble")==null) {
                screenshot("disclaimer");
                if(!smoke) {
                    result.putString("screen","First-launch notice; acknowledgement unchanged"); finish(0,result); return;
                }
                onUi(() -> {
                    check(value("ble")==null,"No Bluetooth controller before acknowledgement");
                    check(find(activity.getWindow().getDecorView(),"nav:Remote")==null,"No app controls before acknowledgement");
                    check(!((Button)value("disclaimerContinue")).isEnabled(),"Continue requires acknowledgement");
                    View exit=find(activity.getWindow().getDecorView(),"disclaimer:exit");
                    check(exit!=null,"Exit available"); exit.performClick();
                }); waitForIdleSync();
                check(getTargetContext().getSharedPreferences("companion",0).getInt("disclaimerVersion",0)==0,"Exit does not acknowledge notice");
                activity=startActivitySync(new Intent().setClassName("org.librepavlok.offline","org.librepavlok.offline.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                waitForIdleSync();
                onUi(() -> {
                    check(value("ble")==null,"Notice repeats after exit without acknowledgement");
                    ((CheckBox)value("disclaimerAcknowledgement")).setChecked(true);
                    check(((Button)value("disclaimerContinue")).isEnabled(),"Acknowledgement enables Continue");
                    ((Button)value("disclaimerContinue")).performClick();
                    check(value("ble")!=null&&find(activity.getWindow().getDecorView(),"nav:Remote")!=null,"Controls initialize after acknowledgement");
                }); waitForIdleSync();
                check(getTargetContext().getSharedPreferences("companion",0).getInt("disclaimerVersion",0)==1,"Notice version persisted");
            }
            screenshot("remote");
            nav("Alarm"); screenshot("alarm");
            nav("Play"); screenshot("play");
            nav("Device"); screenshot("device");
            nav("To-dos"); screenshot("todos");
            if(smoke) {
                onUi(() -> {
                    try {
                        check(!getTargetContext().getSharedPreferences("todos",0).getBoolean("enabled",false),"To-dos disabled by default");
                        Object panel=value("todos");
                        Class<?> item=activity.getClassLoader().loadClass("org.librepavlok.offline.TodoItem");
                        Method edit=panel.getClass().getDeclaredMethod("edit",item); edit.setAccessible(true); edit.invoke(panel,new Object[]{null});
                    } catch(Exception e){ throw new RuntimeException(e); }
                });
                waitForIdleSync();
                onUi(() -> {
                    try {
                        Object panel=value("todos");
                        Field editorField=panel.getClass().getDeclaredField("editor"); editorField.setAccessible(true);
                        AlertDialog editor=(AlertDialog)editorField.get(panel);
                        ((EditText)find(editor.getWindow().getDecorView(),"todo:title")).setText("Finish the report");
                        ((EditText)find(editor.getWindow().getDecorView(),"todo:reminder")).setText("0");
                        ((SeekBar)find(editor.getWindow().getDecorView(),"todo:strength")).setProgress(9);
                        ((Spinner)find(editor.getWindow().getDecorView(),"todo:signal")).setSelection(2);
                        ((SeekBar)find(editor.getWindow().getDecorView(),"todo:duration")).setProgress(249);
                        ((SeekBar)find(editor.getWindow().getDecorView(),"todo:count")).setProgress(4);
                        editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                        Field reviewField=panel.getClass().getDeclaredField("reviewDialog"); reviewField.setAccessible(true);
                        AlertDialog review=(AlertDialog)reviewField.get(panel);
                        check(review!=null&&review.isShowing(),"To-do deadline and shock confirmation");
                        review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    } catch(Exception e){ throw new RuntimeException(e); }
                });
                waitForIdleSync();
                onUi(() -> {
                    try {
                        JSONArray rows=new JSONArray(getTargetContext().getSharedPreferences("todos",0).getString("items","[]"));
                        check(rows.length()==1&&rows.getJSONObject(0).getInt("strength")==100,"To-do saved with independent 100% strength");
                        check(rows.getJSONObject(0).getInt("signal")==2&&rows.getJSONObject(0).getInt("durationMs")==5000&&rows.getJSONObject(0).getInt("pulseCount")==5,"To-do saves signal, duration and repeats");
                        String id=rows.getJSONObject(0).getString("id");
                        ((CheckBox)find(activity.getWindow().getDecorView(),"todo:done:"+id)).setChecked(true);
                        rows=new JSONArray(getTargetContext().getSharedPreferences("todos",0).getString("items","[]"));
                        check(rows.getJSONObject(0).getBoolean("completed"),"Completing a to-do persists");
                        check(!getTargetContext().getSharedPreferences("todos",0).getBoolean("enabled",false),"Saving tasks does not enable shocks");
                    } catch(Exception e){ throw new RuntimeException(e); }
                });
                screenshot("todos-completed");
                onUi(() -> {
                    SeekBar remote=(SeekBar)value("level"),alarm=(SeekBar)value("alarmLevel");
                    remote.setProgress(9); alarm.setProgress(3);
                    check(remote.getProgress()==9&&alarm.getProgress()==3,"Independent strengths");
                    timing("remotePulses",0,"duration").setProgress(99); timing("remotePulses",0,"count").setProgress(2);
                    timing("remotePulses",2,"duration").setProgress(254); timing("remotePulses",2,"count").setProgress(6);
                    timing("remotePulses",1,"count").setProgress(2);
                    timing("alarmPulses",0,"count").setProgress(4); timing("alarmPulses",2,"count").setProgress(6); timing("alarmPulses",1,"count").setProgress(3);
                    timing("playPulses",0,"duration").setProgress(149); timing("playPulses",0,"count").setProgress(4);
                    timing("playPulses",1,"count").setProgress(5);
                    timing("playPulses",2,"duration").setProgress(49); timing("playPulses",2,"count").setProgress(1);
                    check(timing("remotePulses",2,"duration").getProgress()==254&&timing("remotePulses",0,"duration").getProgress()==99,"Remote beep and vibration durations independent");
                    check(!((Button)value("shockButton")).isEnabled(),"Shock starts locked");
                    set("ready",true); set("sessionReady",true); invoke("updateControls");
                    invoke("showTab",new Class<?>[]{int.class},2);
                    ((SeekBar)value("playLevel")).setProgress(9);
                    ((CheckBox)value("shockEnabled")).setChecked(true); ((CheckBox)value("playConsent")).setChecked(true);
                    set("firmware","2.4.99");
                    invoke("shock"); AlertDialog shockReview=(AlertDialog)value("shockConfirmation");
                    check(((android.widget.TextView)shockReview.findViewById(android.R.id.message)).getText().toString().contains("3 shocks"),"Remote confirmation names repeat count");
                    shockReview.dismiss();
                    ((Spinner)value("alarmKind")).setSelection(1); invoke("confirmAlarm");
                    AlertDialog alarmReview=(AlertDialog)value("alarmConfirmation");
                    check(((android.widget.TextView)alarmReview.findViewById(android.R.id.message)).getText().toString().contains("7 beep pulses per alarm cycle"),"Alarm confirmation names saved pulse count");
                    alarmReview.dismiss();
                    ((Spinner)value("playMode")).setSelection(1); ((Spinner)value("playDelay")).setSelection(2);
                    invoke("confirmPlay");
                    AlertDialog dialog=(AlertDialog)value("playConfirmation");
                    check(dialog!=null&&dialog.isShowing(),"Play confirmation");
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                });
                waitForIdleSync();
                onUi(() -> {
                    check(value("playCountdown")!=null,"Countdown starts");
                    try { check(value("playCountdown").getClass().getField("strength").getInt(value("playCountdown"))==100,"Play preserves 100% strength"); }
                    catch(Exception e){ throw new RuntimeException(e); }
                    check(planInt("count")==6,"Play captures chosen shock repeat count");
                    check(!timing("playPulses",1,"count").isEnabled(),"Running countdown freezes pulse settings");
                    ((Button)value("playCancel")).performClick();
                    check(value("playCountdown")==null,"Cancel removes countdown");
                    check(!(Boolean)value("stimulusInFlight"),"Cancelled play does not dispatch");
                    ((Spinner)value("playMode")).setSelection(2);
                    ((CheckBox)value("shockEnabled")).setChecked(false); ((CheckBox)value("playConsent")).setChecked(false);
                    invoke("confirmPlay");
                    ((AlertDialog)value("playConfirmation")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                });
                waitForIdleSync();
                onUi(() -> {
                    check(planInt("signal")==2&&planInt("durationMs")==1000&&planInt("count")==2,"Play beep captures duration and repeats without shock consent");
                    ((Button)value("playCancel")).performClick();
                    set("ready",false); set("sessionReady",false); invoke("updateControls");
                    check(!((Button)value("playStart")).isEnabled(),"Disconnected play disabled");
                    set("hour",9); set("minute",25); ((Spinner)value("repeat")).setSelection(1);
                    invoke("saveDraft");
                });
                onUi(() -> activity.finish()); waitForIdleSync();
                activity=startActivitySync(new Intent().setClassName("org.librepavlok.offline","org.librepavlok.offline.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                onUi(() -> {
                    check(value("ble")!=null&&find(activity.getWindow().getDecorView(),"nav:Remote")!=null,"Acknowledged notice skipped on restart");
                    check((Integer)value("hour")==9&&(Integer)value("minute")==25,"Draft time restored");
                    check(((SeekBar)value("alarmLevel")).getProgress()==3,"Alarm strength restored");
                    check(((SeekBar)value("level")).getProgress()==9,"Remote strength restored");
                    check(((SeekBar)value("playLevel")).getProgress()==9,"Play strength restored");
                    check(timing("remotePulses",0,"duration").getProgress()==99&&timing("remotePulses",2,"duration").getProgress()==254,"Remote per-signal durations restored");
                    check(timing("remotePulses",1,"count").getProgress()==2,"Remote shock repeats restored");
                    check(timing("alarmPulses",0,"count").getProgress()==4&&timing("alarmPulses",2,"count").getProgress()==6&&timing("alarmPulses",1,"count").getProgress()==3,"Alarm signal counts restored independently");
                    check(timing("playPulses",0,"duration").getProgress()==149&&timing("playPulses",1,"count").getProgress()==5&&timing("playPulses",2,"duration").getProgress()==49,"Play signal timing restored independently");
                    try { check(new JSONArray(getTargetContext().getSharedPreferences("todos",0).getString("items","[]")).getJSONObject(0).getBoolean("completed"),"Completed to-do survives relaunch"); }
                    catch(Exception e){ throw new RuntimeException(e); }
                });
                getTargetContext().getSharedPreferences("todos",0).edit().clear().commit();
            }
            nav("Remote"); result.putInt("ui_checks",checks); result.putString("screenshots",getTargetContext().getExternalFilesDir(null).toString()); finish(0,result);
        } catch(Throwable e){ result.putString("error",e.toString()); finish(1,result); }
    }
    private void nav(String name){ onUi(() -> { View v=find(activity.getWindow().getDecorView(),"nav:"+name); if(v==null)throw new IllegalStateException("Missing nav: "+name); v.performClick(); }); waitForIdleSync(); }
    private void onUi(Runnable work){ final Throwable[] failure={null}; runOnMainSync(() -> { try{ work.run(); }catch(Throwable e){ failure[0]=e; } }); if(failure[0]!=null)throw new RuntimeException(failure[0]); }
    private View find(View root,String tag){ if(tag.equals(root.getTag()))return root; if(root instanceof ViewGroup){ ViewGroup group=(ViewGroup)root; for(int i=0;i<group.getChildCount();i++){ View v=find(group.getChildAt(i),tag); if(v!=null)return v; } } return null; }
    private void screenshot(String name)throws Exception {
        Thread.sleep(180); Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap==null)throw new IllegalStateException("Screenshot unavailable");
        try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"ui-"+name+".png"))){ bitmap.compress(Bitmap.CompressFormat.PNG,100,output); }
        bitmap.recycle();
    }
    private Object value(String name){ try{ Field f=activity.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(activity); }catch(Exception e){ throw new RuntimeException(e); } }
    private SeekBar timing(String group,int signal,String name){ try{ Object editor=((Object[])value(group))[signal]; Field f=editor.getClass().getDeclaredField(name); f.setAccessible(true); return (SeekBar)f.get(editor); }catch(Exception e){ throw new RuntimeException(e); } }
    private int planInt(String name){ try{ Object plan=value("playCountdown").getClass().getField("pulse").get(value("playCountdown")); return plan.getClass().getField(name).getInt(plan); }catch(Exception e){ throw new RuntimeException(e); } }
    private void set(String name,Object value){ try{ Field f=activity.getClass().getDeclaredField(name); f.setAccessible(true); f.set(activity,value); }catch(Exception e){ throw new RuntimeException(e); } }
    private void invoke(String name){ invoke(name,new Class<?>[0]); }
    private void invoke(String name,Class<?>[] types,Object...args){ try{ Method m=activity.getClass().getDeclaredMethod(name,types); m.setAccessible(true); m.invoke(activity,args); }catch(Exception e){ throw new RuntimeException(e); } }
    private void check(boolean valid,String label){ checks++; if(!valid)throw new AssertionError(label); }
}
