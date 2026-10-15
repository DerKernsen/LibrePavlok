package org.librepavlok.offline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class TodoNotifications {
    static final int MONITOR_ID=4000;
    static Notification build(Context c,String title,String message,boolean ongoing) {
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        String channel=ongoing?"todo-monitor":"todo-reminders";
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(channel,ongoing?"To-do monitoring":"To-do reminders",ongoing?NotificationManager.IMPORTANCE_LOW:NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(c,MainActivity.class).putExtra("openTodos",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(c,10,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel):new Notification.Builder(c);
        b.setSmallIcon(c.getApplicationInfo().icon).setContentTitle(title).setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(content).setOngoing(ongoing).setAutoCancel(!ongoing);
        if(ongoing) {
            PendingIntent stop=PendingIntent.getService(c,11,new Intent(c,TodoService.class).setAction("pause"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(null,"Pause monitoring",stop).build());
        }
        return b.build();
    }
    static void show(Context c,int id,String title,String message) {
        try { ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(4100+id,build(c,title,message,false)); }
        catch(SecurityException ignored){}
    }
}
