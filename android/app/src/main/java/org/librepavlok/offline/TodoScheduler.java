package org.librepavlok.offline;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.List;

final class TodoScheduler {
    private static long scheduledAt=Long.MAX_VALUE;
    static boolean exactAllowed(Context c){ return Build.VERSION.SDK_INT<31||((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms(); }
    private static PendingIntent alarm(Context c){
        return PendingIntent.getBroadcast(c,0,new Intent(c,TodoReceiver.class).setAction("org.librepavlok.offline.TODO_TICK"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void cancel(Context c){ scheduledAt=Long.MAX_VALUE; ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(alarm(c)); }
    static void schedule(Context c,List<TodoItem> items) {
        if(!new TodoStore(c).enabled()||!exactAllowed(c)){ cancel(c); return; }
        long next=Long.MAX_VALUE,now=System.currentTimeMillis();
        for(TodoItem t:items)next=Math.min(next,t.nextEvent(now));
        if(next==scheduledAt&&next>now)return;
        cancel(c);
        if(next!=Long.MAX_VALUE) {
            ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,Math.max(now+100,next),alarm(c));
            scheduledAt=next;
        }
    }
    static void refresh(Context c) {
        TodoStore store=new TodoStore(c);
        if(c.getSharedPreferences("companion",0).getInt("disclaimerVersion",0)<AppDisclaimer.VERSION){ cancel(c); return; }
        if(!store.enabled()||!TodoStore.hasPending(store.load())) {
            cancel(c); c.stopService(new Intent(c,TodoService.class)); return;
        }
        Intent i=new Intent(c,TodoService.class);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i); else c.startService(i);
    }
    static void resumeFromUi(Context c) {
        TodoStore store=new TodoStore(c);
        if(!store.enabled()||TodoService.running)return;
        List<TodoItem> items=store.load();
        for(TodoItem t:items)if(t.deadlineDue(System.currentTimeMillis())){ t.handled=true; t.outcome="Deadline passed while monitoring was stopped. No shock sent."; }
        if(store.save(items))refresh(c);
    }
}
