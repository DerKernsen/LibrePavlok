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
            if(smoke)getTargetContext().getSharedPreferences("companion",0).edit().putInt("draftHour",7).putInt("draftMinute",0).putInt("draftRepeat",0).putInt("draftKind",1).putInt("draftLevel",6).putInt("remoteLevel",0).putInt("playLevel",0).commit();
            activity=startActivitySync(new Intent().setClassName("org.librepavlok.offline","org.librepavlok.offline.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); Thread.sleep(250);
            screenshot("remote");
            nav("Alarm"); screenshot("alarm");
            nav("Play"); screenshot("play");
            nav("Device"); screenshot("device");
            if(smoke) {
                onUi(() -> {
                    SeekBar remote=(SeekBar)value("level"),alarm=(SeekBar)value("alarmLevel");
                    remote.setProgress(9); alarm.setProgress(3);
                    check(remote.getProgress()==9&&alarm.getProgress()==3,"Independent strengths");
                    check(!((Button)value("shockButton")).isEnabled(),"Shock starts locked");
                    set("ready",true); set("sessionReady",true); invoke("updateControls");
                    invoke("showTab",new Class<?>[]{int.class},2);
                    ((SeekBar)value("playLevel")).setProgress(9);
                    ((CheckBox)value("shockEnabled")).setChecked(true); ((CheckBox)value("playConsent")).setChecked(true);
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
                    ((Button)value("playCancel")).performClick();
                    check(value("playCountdown")==null,"Cancel removes countdown");
                    check(!(Boolean)value("stimulusInFlight"),"Cancelled play does not dispatch");
                    set("ready",false); set("sessionReady",false); invoke("updateControls");
                    check(!((Button)value("playStart")).isEnabled(),"Disconnected play disabled");
                    set("hour",9); set("minute",25); ((Spinner)value("repeat")).setSelection(1);
                    invoke("saveDraft");
                });
                onUi(() -> activity.finish()); waitForIdleSync();
                activity=startActivitySync(new Intent().setClassName("org.librepavlok.offline","org.librepavlok.offline.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                onUi(() -> {
                    check((Integer)value("hour")==9&&(Integer)value("minute")==25,"Draft time restored");
                    check(((SeekBar)value("alarmLevel")).getProgress()==3,"Alarm strength restored");
                    check(((SeekBar)value("level")).getProgress()==9,"Remote strength restored");
                    check(((SeekBar)value("playLevel")).getProgress()==9,"Play strength restored");
                });
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
    private void set(String name,Object value){ try{ Field f=activity.getClass().getDeclaredField(name); f.setAccessible(true); f.set(activity,value); }catch(Exception e){ throw new RuntimeException(e); } }
    private void invoke(String name){ invoke(name,new Class<?>[0]); }
    private void invoke(String name,Class<?>[] types,Object...args){ try{ Method m=activity.getClass().getDeclaredMethod(name,types); m.setAccessible(true); m.invoke(activity,args); }catch(Exception e){ throw new RuntimeException(e); } }
    private void check(boolean valid,String label){ checks++; if(!valid)throw new AssertionError(label); }
}
