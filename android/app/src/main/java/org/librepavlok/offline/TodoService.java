package org.librepavlok.offline;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import java.util.List;
import java.util.UUID;

/** Opt-in foreground monitor; deadlines are phone alarms, never wearable RTC records. */
public final class TodoService extends Service implements BleController.Listener {
    static boolean running;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedBle link;
    private TodoStore store;
    private PowerManager.WakeLock wake;
    private String sendingId;
    private PulsePlan sendingPulse;
    private long sendingDueAt;
    private long lastConnect;
    private boolean ticking;
    private final Runnable tick=new Runnable(){ @Override public void run(){ process(); handler.postDelayed(this,1000); } };
    @Override public void onCreate() {
        running=true;
        super.onCreate(); store=new TodoStore(this);
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"LibrePavlok:todo");
        wake.setReferenceCounted(false);
        link=SharedBle.get(this); link.monitor=true; link.attach(this);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        try { startForeground(TodoNotifications.MONITOR_ID,TodoNotifications.build(this,"To-do monitoring active","Deadlines require a connected wearable. Tap to manage tasks.",true)); }
        catch(RuntimeException e){ store.setEnabled(false); TodoScheduler.cancel(this); stopSelf(); return START_NOT_STICKY; }
        if(intent!=null&&"pause".equals(intent.getAction())){ store.setEnabled(false); TodoScheduler.cancel(this); stopSelf(); return START_NOT_STICKY; }
        // A recreated process must not catch up old deadline shocks.
        if(intent==null) {
            List<TodoItem> items=store.load();
            for(TodoItem t:items)if(t.deadlineDue(System.currentTimeMillis())){ t.handled=true; t.outcome="Monitoring restarted after deadline. No shock sent."; }
            if(!store.save(items)){ stopSelf(); return START_NOT_STICKY; }
        }
        if(!ticking){ ticking=true; handler.post(tick); }
        process(); return START_STICKY;
    }
    private void process() {
        try {
            if(!store.enabled()){ TodoScheduler.cancel(this); stopSelf(); return; }
            if(!TodoScheduler.exactAllowed(this))throw new IllegalStateException("Exact alarm access revoked");
            List<TodoItem> items=store.load(); long now=System.currentTimeMillis();
            if(!TodoStore.hasPending(items)&&sendingId==null){ TodoScheduler.cancel(this); stopSelf(); return; }
            if(!link.ready&&!link.connecting&&now-lastConnect>30000){ lastConnect=now; link.connectRemembered(this); }
            link.ensureSession();
            for(TodoItem t:items) {
                if(t.reminderDue(now)) {
                    t.reminded=true;
                    if(!store.save(items))throw new IllegalStateException("Could not save reminder state");
                    TodoNotifications.show(this,t.id.hashCode(),"Due soon: "+t.title,"Deadline: "+android.text.format.DateFormat.format("EEE, dd MMM HH:mm",t.dueAt)+". Mark it done in To-dos.");
                    link.onLog("To-do: advance reminder delivered to phone notifications.");
                }
                if(!t.deadlineDue(now))continue;
                if(link.todoSending)continue;
                PulsePlan pulse=t.pulse();
                boolean send=t.canDispatch(now)&&link.available();
                String skipped=" No "+pulse.name()+" sent.";
                t.handled=true; t.outcome=send?"Burst requested: "+pulse.description()+"; physical delivery is unverified.":now-t.dueAt>TodoItem.MAX_LATENESS_MS?"Deadline missed while monitoring was interrupted."+skipped:"Deadline missed: wearable disconnected or busy."+skipped;
                // Durable claim before BLE: a crash or duplicate alarm cannot repeat a shock.
                if(!store.save(items))throw new IllegalStateException("Could not claim deadline");
                if(send) {
                    wake.acquire(15000); sendingId=t.id; sendingPulse=pulse; sendingDueAt=t.dueAt; link.todoSending=true;
                    link.onLog("To-do: sending "+pulse.description()+".");
                    link.controller.write(UUID.fromString(pulse.characteristic()),pulse.command(link.controller.getFirmware()),"To-do:"+t.id);
                    // Finish the next due task only after this write resolves.
                    break;
                }
                TodoNotifications.show(this,t.id.hashCode(),"Overdue: "+t.title,t.outcome+" Open To-dos to mark it done.");
                link.onLog("To-do: missed deadline recorded without a burst.");
            }
            TodoScheduler.schedule(this,items);
        } catch(RuntimeException e) {
            store.setEnabled(false);
            TodoScheduler.cancel(this); stopSelf();
            TodoNotifications.show(this,2,"To-do monitoring stopped","Open To-dos to check permissions and saved tasks.");
        }
    }
    @Override public void onWriteComplete(UUID id,String label,boolean success) {
        if(sendingId==null||!label.equals("To-do:"+sendingId))return;
        try {
            List<TodoItem> items=store.load();
            for(TodoItem t:items)if(t.id.equals(sendingId)&&t.handled&&t.dueAt==sendingDueAt) {
                t.outcome=success?"Burst command sent: "+sendingPulse.description()+". Physical delivery is unverified.":"Burst command failed or delivery is unknown. No retry.";
                TodoNotifications.show(this,t.id.hashCode(),"Overdue: "+t.title,t.outcome);
            }
            store.save(items);
        } finally { sendingId=null; sendingPulse=null; link.todoSending=false; if(wake.isHeld())wake.release(); link.onLog("To-do: deadline action finished."); }
    }
    @Override public void onState(String state,boolean ready){}
    @Override public void onValue(UUID id,byte[] value){}
    @Override public void onDevice(BluetoothDevice d,int rssi){}
    @Override public void onLog(String message){}
    @Override public IBinder onBind(Intent intent){ return null; }
    @Override public void onDestroy() {
        running=false;
        handler.removeCallbacksAndMessages(null);
        if(wake!=null&&wake.isHeld())wake.release();
        if(link!=null){ link.monitor=false; link.todoSending=false; link.detach(this); }
        stopForeground(true); super.onDestroy();
    }
}
