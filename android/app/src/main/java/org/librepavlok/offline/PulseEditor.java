package org.librepavlok.offline;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Reusable native controls. Stored alarms and shocks expose counts, never fictitious seconds. */
final class PulseEditor {
    final LinearLayout root, durationRow;
    final SeekBar duration, count;
    private final TextView durationLabel, countLabel, explanation;
    private final boolean onboard;
    private int signal;
    Runnable onChange;
    PulseEditor(LinearLayout parent,String title,int signal,boolean onboard,int durationMs,int repeats) {
        this.signal=signal; this.onboard=onboard;
        root=AppUi.column(parent.getContext()); parent.addView(root,new LinearLayout.LayoutParams(-1,-2));
        AppUi.text(root,title,15,AppUi.INK,true);
        durationRow=AppUi.column(parent.getContext()); root.addView(durationRow);
        durationLabel=AppUi.text(durationRow,"",13,AppUi.GREEN,true);
        duration=new SeekBar(parent.getContext()); duration.setMax(254); duration.setProgress(Math.max(0,Math.min(254,durationMs/20-1)));
        duration.setContentDescription(title+" duration in 20 millisecond steps"); durationRow.addView(duration,new LinearLayout.LayoutParams(-1,AppUi.dp(parent.getContext(),48)));
        countLabel=AppUi.text(root,"",13,AppUi.GREEN,true);
        count=new SeekBar(parent.getContext()); count.setMax(6); count.setProgress(Math.max(0,Math.min(6,repeats-1)));
        count.setContentDescription(title+" repeat count"); root.addView(count,new LinearLayout.LayoutParams(-1,AppUi.dp(parent.getContext(),48)));
        explanation=AppUi.text(root,"",12,AppUi.MUTED,false);
        SeekBar.OnSeekBarChangeListener listener=new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){ refresh(); }
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        };
        duration.setOnSeekBarChangeListener(listener); count.setOnSeekBarChangeListener(listener); refresh();
    }
    static PulseEditor remembered(LinearLayout parent,String title,int signal,boolean onboard,SharedPreferences prefs,String key,int defaultCount) {
        return new PulseEditor(parent,title,signal,onboard,prefs.getInt(key+"DurationMs",640),prefs.getInt(key+"Count",defaultCount));
    }
    int durationMs(){ return (duration.getProgress()+1)*20; }
    int count(){ return count.getProgress()+1; }
    PulsePlan snapshot(int strength){ return new PulsePlan(signal,strength,durationMs(),count()); }
    void setSignal(int signal){ this.signal=signal; refresh(); }
    void save(SharedPreferences.Editor prefs,String key){ prefs.putInt(key+"DurationMs",durationMs()).putInt(key+"Count",count()); }
    void enabled(boolean enabled){ duration.setEnabled(enabled); count.setEnabled(enabled); }
    private void refresh() {
        durationRow.setVisibility(onboard||signal==PulsePlan.SHOCK?View.GONE:View.VISIBLE);
        durationLabel.setText("Pulse duration: "+durationMs()+" ms");
        countLabel.setText("Repeat: "+count()+" "+(signal==PulsePlan.SHOCK?"shock":"pulse")+(count()==1?"":"s"));
        explanation.setText(onboard?"Your wearable controls pulse width and cadence. This sets repetitions per alarm cycle.":signal==PulsePlan.SHOCK?"Shock width and cadence are fixed by the wearable. More repeats make a longer burst.":"Each pulse uses the duration above, with 640 ms between pulses.");
        if(onChange!=null)onChange.run();
    }
}
