package org.librepavlok.offline;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TodoReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        try {
            // After reboot / clock edits expire old events, never replay a past shock.
            if(!"org.librepavlok.offline.TODO_TICK".equals(intent.getAction())) {
                TodoStore store=new TodoStore(context);
                java.util.List<TodoItem> items=store.load();
                long now=System.currentTimeMillis();
                for(TodoItem t:items)if(t.deadlineDue(now)){ t.handled=true; t.outcome="Missed while monitoring was interrupted. No shock sent."; }
                if(!store.save(items))return;
            }
            TodoScheduler.refresh(context);
        } catch(RuntimeException e){
            // Revoked permissions or Android restrictions: fail closed, keep tasks for review.
            TodoNotifications.show(context,2,"To-do monitoring stopped","Open To-dos to check permissions and restart monitoring.");
        }
    }
}
