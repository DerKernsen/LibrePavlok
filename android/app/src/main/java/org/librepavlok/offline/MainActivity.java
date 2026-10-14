package org.librepavlok.offline;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A local-only companion; connecting reads device information and never actuates it. */
public final class MainActivity extends Activity implements BleController.Listener {
    private static final int INK=AppUi.INK, GREEN=AppUi.GREEN, MUTED=AppUi.MUTED;
    private LinearLayout page, devices, controlPanel, alarmPanel, playPanel, diagnosisPanel;
    private TextView status, info, logView, levelLabel, alarmTime, alarmStatus, alarmConnection;
    private TextView batteryBadge, actionStatus, quickStrength, nextAlarmText, deviceInfo, alarmLevelLabel, playLevelLabel, playStatus, playNumber;
    private SeekBar level, alarmLevel, playLevel;
    private CheckBox shockEnabled;
    private CheckBox playConsent;
    private Spinner alarmKind, repeat, playMode, playDelay;
    private Button connectionAction, shockButton, playStart, playCancel, stickyAlarmSave;
    private ScrollView scroll;
    private SharedPreferences preferences;
    private final ArrayList<View> editorControls=new ArrayList<>(), emergencyControls=new ArrayList<>();
    private final ArrayList<LinearLayout> navItems=new ArrayList<>();
    private int activeTab;
    private boolean stimulusInFlight;
    private PlayCountdown playCountdown;
    private AlertDialog shockConfirmation, playConfirmation;
    private AlarmSnapshot savingSnapshot;
    private BleController ble;
    private AlertDialog alarmConfirmation;
    private boolean ready, sessionReady;
    private int hour = 7, minute = 0;
    private String firmware = "", serial = "", battery = "—", hardware = "—";
    private final ArrayList<View> sessionControls = new ArrayList<>();
    private final HashSet<String> seen = new HashSet<>();
    private final StringBuilder logs = new StringBuilder();
    private String exportText;
    private byte[] pendingAlarm, expectedAlarm, expectedRollover;
    private boolean awaitingAlarmReadback, awaitingAlarmState;
    private boolean checkingStoredCount, checkingRtcIndex;
    private int verificationSlot=1, activationSlot=1;
    private BluetoothDevice selectedDevice;
    private boolean explicitDisconnect, sessionRequested;
    private int reconnectAttempts;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable batteryRefresh = new Runnable() {
        @Override public void run() {
            if(ready&&!isAlarmBusy()) ble.refreshBatteryWhileIdle();
            handler.postDelayed(this,12000);
        }
    };
    private final Runnable reconnect = () -> {
        if(selectedDevice!=null&&!explicitDisconnect&&!ready) {
            firmware="";serial="";onLog("Reconnecting after link loss; no stimulus or alarm is retried.");ble.connect(selectedDevice);
        }
    };
    private final Runnable readbackTimeout = () -> {
        if(isAlarmBusy()) failAlarm("Alarm verification timed out. The wearable has not confirmed a saved, armed alarm.");
    };
    private final Runnable activateAlarm = () -> {
        if(expectedAlarm!=null&&ready&&sessionReady) {
            activationSlot=LegacyProtocol.nextAlarmIndex(expectedAlarm,Calendar.getInstance());
            writeRtc(LegacyProtocol.selectAlarm(activationSlot),"Alarm: activate selected slot");
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        preferences=getSharedPreferences("companion",MODE_PRIVATE);
        hour=preferences.getInt("draftHour",7); minute=preferences.getInt("draftMinute",0);
        ble=new BleController(this,this);
        getWindow().setStatusBarColor(AppUi.BG); getWindow().setNavigationBarColor(AppUi.BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                |(Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));
        LinearLayout root=AppUi.column(this); root.setBackgroundColor(AppUi.BG);
        if(Build.VERSION.SDK_INT>=29)root.setForceDarkAllowed(false);
        root.setPadding(0,dp(12),0,dp(10));
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets) -> {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(0,bars.top+dp(10),0,bars.bottom+dp(8)); return insets;
            });
        }
        setContentView(root);

        LinearLayout brand=row(root); brand.setPadding(dp(22),0,dp(22),dp(12));
        brand.addView(new AppUi.Icon(this,"bolt",GREEN,27));
        TextView name=new TextView(this); name.setText("LibrePavlok"); name.setTextSize(22); name.setTextColor(INK);
        name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); name.setPadding(dp(9),0,0,0);
        brand.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView local=new TextView(this); local.setText("  OFFLINE  "); local.setTextSize(10); local.setTextColor(GREEN);
        local.setPadding(dp(5),dp(7),dp(5),dp(7)); AppUi.surface(local,AppUi.SOFT,10,false); brand.addView(local);

        LinearLayout connection=row(root); connection.setPadding(dp(16),dp(8),dp(10),dp(8));
        AppUi.surface(connection,AppUi.WHITE,20,true);
        LinearLayout.LayoutParams cp=(LinearLayout.LayoutParams)connection.getLayoutParams();
        cp.setMargins(dp(20),0,dp(20),dp(8));
        LinearLayout connectionText=AppUi.column(this); connection.addView(connectionText,new LinearLayout.LayoutParams(0,-2,1));
        status=text(connectionText,"Disconnected",14,INK,true);
        info=text(connectionText,"Connect your wearable",11,MUTED,false);
        connectionText.setOnClickListener(v -> showTab(3));
        batteryBadge=new TextView(this); batteryBadge.setTextSize(12); batteryBadge.setTextColor(GREEN);
        batteryBadge.setPadding(dp(8),0,dp(8),0); connection.addView(batteryBadge); batteryBadge.setVisibility(View.GONE);
        connectionAction=button(connection,"Connect",AppUi.LIME,INK,v -> quickConnect());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(92),dp(44)); ap.leftMargin=dp(4);
        connectionAction.setLayoutParams(ap); connectionAction.setMinHeight(dp(44)); connectionAction.setMinimumHeight(dp(44));

        scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        page=AppUi.column(this); page.setPadding(dp(20),dp(3),dp(20),dp(22)); scroll.addView(page);
        controlPanel=panel(); alarmPanel=panel(); playPanel=panel(); diagnosisPanel=panel();
        buildRemote(); buildAlarm(); buildPlay(); buildDevice();
        stickyAlarmSave=sessionButton(root,"Save alarm to wearable…",AppUi.GREEN,AppUi.WHITE,v -> confirmAlarm());
        LinearLayout.LayoutParams saveLayout=(LinearLayout.LayoutParams)stickyAlarmSave.getLayoutParams();
        saveLayout.setMargins(dp(20),dp(4),dp(20),dp(4));
        playStart=sessionButton(root,"Start countdown…",AppUi.INK,AppUi.WHITE,v -> confirmPlay());
        playCancel=button(root,"Cancel countdown",AppUi.LIME,INK,v -> cancelPlay("Countdown cancelled."));
        for(Button action:new Button[]{playStart,playCancel}) {
            LinearLayout.LayoutParams layout=(LinearLayout.LayoutParams)action.getLayoutParams();
            layout.setMargins(dp(20),dp(4),dp(20),dp(4));
        }

        LinearLayout nav=row(root); nav.setPadding(dp(5),dp(5),dp(5),dp(5));
        AppUi.surface(nav,AppUi.WHITE,24,true);
        LinearLayout.LayoutParams np=(LinearLayout.LayoutParams)nav.getLayoutParams(); np.setMargins(dp(16),dp(5),dp(16),0);
        String[] titles={"Remote","Alarm","Play","Device"}, icons={"wave","alarm","play","device"};
        for(int i=0;i<titles.length;i++) {
            final int tab=i; LinearLayout item=AppUi.column(this); item.setGravity(Gravity.CENTER);
            item.setPadding(0,dp(9),0,dp(7)); item.setTag("nav:"+titles[i]);
            item.setContentDescription(titles[i]+" tab"); item.setClickable(true); item.setFocusable(true);
            AppUi.Icon icon=new AppUi.Icon(this,icons[i],MUTED,23); item.addView(icon);
            TextView label=text(item,titles[i],11,MUTED,true); label.setGravity(Gravity.CENTER);
            nav.addView(item,new LinearLayout.LayoutParams(0,-2,1)); navItems.add(item); item.setOnClickListener(v -> showTab(tab));
        }
        updateControls(); refreshSavedSummary(); showTab(0); handler.postDelayed(batteryRefresh,12000);
    }

    private LinearLayout panel() { LinearLayout p=AppUi.column(this); page.addView(p,new LinearLayout.LayoutParams(-1,-2)); return p; }
    private void heading(LinearLayout panel,String title,String subtitle) {
        text(panel,title,30,INK,true); text(panel,subtitle,14,MUTED,false);
    }
    private void buildRemote() {
        heading(controlPanel,"Remote", "Wearable controls, one tap away.");
        LinearLayout quick=card(controlPanel);
        text(quick,"Quick controls",18,INK,true);
        quickStrength=text(quick,"10% strength · one pulse",12,GREEN,true);
        LinearLayout actions=row(quick);
        Button vibe=sessionButton(actions,"Vibrate once",v -> pulse(false));
        Button beep=sessionButton(actions,"Beep once",v -> pulse(true));
        vibe.setMinHeight(dp(78)); beep.setMinHeight(dp(78)); vibe.setTextSize(15); beep.setTextSize(15);
        actionStatus=text(quick,"Connect to use controls.",12,GREEN,true);

        LinearLayout zap=AppUi.card(controlPanel,AppUi.PEACH);
        text(zap,"Shock",20,INK,true);
        text(zap,"One shock, at the strength you choose.",13,MUTED,false);
        shockEnabled=check(zap,"Unlock shock controls");
        shockEnabled.setOnCheckedChangeListener((b,on) -> { if(!on)cancelPlay("Cancelled. Shock controls are locked."); updateControls(); });
        shockButton=sessionButton(zap,"Send one shock…",AppUi.INK,AppUi.WHITE,v -> shock());

        LinearLayout strength=card(controlPanel); text(strength,"Remote strength",18,INK,true);
        levelLabel=text(strength,"10%",27,GREEN,true);
        level=slider(strength,preferences.getInt("remoteLevel",0));
        level.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int p,boolean user) { levelLabel.setText(intensity()+"%"); quickStrength.setText(intensity()+"% strength · one pulse"); shockButton.setText("Send one shock · "+intensity()+"%…"); }
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });
        levelLabel.setText(intensity()+"%");
        quickStrength.setText(intensity()+"% strength · one pulse"); shockButton.setText("Send one shock · "+intensity()+"%…");
        LinearLayout presets=row(strength);
        for(int value:new int[]{10,50,100})button(presets,value+"%",v -> level.setProgress(value/10-1));
        text(strength,"This setting controls the remote. Your alarm has its own.",12,MUTED,false);

        LinearLayout next=AppUi.card(controlPanel,AppUi.SOFT);
        text(next,"LAST SAVED ALARM",10,GREEN,true); nextAlarmText=text(next,"No alarm saved yet.",20,INK,true);
        button(next,"Edit alarm →",v -> showTab(1));
        Button stop=sessionButton(controlPanel,"Stop alarm",v -> writeRtc(new byte[]{7,2,1},"Stop alarm")); emergencyControls.add(stop);
    }
    private void buildAlarm() {
        heading(alarmPanel,"Alarm", "Saved directly on your wearable.");
        LinearLayout time=AppUi.card(alarmPanel,AppUi.GREEN);
        text(time,"WAKE-UP TIME",10,AppUi.LIME,true);
        alarmTime=text(time,String.format(Locale.ROOT,"%02d:%02d",hour,minute),62,AppUi.WHITE,true);
        alarmTime.setContentDescription("Wake-up time. Tap to edit"); alarmTime.setOnClickListener(v -> chooseTime()); editorControls.add(alarmTime);
        Button edit=button(time,"Choose time",AppUi.LIME,INK,v -> chooseTime()); editorControls.add(edit);
        LinearLayout naps=row(time);
        for(int n:new int[]{15,25,45}) { Button b=button(naps,"+"+n+" min",AppUi.SOFT,INK,v -> setNap(n)); editorControls.add(b); }

        LinearLayout options=card(alarmPanel);
        text(options,"Alarm settings",18,INK,true);
        text(options,"Repeat",12,MUTED,true); repeat=spinner(options,"Once (next occurrence)","Every day");
        repeat.setSelection(preferences.getInt("draftRepeat",0)); editorControls.add(repeat);
        text(options,"Wake-up signal",12,MUTED,true); alarmKind=spinner(options,"Vibration","Beep","Shock");
        alarmKind.setSelection(preferences.getInt("draftKind",1)); editorControls.add(alarmKind);
        text(options,"Alarm strength",12,MUTED,true); alarmLevelLabel=text(options,"70%",27,GREEN,true);
        alarmLevel=slider(options,preferences.getInt("draftLevel",6)); editorControls.add(alarmLevel);
        alarmLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int p,boolean user){ alarmLevelLabel.setText(alarmIntensity()+"%"); }
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        }); alarmLevelLabel.setText(alarmIntensity()+"%");
        text(options,"Shock alarms use the unlock on Remote. Beep and vibration need no extra setup.",12,MUTED,false);
        alarmConnection=text(alarmPanel,"Connect to save. You can edit the time first.",12,MUTED,false);
        alarmStatus=text(alarmPanel,lastSavedDescription(),13,GREEN,true);
        LinearLayout alarmActions=row(alarmPanel);
        Button snooze=sessionButton(alarmActions,"Snooze",v -> writeRtc(new byte[]{7,1,1},"Snooze")); emergencyControls.add(snooze);
        Button stop=sessionButton(alarmActions,"Stop alarm",v -> writeRtc(new byte[]{7,2,1},"Stop alarm")); emergencyControls.add(stop);

        LinearLayout more=AppUi.column(this);
        button(alarmPanel,"Alarm tools ▾",v -> more.setVisibility(more.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        alarmPanel.addView(more); more.setVisibility(View.GONE);
        sessionButton(more,"Test beep alarm in 3 minutes…",v -> prepareAlarmTest(true));
        sessionButton(more,"Test vibration alarm in 3 minutes…",v -> prepareAlarmTest(false));
        sessionButton(more,"Clear wearable alarms…",v -> new AlertDialog.Builder(this).setTitle("Clear all wearable alarms?")
                .setMessage("This stops the active alarm and removes everything saved on your wearable.")
                .setNegativeButton("Keep them",null).setPositiveButton("Clear alarms",(d,w) -> {
                    writeRtc(new byte[]{7,2,1},"Stop before clearing alarms");
                    writeRtc(LegacyProtocol.clearRtc(),"Reset RTC before clearing alarms");
                    writeRtc(LegacyProtocol.clearAlarms(),"Clear alarms");
                }).show());
    }
    private void buildPlay() {
        heading(playPanel,"Play", "A delayed pulse, within Bluetooth range.");
        LinearLayout card=AppUi.card(playPanel,AppUi.GOLD);
        text(card,"Countdown",19,INK,true);
        playNumber=text(card,"—",58,GREEN,true); playNumber.setGravity(Gravity.CENTER);
        playStatus=text(card,"Choose a signal and delay.",13,MUTED,false); playStatus.setGravity(Gravity.CENTER);
        text(card,"Signal",12,MUTED,true); playMode=spinner(card,"One vibration","One shock");
        text(card,"Strength",12,MUTED,true);
        playLevelLabel=text(card,"10%",27,GREEN,true);
        playLevel=slider(card,preferences.getInt("playLevel",0));
        playLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int p,boolean user){ playLevelLabel.setText(playIntensity()+"%"); }
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        }); playLevelLabel.setText(playIntensity()+"%");
        text(card,"Delay",12,MUTED,true); playDelay=spinner(card,"3 seconds","5 seconds","8 seconds","Random: 3–8 seconds"); playDelay.setSelection(1);
        playConsent=check(card,"Wearer has agreed");
        playConsent.setOnCheckedChangeListener((b,on) -> { if(!on&&playCountdown!=null&&playCountdown.shock)cancelPlay("Countdown cancelled."); });
        text(card,"One pulse when the countdown ends.",12,MUTED,false);
        text(playPanel,"Leaving Play or disconnecting cancels the countdown.",12,MUTED,false);
    }
    private void buildDevice() {
        heading(diagnosisPanel,"Device", "Bluetooth connection and device details.");
        LinearLayout device=card(diagnosisPanel);
        text(device,"Connection",18,INK,true);
        deviceInfo=text(device,"Wake your wearable and keep it nearby. If the old app is connected, disconnect it there first.",13,MUTED,false);
        button(device,"Find device",AppUi.GREEN,AppUi.WHITE,v -> requestScan());
        if(!preferences.getString("deviceAddress","").isEmpty())button(device,"Reconnect last wearable",v -> quickConnect());
        devices=AppUi.column(this); device.addView(devices);
        button(device,"Disconnect",v -> disconnectDevice());
        button(device,"Start local session",v -> startSession());
        text(device,"Connecting never sends a beep, vibration or shock.",12,MUTED,false);
        LinearLayout help=AppUi.card(diagnosisPanel,AppUi.SOFT);
        text(help,"Connection tools",18,INK,true);
        text(help,"Wake it, bring it close, and try again. Android may also ask for Nearby devices or Location access to scan.",13,MUTED,false);
        button(help,"Refresh device info",v -> { if(isAlarmBusy())notice("Let the alarm finish saving first."); else ble.readInfo(); });
        sessionButton(help,"Read wearable alarm status",v -> { ble.enableRtcNotifications(); writeRtc(LegacyProtocol.readAlarmStatus(),"Query alarm state"); writeRtc(LegacyProtocol.readAlarmInventory(),"Query stored alarm count"); });
        LinearLayout report=card(diagnosisPanel); text(report,"For the curious",18,INK,true);
        text(report,"If something acts weird, save a local report. It stays yours until you share it.",13,MUTED,false);
        button(report,"Export local report",v -> exportReport());
        button(report,"Show connection log ▾",v -> logView.setVisibility(logView.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        logView=text(report,"Ready. Everything stays on this phone.",11,MUTED,false);
        logView.setTypeface(Typeface.MONOSPACE); logView.setTextIsSelectable(true); logView.setVisibility(View.GONE);
        text(diagnosisPanel,"LibrePavlok 0.2.0 · Made for the original Shock Clock",11,MUTED,false);
    }
    private CheckBox check(LinearLayout parent,String label) {
        CheckBox c=new CheckBox(this); c.setText(label); c.setTextColor(INK); c.setTextSize(13);
        c.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN)); c.setMinHeight(dp(48));
        parent.addView(c,new LinearLayout.LayoutParams(-1,-2)); return c;
    }
    private SeekBar slider(LinearLayout parent,int progress) {
        SeekBar s=new SeekBar(this); s.setMax(9); s.setProgress(Math.max(0,Math.min(9,progress)));
        s.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN)); s.setThumbTintList(android.content.res.ColorStateList.valueOf(GREEN));
        parent.addView(s,new LinearLayout.LayoutParams(-1,dp(46))); return s;
    }
    private void chooseTime() {
        if(isAlarmBusy())return;
        new TimePickerDialog(this,(p,h,m) -> { hour=h; minute=m; alarmTime.setText(String.format(Locale.ROOT,"%02d:%02d",h,m)); saveDraft(); },hour,minute,true).show();
    }
    private void setNap(int minutes) {
        Calendar time=Calendar.getInstance(); time.add(Calendar.MINUTE,minutes);
        hour=time.get(Calendar.HOUR_OF_DAY); minute=time.get(Calendar.MINUTE); repeat.setSelection(0);
        alarmTime.setText(String.format(Locale.ROOT,"%02d:%02d",hour,minute)); saveDraft();
    }
    private void prepareAlarmTest(boolean beep) {
        setNap(3); repeat.setSelection(0); alarmKind.setSelection(beep?1:0); alarmLevel.setProgress(beep?9:0); confirmAlarm();
    }
    private int alarmIntensity(){ return (alarmLevel.getProgress()+1)*10; }
    private void saveDraft() {
        if(preferences==null||level==null||alarmLevel==null)return;
        preferences.edit().putInt("draftHour",hour).putInt("draftMinute",minute).putInt("draftRepeat",repeat.getSelectedItemPosition())
                .putInt("draftKind",alarmKind.getSelectedItemPosition()).putInt("draftLevel",alarmLevel.getProgress())
                .putInt("remoteLevel",level.getProgress()).putInt("playLevel",playLevel.getProgress()).apply();
    }
    private String lastSavedDescription() { return preferences.getString("lastSaved","No verified alarm saved yet."); }
    private void refreshSavedSummary() {
        if(nextAlarmText!=null)nextAlarmText.setText(preferences.getString("lastSavedShort","No alarm saved yet."));
    }
    private void quickConnect() {
        if(ready){ showTab(3); return; }
        String address=preferences.getString("deviceAddress","");
        String permission=Build.VERSION.SDK_INT>=31?Manifest.permission.BLUETOOTH_CONNECT:Manifest.permission.ACCESS_FINE_LOCATION;
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        if(!address.isEmpty()&&BluetoothAdapter.checkBluetoothAddress(address)&&checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED&&adapter!=null&&adapter.isEnabled()) {
            connectChosen(adapter.getRemoteDevice(address));
        } else { showTab(3); requestScan(); }
    }
    private void connectChosen(BluetoothDevice device) {
        if(isAlarmBusy()||stimulusInFlight){ notice("Let the current action finish first."); return; }
        cancelPlay("Cancelled. The connection changed."); handler.removeCallbacks(reconnect);
        selectedDevice=device; explicitDisconnect=false; reconnectAttempts=0; sessionRequested=true;
        firmware=""; serial=""; battery="—"; hardware="—"; shockEnabled.setChecked(false);
        try { preferences.edit().putString("deviceAddress",device.getAddress()).putString("deviceName",String.valueOf(device.getName())).apply(); }
        catch(SecurityException ignored){}
        ble.connect(device);
    }
    private void disconnectDevice() {
        cancelPlay("Cancelled. The wearable disconnected."); explicitDisconnect=true; sessionRequested=false;
        handler.removeCallbacks(reconnect); ble.disconnect();
    }
    private void confirmPlay() {
        if(!canSend())return;
        if(playConfirmation!=null&&playConfirmation.isShowing())return;
        boolean zap=playMode.getSelectedItemPosition()==1;
        if(zap&&!shockEnabled.isChecked()){ showTab(0); notice("Unlock shock controls on Remote first."); return; }
        if(zap&&!playConsent.isChecked()){ notice("Confirm wearer agreement to start."); return; }
        int choice=playDelay.getSelectedItemPosition(); int delay=choice==0?3:choice==1?5:choice==2?8:3+new Random().nextInt(6);
        int strength=playIntensity();
        playConfirmation=new AlertDialog.Builder(this).setTitle(zap?"One "+strength+"% shock in "+delay+" seconds?":"One buzz in "+delay+" seconds?")
                .setMessage("The countdown stays visible. Cancel any time before it finishes. "+(zap?"One shock only, at the strength above.":"Practice mode sends one vibration at "+strength+"%."))
                .setNegativeButton("Not now",null).setPositiveButton("Start countdown",(d,w) -> {
                    if(!canSend())return;
                    playCountdown=new PlayCountdown(zap,strength,delay,SystemClock.elapsedRealtime());
                    playStatus.setText("Countdown running"); playCancel.setVisibility(View.VISIBLE);
                    updateControls(); handler.post(playTick);
                }).show();
    }
    private final Runnable playTick=new Runnable() {
        @Override public void run() {
            if(playCountdown==null)return;
            PlayCountdown plan=playCountdown; long now=SystemClock.elapsedRealtime();
            playNumber.setText(String.valueOf(plan.secondsLeft(now)));
            if(plan.take(now,ready&&sessionReady,shockEnabled.isChecked(),playConsent.isChecked(),isAlarmBusy()||stimulusInFlight)) {
                playCountdown=null; playCancel.setVisibility(View.GONE); stimulusInFlight=true;
                try {
                    byte[] data=plan.shock?LegacyProtocol.shock(plan.strength,firmware):LegacyProtocol.vibration(plan.strength,1,640,640);
                    playStatus.setText("Sending pulse…"); updateControls();
                    ble.write(UUID.fromString(plan.shock?LegacyProtocol.SHOCK:LegacyProtocol.VIBRATION),data,"Play: "+(plan.shock?"shock":"vibration")+" "+plan.strength+"%");
                } catch(IllegalArgumentException e){ stimulusInFlight=false; playStatus.setText(e.getMessage()); updateControls(); }
            } else if(!plan.isPending())cancelPlay("Cancelled. Nothing sent.");
            else handler.postDelayed(this,150);
        }
    };
    private void cancelPlay(String message) {
        if(playCountdown==null)return;
        playCountdown.cancel(); playCountdown=null; handler.removeCallbacks(playTick);
        if(playCancel!=null)playCancel.setVisibility(View.GONE);
        if(playNumber!=null)playNumber.setText("—"); if(playStatus!=null)playStatus.setText(message); updateControls();
    }
    private boolean canSend() {
        if(!ready||!sessionReady){ notice("Connect your wearable first."); return false; }
        if(isAlarmBusy()||stimulusInFlight||playCountdown!=null){ notice("Let the current action finish first."); return false; }
        return true;
    }
    private static final class AlarmSnapshot {
        final int hour,minute,kind,strength,repeat;
        AlarmSnapshot(int h,int m,int k,int s,int r){ hour=h;minute=m;kind=k;strength=s;repeat=r; }
        String summary(){ return String.format(Locale.ROOT,"%02d:%02d · %s · %s",hour,minute,kind==0?"Vibration":kind==1?"Beep":"Shock",repeat==0?"Once":"Every day"); }
    }

    private int intensity() { return (level.getProgress()+1)*10; }
    private int playIntensity() { return (playLevel.getProgress()+1)*10; }
    private void showTab(int tab) {
        if(tab!=2)cancelPlay("Cancelled. You left Play mode."); activeTab=tab;
        LinearLayout[] panels={controlPanel,alarmPanel,playPanel,diagnosisPanel};
        for(int i=0;i<panels.length;i++)panels[i].setVisibility(i==tab?View.VISIBLE:View.GONE);
        if(stickyAlarmSave!=null)stickyAlarmSave.setVisibility(tab==1?View.VISIBLE:View.GONE);
        updatePlayActions();
        for(int i=0;i<navItems.size();i++) {
            LinearLayout item=navItems.get(i); AppUi.surface(item,i==tab?AppUi.LIME:AppUi.WHITE,18,false);
            ((AppUi.Icon)item.getChildAt(0)).tint(i==tab?GREEN:MUTED);
            ((TextView)item.getChildAt(1)).setTextColor(i==tab?INK:MUTED); item.setSelected(i==tab);
        }
        if(scroll!=null)scroll.scrollTo(0,0);
    }
    private boolean isAlarmBusy() { return pendingAlarm!=null||expectedAlarm!=null||awaitingAlarmReadback||awaitingAlarmState; }
    private void updatePlayActions() {
        if(playStart!=null)playStart.setVisibility(activeTab==2&&playCountdown==null?View.VISIBLE:View.GONE);
        if(playCancel!=null)playCancel.setVisibility(activeTab==2&&playCountdown!=null?View.VISIBLE:View.GONE);
    }
    private void updateControls() {
        updatePlayActions();
        boolean enabled=ready&&sessionReady&&!isAlarmBusy()&&!stimulusInFlight&&playCountdown==null;
        for(View v:sessionControls){ v.setEnabled(enabled); v.setAlpha(enabled?1f:0.75f); }
        for(View v:editorControls)v.setEnabled(!isAlarmBusy()&&playCountdown==null);
        for(View v:emergencyControls){ boolean usable=ready&&sessionReady&&!isAlarmBusy(); v.setEnabled(usable); v.setAlpha(usable?1f:0.5f); }
        if(level!=null)level.setEnabled(!isAlarmBusy()&&!stimulusInFlight&&playCountdown==null);
        if(shockButton!=null){ boolean unlocked=enabled&&shockEnabled.isChecked(); shockButton.setEnabled(unlocked); shockButton.setAlpha(unlocked?1f:0.7f); }
        if(playMode!=null){ playMode.setEnabled(playCountdown==null); playDelay.setEnabled(playCountdown==null); playLevel.setEnabled(playCountdown==null); }
        if(connectionAction!=null)connectionAction.setText(ready?"Device":"Connect");
        if(alarmConnection!=null)alarmConnection.setText(isAlarmBusy()?"Saving. Keep your wearable close…":ready&&sessionReady?"Connected. Ready to save your wake-up.":ready?"Getting the wearable ready…":"Connect to save. You can edit the time first.");
    }
    private void requestScan() {
        if(isAlarmBusy()||stimulusInFlight||playCountdown!=null) { notice("Let the current action finish first, or cancel the countdown."); return; }
        String[] permissions = Build.VERSION.SDK_INT>=31 ? new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT} : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        for(String p:permissions) if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(permissions,100); return; }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if(adapter==null) { status.setText("Bluetooth unavailable on this device"); onLog("This phone/emulator has no Bluetooth adapter. Use a physical Android phone for wearable testing."); return; }
            if(!adapter.isEnabled()) { startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),101); return; }
            if(Build.VERSION.SDK_INT<=30) {
                LocationManager location=(LocationManager)getSystemService(LOCATION_SERVICE);
                if(location!=null&&!location.isProviderEnabled(LocationManager.GPS_PROVIDER)&&!location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    notice("Android 6–11 requires the system Location toggle for Bluetooth scans. Turn on Location in phone Settings, then try Find device again. The app does not read your location."); return;
                }
            }
            seen.clear(); devices.removeAllViews(); status.setText("Looking for your wearable…"); ble.scan();
        } catch(SecurityException e) { status.setText("Bluetooth permission is needed"); onLog("Bluetooth permission was denied or revoked; try Find device again."); }
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] results) {
        super.onRequestPermissionsResult(r,p,results);
        if(r==100) { for(int result:results) if(result!=PackageManager.PERMISSION_GRANTED) { status.setText("Bluetooth permission is needed to find the wearable"); return; } requestScan(); }
    }
    private void startSession() {
        sessionRequested=true;
        if(!ready) { notice("Connect to your Shock Clock first."); return; }
        if(sessionReady) { notice("Local session is already ready."); return; }
        if(!LegacyProtocol.isSupportedLegacyFirmware(firmware)) { notice("This device needs the legacy A4.x.y or 2.4.x profile. Refresh device info on Device to check firmware."); return; }
        if(!LegacyProtocol.isShockClockFirmware(firmware)) {
            sessionReady=true; status.setText("Connected"); updateControls(); updateDeviceSummary();
            onLog("Legacy 2.4 firmware uses direct controls; A4-only handshake skipped. No activation or stimulus write was sent."); return;
        }
        try { ble.write(UUID.fromString(LegacyProtocol.CONNECTION),LegacyProtocol.connectionHandshake(serial),"Local handshake"); }
        catch(IllegalArgumentException e) { notice("Wait for the device serial number. Refresh device info on Device if needed."); }
    }
    private void pulse(boolean beep) {
        if(!canSend())return;
        try { byte[] data = beep ? LegacyProtocol.beep(intensity(),1,640,640) : LegacyProtocol.vibration(intensity(),1,640,640);
            stimulusInFlight=true; actionStatus.setText("Sending one "+(beep?"beep":"buzz")+" at "+intensity()+"%…"); updateControls();
            ble.write(UUID.fromString(beep?LegacyProtocol.BEEP:LegacyProtocol.VIBRATION),data,beep?"Beep once":"Vibrate once"); }
        catch(IllegalArgumentException e) { notice(e.getMessage()); }
    }
    private void shock() {
        if(!canSend())return;
        if(shockConfirmation!=null&&shockConfirmation.isShowing())return;
        if(!shockEnabled.isChecked()) { notice("Enable manual shock controls first."); return; }
        final int strength = intensity(); final byte[] data;
        try { data=LegacyProtocol.shock(strength,firmware); } catch(IllegalArgumentException e) { notice(e.getMessage()); return; }
        shockConfirmation=new AlertDialog.Builder(this).setTitle("One shock at "+strength+"%?").setMessage("Sends one shock immediately.")
                .setNegativeButton("Not now",null).setPositiveButton("Send one shock",(d,w) -> {
                    if(canSend()&&shockEnabled.isChecked()) { stimulusInFlight=true; actionStatus.setText("Sending one shock at "+strength+"%…"); updateControls(); ble.write(UUID.fromString(LegacyProtocol.SHOCK),data,"Shock once "+strength+"%"); }
                }).show();
    }
    private void writeRtc(byte[] bytes,String label) {
        if(!label.startsWith("Alarm:"))cancelPlay("Countdown cancelled. Alarm control takes priority.");
        if(isAlarmBusy()&&!label.startsWith("Alarm:")) { notice("Wait for the alarm save to finish."); return; }
        if(ready&&sessionReady) ble.write(UUID.fromString(LegacyProtocol.RTC),bytes,label);
    }
    private void confirmAlarm() {
        if(!canSend())return;
        if(alarmConfirmation!=null&&alarmConfirmation.isShowing())return;
        final Calendar alarm = Calendar.getInstance(); alarm.set(Calendar.HOUR_OF_DAY,hour); alarm.set(Calendar.MINUTE,minute); alarm.set(Calendar.SECOND,hour==0&&minute==0?4:0); alarm.set(Calendar.MILLISECOND,0);
        if(repeat.getSelectedItemPosition()==0&&alarm.getTimeInMillis()<=System.currentTimeMillis()) alarm.add(Calendar.DAY_OF_MONTH,1);
        final int repeatMask = repeat.getSelectedItemPosition()==0 ? 0x80 : 0x7f;
        final int kind = alarmKind.getSelectedItemPosition();
        if(kind==2&&!shockEnabled.isChecked()) { notice("Unlock shock controls on Remote first."); return; }
        final byte[] record;
        try {
            if(kind==2) record=LegacyProtocol.alarm(firmware,repeatMask,alarm,0,0,0,0,alarmIntensity(),false,10,false,false);
            else record=kind==1 ? LegacyProtocol.beepAlarm(firmware,repeatMask,alarm,alarmIntensity(),3) : LegacyProtocol.vibrationAlarm(firmware,repeatMask,alarm,alarmIntensity(),3);
        }
        catch(IllegalArgumentException e) { notice(e.getMessage()); return; }
        String time = String.format(Locale.ROOT,"%02d:%02d",hour,minute);
        String signal=kind==2?"shock":kind==1?"beep":"vibration";
        final AlarmSnapshot snapshot=new AlarmSnapshot(hour,minute,kind,alarmIntensity(),repeat.getSelectedItemPosition());
        alarmConfirmation=new AlertDialog.Builder(this).setTitle("Save alarm for "+time+"?")
                .setMessage(signal.substring(0,1).toUpperCase(Locale.ROOT)+signal.substring(1)+" at "+alarmIntensity()+"%, "+(repeatMask==0x80?"once":"every day")+". Replaces the alarms currently on your wearable.")
                .setNegativeButton("Keep editing",null).setPositiveButton("Replace and save",(d,w) -> { if(canSend()){ savingSnapshot=snapshot; saveDraft(); saveAlarm(record); } }).show();
    }
    private void saveAlarm(byte[] record) {
        if(!ready||!sessionReady) { notice("Reconnect and start a local session first."); return; }
        if(isAlarmBusy()) { notice("An alarm save is already in progress."); return; }
        alarmStatus.setText("Sending alarm. Keep the wearable connected…");
        pendingAlarm=record.clone();
        handler.postDelayed(readbackTimeout,30000);
        updateControls();
        ble.enableRtcNotifications();
    }
    private void sendAlarmRecord() {
        byte[] record=pendingAlarm; pendingAlarm=null;
        if(record==null||!ready||!sessionReady) return;
        expectedAlarm=record.clone();
        expectedRollover=LegacyProtocol.rolloverAlarm(firmware,Calendar.getInstance());
        updateControls();
        writeRtc(LegacyProtocol.clearAlarms(),"Alarm: clear old records");
    }
    private void failAlarm(String message) {
        pendingAlarm=null; expectedAlarm=null; expectedRollover=null; awaitingAlarmReadback=false; awaitingAlarmState=false;
        checkingStoredCount=false; checkingRtcIndex=false;
        handler.removeCallbacks(readbackTimeout); handler.removeCallbacks(activateAlarm);
        if(savingSnapshot!=null){ preferences.edit().putString("lastSaved","The last save did not finish. Check your wearable before relying on it.").putString("lastSavedShort","Last save incomplete").apply(); savingSnapshot=null; refreshSavedSummary(); }
        alarmStatus.setText(message); onLog(message); updateControls();
    }
    private void notice(String message) { new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",null).show(); }
    private void exportReport() {
        exportText="LibrePavlok 0.2.0 local report\n"+new Date()+"\n\n"+ble.diagnosticReport()+"\n\nSession log\n"+logs;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("text/plain"); i.addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_TITLE,"librepavlok-report.txt"); startActivityForResult(i,102);
    }
    @Override protected void onActivityResult(int r,int result,Intent data) {
        super.onActivityResult(r,result,data);
        if(r==101&&result==RESULT_OK) requestScan();
        if(r==102&&result==RESULT_OK&&data!=null&&data.getData()!=null) {
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())) { if(out==null) throw new java.io.IOException("No destination"); out.write(exportText.getBytes(StandardCharsets.UTF_8)); onLog("Local report saved."); }
            catch(Exception e) { notice("Could not save report: "+e.getMessage()); }
        }
    }
    @Override public void onDevice(BluetoothDevice device,int rssi) {
        try { if(!seen.add(device.getAddress())) return; String name=device.getName(); button(devices,(name==null?"Pavlok device":name)+"  ·  "+(rssi>=-65?"Nearby":"In range"),v -> connectChosen(device)); }
        catch(SecurityException e) { onLog("Bluetooth permission was revoked."); }
    }
    @Override public void onState(String value,boolean isReady) {
        ready=isReady;
        if(!ready) {
            cancelPlay("Cancelled. The connection was lost."); stimulusInFlight=false;
            if(shockEnabled!=null)shockEnabled.setChecked(false);
            sessionReady=false;
            if(isAlarmBusy()) failAlarm("Disconnected before alarm verification. Check the wearable before relying on this alarm.");
            updateControls();
        }
        if(status!=null)status.setText(ready?(sessionReady?"Connected":"Getting ready…"):value.startsWith("Connecting")||value.startsWith("Connected")?"Connecting…":value.contains("Permission")?"Permission needed":"Disconnected"); updateControls(); updateDeviceSummary();
        if(!ready&&value.startsWith("Disconnected")&&selectedDevice!=null&&!explicitDisconnect&&reconnectAttempts<2) {
            reconnectAttempts++; handler.removeCallbacks(reconnect);handler.postDelayed(reconnect,1500);
        }
    }
    @Override public void onValue(UUID uuid,byte[] value) {
        String u=uuid.toString(); String s=new String(value,StandardCharsets.UTF_8).replace("\u0000","").trim();
        if(u.equals(LegacyProtocol.FIRMWARE)) {
            firmware=s;
            if(ready&&sessionRequested&&!sessionReady&&LegacyProtocol.isSupportedLegacyFirmware(firmware)) startSession();
        }
        if(u.equals(LegacyProtocol.SERIAL)) serial=s;
        if(u.equals(LegacyProtocol.HARDWARE)) hardware=s;
        if(u.equals(LegacyProtocol.BATTERY)&&value.length>0) battery=(value[0]&255)+"%";
        if(u.equals(LegacyProtocol.RTC)&&(checkingStoredCount||checkingRtcIndex)&&LegacyProtocol.alarmInventoryCount(value)>=0) {
            if(LegacyProtocol.alarmInventoryCount(value)!=2) failAlarm("Wearable alarm count does not match the two saved records. Save verification failed.");
            else if(checkingStoredCount) {
                checkingStoredCount=false; verificationSlot=1; awaitingAlarmReadback=true;
                writeRtc(LegacyProtocol.readAlarm(1),"Alarm: verify stored slot 1");
            } else if(LegacyProtocol.alarmInventoryIndex(value)!=activationSlot) failAlarm("Wearable selected a different alarm slot. Save verification failed.");
            else {
                checkingRtcIndex=false; awaitingAlarmState=true;
                writeRtc(LegacyProtocol.readAlarmStatus(),"Alarm: verify armed state");
            }
        }
        if(u.equals(LegacyProtocol.RTC)&&awaitingAlarmReadback&&LegacyProtocol.matchesAlarmReadback(verificationSlot==1?expectedAlarm:expectedRollover,verificationSlot,value)) {
            onLog("Alarm slot "+verificationSlot+" storage verified; activation is still pending.");
            if(verificationSlot==1) {
                verificationSlot=2;
                writeRtc(LegacyProtocol.readAlarm(2),"Alarm: verify rollover slot 2");
            } else {
                awaitingAlarmReadback=false;
                alarmStatus.setText("Both alarm records verified. Arming the wearable clock…");
                writeRtc(LegacyProtocol.clearRtc(),"Alarm: reset RTC selection");
            }
        }
        if(u.equals(LegacyProtocol.RTC)&&awaitingAlarmState) {
            int state=LegacyProtocol.alarmState(value);
            if(state==1) {
                expectedAlarm=null; expectedRollover=null; awaitingAlarmState=false; handler.removeCallbacks(readbackTimeout);
                if(savingSnapshot!=null){
                    String summary=savingSnapshot.summary(); preferences.edit().putString("lastSavedShort",summary).putString("lastSaved","Last verified on your wearable: "+summary+" · "+savingSnapshot.strength+"%.").apply();
                    savingSnapshot=null; refreshSavedSummary();
                }
                alarmStatus.setText("Saved and armed. Your wearable has the wake-up now.");
                onLog("Both stored records, selected slot and armed state verified."); updateControls();
            } else if(state==0||state==2) {
                failAlarm(state==0?"Alarm stored, but the wearable reports NOT ARMED. Saving failed; this alarm will not wake you.":"Wearable reports an alarm already firing. Stop it and choose a future time.");
            }
        }
        if(u.equals(LegacyProtocol.RTC)&&!isAlarmBusy()&&LegacyProtocol.alarmState(value)==2)alarmStatus.setText("Your alarm is ringing. Stop or snooze below.");
        updateDeviceSummary();
    }
    private void updateDeviceSummary() {
        if(info!=null)info.setText(ready?(sessionReady?"Ready for controls and alarms":"Starting local session…"):"Connect your wearable");
        if(batteryBadge!=null){ batteryBadge.setText(battery); batteryBadge.setVisibility(ready&&!battery.equals("—")?View.VISIBLE:View.GONE); }
        if(deviceInfo!=null&&ready)deviceInfo.setText("Battery "+battery+"\nFirmware "+(firmware.isEmpty()?"Reading…":firmware)+" · Hardware "+hardware+"\n"+(sessionReady?"Connected and ready.":"Getting ready…"));
    }
    @Override public void onWriteComplete(UUID uuid,String label,boolean success) {
        boolean stimulus=label.equals("Vibrate once")||label.equals("Beep once")||label.startsWith("Shock once")||label.startsWith("Play:");
        if(stimulus) {
            stimulusInFlight=false;
            actionStatus.setText(success?"Pulse sent.":"Could not send. Reconnect and try again.");
            if(label.startsWith("Play:")){ playNumber.setText("—"); playStatus.setText(success?"Pulse sent.":"Could not send. Check the connection."); }
            updateControls();
        }
        if(label.equals("Local handshake")) { sessionReady=success; if(success) status.setText("Connected"); updateControls(); updateDeviceSummary(); }
        if(!success) { sessionReady=false; cancelPlay("Cancelled. The connection needs attention."); if(isAlarmBusy())failAlarm("Save interrupted. The wearable may have a partial alarm. Reconnect and save again."); else { actionStatus.setText("That didn’t complete. Reconnect and try again."); updateControls(); } return; }
        if(label.equals("Enable RTC notifications")&&success) sendAlarmRecord();
        if(expectedAlarm!=null&&label.equals("Alarm: clear old records")) writeRtc(LegacyProtocol.clock(Calendar.getInstance()),"Alarm: set local clock");
        if(expectedAlarm!=null&&label.equals("Alarm: set local clock")) writeRtc(expectedAlarm,"Alarm: append record");
        if(expectedAlarm!=null&&label.equals("Alarm: append record")) {
            writeRtc(expectedRollover,"Alarm: append rollover record");
        }
        if(expectedAlarm!=null&&label.equals("Alarm: append rollover record")) {
            checkingStoredCount=true;
            alarmStatus.setText("Checking both wearable alarm records…");
            writeRtc(LegacyProtocol.readAlarmInventory(),"Alarm: verify stored count");
        }
        if(expectedAlarm!=null&&label.equals("Alarm: reset RTC selection")) handler.postDelayed(activateAlarm,600);
        if(label.equals("Alarm: activate selected slot")&&success) {
            if(expectedAlarm==null) return;
            checkingRtcIndex=true;
            alarmStatus.setText("Checking that the wearable armed this alarm…");
            writeRtc(LegacyProtocol.readAlarmInventory(),"Alarm: verify selected slot");
        }
        if(label.equals("Clear alarms")&&success){ preferences.edit().remove("lastSaved").remove("lastSavedShort").apply(); refreshSavedSummary(); alarmStatus.setText("All clear. The alarm records were removed."); }
    }
    @Override public void onLog(String message) {
        String line=String.format(Locale.ROOT,"%tT  %s\n",new Date(),message); logs.append(line); if(logs.length()>24000) logs.delete(0,logs.length()-24000);
        if(logView!=null) logView.setText(logs.toString()); android.util.Log.i("LibrePavlok",message);
        if(status!=null&&!ready&&message.equals("Scan finished.")) status.setText(seen.isEmpty()?"No wearable found":"Pick your wearable");
    }
    @Override protected void onPause(){ cancelPlay("Paused. Nothing sent."); saveDraft(); super.onPause(); }
    @Override public void onBackPressed(){ if(playCountdown!=null){ cancelPlay("Cancelled. Nothing sent."); return; } if(activeTab!=0){ showTab(0); return; } super.onBackPressed(); }
    @Override protected void onDestroy() { cancelPlay("Cancelled. Nothing sent."); handler.removeCallbacksAndMessages(null); if(ble!=null) ble.close(); super.onDestroy(); }

    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private LinearLayout card(LinearLayout parent) {
        return AppUi.card(parent,AppUi.WHITE);
    }
    private LinearLayout row(LinearLayout parent) { return AppUi.row(parent); }
    private TextView text(LinearLayout parent,String message,int size,int color,boolean bold) {
        return AppUi.text(parent,message,size,color,bold);
    }
    private Button button(LinearLayout parent,String label,View.OnClickListener click) {
        return button(parent,label,AppUi.SOFT,INK,click);
    }
    private Button button(LinearLayout parent,String label,int fill,int ink,View.OnClickListener click){ return AppUi.button(parent,label,fill,ink,click); }
    private Button sessionButton(LinearLayout parent,String title,View.OnClickListener click) { return sessionButton(parent,title,AppUi.LIME,INK,click); }
    private Button sessionButton(LinearLayout parent,String title,int fill,int ink,View.OnClickListener click){ Button b=button(parent,title,fill,ink,click); sessionControls.add(b); return b; }
    private Spinner spinner(LinearLayout parent,String... items) {
        Spinner s=new Spinner(this); ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,items){
            @Override public View getView(int position,View convert,android.view.ViewGroup group){ TextView t=(TextView)super.getView(position,convert,group); t.setTextColor(INK); t.setTextSize(14); t.setPadding(dp(12),0,dp(8),0); return t; }
        }; a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(a); AppUi.surface(s,AppUi.SOFT,14,false);
        parent.addView(s,new LinearLayout.LayoutParams(-1,dp(49))); return s;
    }
}
