package org.librepavlok.offline.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Method;

/** Emulator-only deadline/notification integration. Never selects a Bluetooth address. */
public final class TodoMonitorTest extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle args){ super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle(); Activity activity=null;
        SharedPreferences store=getTargetContext().getSharedPreferences("todos",0);
        try {
            if(!Build.PRODUCT.contains("sdk")&&!Build.PRODUCT.contains("gphone"))throw new IllegalStateException("Emulator only");
            getTargetContext().getSharedPreferences("companion",0).edit().putInt("disclaimerVersion",1).remove("deviceAddress").commit();
            store.edit().clear().commit();
            activity=startActivitySync(new Intent().setClassName("org.librepavlok.offline","org.librepavlok.offline.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long now=System.currentTimeMillis();
            JSONArray rows=new JSONArray();
            rows.put(task("advance",now+59000,1,false));
            rows.put(task("due",now-1000,0,false));
            rows.put(task("stale",now-61000,0,false));
            rows.put(task("done",now-1000,0,true));
            rows.put(task("beep",now-1000,0,false).put("signal",2).put("durationMs",5100).put("pulseCount",7));
            rows.put(task("vibration",now-1000,0,false).put("signal",0).put("durationMs",2000).put("pulseCount",3));
            store.edit().putString("items",rows.toString()).putBoolean("enabled",true).commit();
            Intent service=new Intent().setClassName("org.librepavlok.offline","org.librepavlok.offline.TodoService");
            runOnMainSync(() -> { if(Build.VERSION.SDK_INT>=26)getTargetContext().startForegroundService(service); else getTargetContext().startService(service); });
            Thread.sleep(1800);
            JSONArray actual=new JSONArray(store.getString("items","[]"));
            check(find(actual,"advance").getBoolean("reminded"),"Advance reminder claimed in foreground service");
            check(find(actual,"due").getBoolean("handled"),"Deadline claimed without wearable");
            check(find(actual,"due").getString("outcome").contains("No shock sent"),"Disconnected deadline records no shock");
            check(find(actual,"stale").getString("outcome").contains("interrupted"),"Old deadline skips shock");
            check(!find(actual,"done").getBoolean("handled"),"Completed deadline ignored");
            check(find(actual,"due").getInt("signal")==1&&find(actual,"due").getInt("pulseCount")==1,"Previous task JSON migrates to one shock");
            check(find(actual,"beep").getInt("durationMs")==5100&&find(actual,"beep").getInt("pulseCount")==7&&find(actual,"beep").getString("outcome").contains("No beep sent"),"Beep timing retained and disconnection handled");
            check(find(actual,"vibration").getInt("signal")==0&&find(actual,"vibration").getString("outcome").contains("No vibration sent"),"Vibration task timing retained");
            String outcomes=actual.toString();
            runOnMainSync(() -> getTargetContext().startService(service));
            Thread.sleep(1200);
            check(store.getString("items","").equals(outcomes),"Duplicate service starts do not repeat events");
            ClassLoader loader=activity.getClassLoader();
            Class<?> serviceClass=loader.loadClass("org.librepavlok.offline.TodoService");
            java.lang.reflect.Field running=serviceClass.getDeclaredField("running"); running.setAccessible(true);
            check(running.getBoolean(null),"Monitor remains active for pending task");
            actual.put(task("background",System.currentTimeMillis()+2500,0,false));
            store.edit().putString("items",actual.toString()).commit();
            Activity visible=activity;
            runOnMainSync(() -> { getTargetContext().startService(service); visible.moveTaskToBack(true); });
            try(android.os.ParcelFileDescriptor ignored=getUiAutomation().executeShellCommand("dumpsys deviceidle force-idle")){ Thread.sleep(4500); }
            finally { try(android.os.ParcelFileDescriptor ignored=getUiAutomation().executeShellCommand("dumpsys deviceidle unforce")){} }
            actual=new JSONArray(store.getString("items","[]"));
            check(find(actual,"background").getBoolean("handled"),"Exact deadline processed with app in background and device idle");
            check(find(actual,"background").getString("outcome").contains("No shock sent"),"Background deadline does not send without wearable");
            Intent pause=new Intent(service).setAction("pause");
            runOnMainSync(() -> getTargetContext().startService(pause));
            Thread.sleep(600);
            check(!store.getBoolean("enabled",true),"Notification action pauses monitoring");
            check(!running.getBoolean(null),"Paused monitor stops service");
            // Clock changes expire past tasks and cannot replay an actuator event.
            rows=new JSONArray().put(task("clock-change",now-1000,0,false));
            store.edit().putString("items",rows.toString()).commit();
            Class<?> receiverClass=loader.loadClass("org.librepavlok.offline.TodoReceiver");
            Object receiver=receiverClass.getConstructor().newInstance();
            Method receive=receiverClass.getMethod("onReceive",android.content.Context.class,Intent.class);
            runOnMainSync(() -> { try{ receive.invoke(receiver,getTargetContext(),new Intent(Intent.ACTION_TIME_CHANGED)); }catch(Exception e){ throw new RuntimeException(e); } });
            check(find(new JSONArray(store.getString("items","[]")),"clock-change").getBoolean("handled"),"Clock change expires past deadline");
            result.putInt("todo_monitor_checks",checks); finish(0,result);
        } catch(Throwable e){ result.putString("error",e.toString()); finish(1,result); }
        finally {
            store.edit().clear().commit();
            getTargetContext().stopService(new Intent().setClassName("org.librepavlok.offline","org.librepavlok.offline.TodoService"));
            if(activity!=null){ Activity end=activity; runOnMainSync(end::finish); }
        }
    }
    private JSONObject task(String id,long due,int reminder,boolean completed)throws Exception {
        return new JSONObject().put("id",id).put("title",id).put("due",due).put("reminder",reminder).put("strength",10)
                .put("completed",completed).put("handled",false).put("reminded",false).put("outcome","");
    }
    private JSONObject find(JSONArray rows,String id)throws Exception { for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id))return rows.getJSONObject(i); throw new AssertionError("Missing task"); }
    private void check(boolean value,String message){ checks++; if(!value)throw new AssertionError(message); }
}
