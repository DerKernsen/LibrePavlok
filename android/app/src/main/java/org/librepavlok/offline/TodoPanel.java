package org.librepavlok.offline;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.*;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/** All to-do settings and permissions stay inside this optional tab. */
final class TodoPanel {
    private final Activity activity;
    private final LinearLayout root;
    private final TodoStore store;
    private final SharedBle link;
    private final Runnable afterChange;
    private AlertDialog editor, reviewDialog;
    TodoPanel(Activity activity,LinearLayout root,SharedBle link,Runnable afterChange) {
        this.activity=activity; this.root=root; this.link=link; this.afterChange=afterChange; store=new TodoStore(activity);
    }
    void refresh() {
        root.removeAllViews();
        AppUi.text(root,"To-dos",30,AppUi.INK,true);
        AppUi.text(root,"Your tasks, with a deadline for each one.",14,AppUi.MUTED,false);
        LinearLayout settings=AppUi.card(root,AppUi.SOFT);
        AppUi.text(settings,store.enabled()?"Monitoring enabled":"Optional · monitoring paused",18,AppUi.INK,true);
        AppUi.text(settings,"An unfinished task sends its chosen signal burst at the deadline. Reminders appear on your phone. Keep the wearable connected and nearby; failed bursts are never retried.",13,AppUi.MUTED,false);
        AppUi.button(settings,store.enabled()?"Pause monitoring":"Enable monitoring",AppUi.GREEN,AppUi.WHITE,v -> {
            if(store.enabled()) {
                if(store.setEnabled(false)){ TodoScheduler.refresh(activity); refresh(); }
                else message("Could not pause monitoring. Try again.");
            } else enable();
        });
        if(store.enabled()&&!TodoScheduler.exactAllowed(activity))AppUi.text(settings,"Exact alarm access is missing. Re-enable monitoring to fix permissions.",13,AppUi.INK,true);
        AppUi.button(root,"Add to-do",AppUi.LIME,AppUi.INK,v -> edit(null));
        try {
            List<TodoItem> items=store.load();
            if(items.isEmpty()) {
                LinearLayout empty=AppUi.card(root,AppUi.WHITE);
                AppUi.text(empty,"Nothing on your list yet",20,AppUi.INK,true);
                AppUi.text(empty,"Add a task, choose its date and time, and optionally set a reminder such as 30 minutes before.",14,AppUi.MUTED,false);
            }
            for(TodoItem t:items)showItem(t);
        } catch(IllegalStateException e){ message(e.getMessage()); }
    }
    private void showItem(TodoItem t) {
        LinearLayout card=AppUi.card(root,t.completed?AppUi.SOFT:AppUi.WHITE);
        CheckBox done=new CheckBox(activity); done.setText(t.title); done.setTextSize(18); done.setTextColor(AppUi.INK);
        done.setMinHeight(AppUi.dp(activity,48)); done.setChecked(t.completed); done.setTag("todo:done:"+t.id); card.addView(done);
        AppUi.text(card,format(t.dueAt),16,AppUi.GREEN,true);
        AppUi.text(card,(t.reminderMinutes==0?"No advance reminder":t.reminderMinutes+" min before")+" · "+t.pulse().description()+" if unfinished",12,AppUi.MUTED,false);
        if(!t.outcome.isEmpty())AppUi.text(card,t.outcome,12,AppUi.MUTED,false);
        else if(t.dueAt<=System.currentTimeMillis()&&!t.completed)AppUi.text(card,"Overdue",13,AppUi.INK,true);
        done.setOnCheckedChangeListener((b,checked) -> {
            if(!checked&&t.dueAt<=System.currentTimeMillis()){ message("Choose a new future deadline to reopen this task."); refresh(); return; }
            mutate(t.id,item -> { item.completed=checked; if(checked)item.outcome="Completed. No further deadline action."; else item.outcome=""; });
        });
        LinearLayout buttons=AppUi.row(card);
        AppUi.button(buttons,"Edit",AppUi.SOFT,AppUi.INK,v -> edit(t));
        AppUi.button(buttons,"Delete",AppUi.SOFT,AppUi.INK,v -> new AlertDialog.Builder(activity).setTitle("Delete this to-do?")
                .setMessage(t.title+"\nIts pending reminder and deadline action will be cancelled.")
                .setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w) -> {
                    List<TodoItem> items=store.load(); removeItem(items,t.id); changed(items);
                }).show());
    }
    private interface Change { void apply(TodoItem item); }
    private void removeItem(List<TodoItem> items,String id){ for(int i=items.size()-1;i>=0;i--)if(items.get(i).id.equals(id))items.remove(i); }
    private void mutate(String id,Change change) {
        List<TodoItem> items=store.load(); for(TodoItem t:items)if(t.id.equals(id))change.apply(t); changed(items);
    }
    private void changed(List<TodoItem> items) {
        if(!store.save(items)){ message("Could not save tasks. Try again."); refresh(); return; }
        try { TodoScheduler.refresh(activity); }
        catch(RuntimeException e){ store.setEnabled(false); TodoScheduler.cancel(activity); activity.stopService(new Intent(activity,TodoService.class)); message("Monitoring could not start. Check Bluetooth and Android permissions, then enable it again."); }
        refresh(); afterChange.run();
    }
    private boolean permissions() {
        String bt=Build.VERSION.SDK_INT>=31?Manifest.permission.BLUETOOTH_CONNECT:Manifest.permission.BLUETOOTH;
        if(activity.checkSelfPermission(bt)!=PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{bt},103); message("Allow Bluetooth, then tap Enable monitoring again."); return false;
        }
        if(Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},103); return false;
        }
        if(Build.VERSION.SDK_INT>=24&&!((NotificationManager)activity.getSystemService(Activity.NOTIFICATION_SERVICE)).areNotificationsEnabled()) {
            message("Allow app notifications in Android Settings so you can receive reminders and pause monitoring."); return false;
        }
        if(!TodoScheduler.exactAllowed(activity)) {
            activity.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+activity.getPackageName())));
            return false;
        }
        if(activity.getSharedPreferences("companion",0).getString("deviceAddress","").isEmpty()) { message("Connect a wearable on Device first, then enable monitoring."); return false; }
        return true;
    }
    private void enable() {
        if(!permissions())return;
        new AlertDialog.Builder(activity).setTitle("Enable to-do deadline signals?")
                .setMessage("Each unfinished task can send its chosen burst, including repeated shocks, while this app is in the background. A persistent notification lets you pause monitoring. Old deadlines will be skipped; they will not trigger a catch-up burst.")
                .setNegativeButton("Not now",null).setPositiveButton("Enable monitoring",(d,w) -> {
                    List<TodoItem> items=store.load();
                    for(TodoItem t:items)if(t.deadlineDue(System.currentTimeMillis())){ t.handled=true; t.outcome="Deadline passed while paused. No shock sent."; }
                    if(!store.save(items)||!store.setEnabled(true)){ message("Could not enable monitoring. Try again."); return; }
                    link.suspended=false; changed(items);
                }).show();
    }
    private void edit(TodoItem existing) {
        LinearLayout form=AppUi.column(activity); int pad=AppUi.dp(activity,20); form.setPadding(pad,pad,pad,pad);
        EditText title=new EditText(activity); title.setHint("What needs doing?"); title.setSingleLine(true); title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});
        title.setTag("todo:title"); if(existing!=null)title.setText(existing.title); form.addView(title);
        Calendar due=Calendar.getInstance(); if(existing!=null)due.setTimeInMillis(existing.dueAt); else due.add(Calendar.HOUR_OF_DAY,1);
        due.set(Calendar.SECOND,0); due.set(Calendar.MILLISECOND,0);
        Button date=AppUi.button(form,format(due.getTimeInMillis()),AppUi.SOFT,AppUi.INK,v -> {}); date.setTag("todo:deadline");
        date.setOnClickListener(v -> new DatePickerDialog(activity,(picker,y,m,d) -> {
            due.set(y,m,d);
            new TimePickerDialog(activity,(time,h,min) -> { due.set(Calendar.HOUR_OF_DAY,h); due.set(Calendar.MINUTE,min); date.setText(format(due.getTimeInMillis())); },due.get(Calendar.HOUR_OF_DAY),due.get(Calendar.MINUTE),true).show();
        },due.get(Calendar.YEAR),due.get(Calendar.MONTH),due.get(Calendar.DAY_OF_MONTH)).show());
        AppUi.text(form,"Remind me this many minutes before (0 = off)",13,AppUi.MUTED,false);
        EditText reminder=new EditText(activity); reminder.setInputType(InputType.TYPE_CLASS_NUMBER); reminder.setSingleLine(true);
        reminder.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)}); reminder.setText(String.valueOf(existing==null?30:existing.reminderMinutes)); reminder.setTag("todo:reminder"); form.addView(reminder);
        AppUi.text(form,"Signal if unfinished",15,AppUi.INK,true);
        Spinner signal=new Spinner(activity);
        ArrayAdapter<String> signals=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,new String[]{"Vibration","Shock","Beep"});
        signals.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); signal.setAdapter(signals);
        signal.setSelection(existing==null?PulsePlan.SHOCK:existing.signal); signal.setTag("todo:signal");
        form.addView(signal,new LinearLayout.LayoutParams(-1,AppUi.dp(activity,49)));
        TextView strengthLabel=AppUi.text(form,"",24,AppUi.GREEN,true);
        SeekBar strength=new SeekBar(activity); strength.setMax(9); strength.setProgress(existing==null?0:existing.strength/10-1); strength.setTag("todo:strength"); form.addView(strength);
        strengthLabel.setText(((strength.getProgress()+1)*10)+"%");
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){ strengthLabel.setText(((p+1)*10)+"%"); }
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });
        PulseEditor timing=new PulseEditor(form,"Deadline pulse",signal.getSelectedItemPosition(),false,existing==null?640:existing.durationMs,existing==null?1:existing.pulseCount);
        timing.count.setTag("todo:count"); timing.duration.setTag("todo:duration");
        signal.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int position,long id){ timing.setSignal(position); }
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        AppUi.text(form,"Marking it done cancels its pending reminder and burst. Monitoring must be enabled for actions to run. A burst already submitted to the wearable cannot be recalled.",12,AppUi.MUTED,false);
        ScrollView scroll=new ScrollView(activity); scroll.addView(form);
        editor=new AlertDialog.Builder(activity).setTitle(existing==null?"Add to-do":"Edit to-do").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Review",null).create();
        editor.setOnShowListener(d -> editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int minutes=Integer.parseInt(reminder.getText().toString()); long deadline=due.getTimeInMillis();
                if(deadline<=System.currentTimeMillis())throw new IllegalArgumentException("Choose a future deadline.");
                timing.setSignal(signal.getSelectedItemPosition());
                TodoItem task=new TodoItem(existing==null?UUID.randomUUID().toString():existing.id,title.getText().toString(),deadline,minutes,timing.snapshot((strength.getProgress()+1)*10));
                if(minutes>0&&task.reminderAt()<=System.currentTimeMillis())throw new IllegalArgumentException("The advance reminder would already be past. Use fewer minutes or 0.");
                reviewDialog=new AlertDialog.Builder(activity).setTitle("Save deadline and signal?")
                        .setMessage(task.title+"\n"+format(task.dueAt)+"\n"+task.pulse().description()+" if unfinished. "+(task.reminderMinutes>0?"Phone reminder "+task.reminderMinutes+" minutes before.":"No advance reminder.")+(!store.enabled()?"\nMonitoring is currently paused.":""))
                        .setNegativeButton("Back",null).setPositiveButton("Save to-do",(dialog,which) -> {
                            List<TodoItem> items=store.load(); removeItem(items,task.id); items.add(task);
                            editor.dismiss(); changed(items);
                        }).show();
            } catch(NumberFormatException e){ message("Enter reminder minutes from 0 to 10080."); }
            catch(IllegalArgumentException e){ message(e.getMessage()); }
        })); editor.show();
    }
    private String format(long time){ return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new java.util.Date(time)); }
    private void message(String text){ new AlertDialog.Builder(activity).setMessage(text).setPositiveButton("OK",null).show(); }
}
