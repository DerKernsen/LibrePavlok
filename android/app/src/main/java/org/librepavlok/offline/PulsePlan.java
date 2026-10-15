package org.librepavlok.offline;

import java.util.Calendar;

/** Immutable settings captured before confirmation/countdown/background dispatch. */
public final class PulsePlan {
    public static final int VIBRATION=0, SHOCK=1, BEEP=2, DEFAULT_DURATION_MS=640;
    public final int signal, strength, durationMs, count;
    public PulsePlan(int signal,int strength,int durationMs,int count) {
        if(signal<0||signal>2)throw new IllegalArgumentException("Choose vibration, shock or beep.");
        if(strength<10||strength>100||strength%10!=0)throw new IllegalArgumentException("Choose strength in steps of ten.");
        if(durationMs<20||durationMs>5100||durationMs%20!=0)throw new IllegalArgumentException("Pulse duration must be 20–5100 ms, in 20 ms steps.");
        if(count<1||count>7)throw new IllegalArgumentException("Choose 1–7 pulses.");
        this.signal=signal; this.strength=strength; this.durationMs=durationMs; this.count=count;
    }
    public String name(){ return signal==SHOCK?"shock":signal==BEEP?"beep":"vibration"; }
    public String characteristic(){ return signal==SHOCK?LegacyProtocol.SHOCK:signal==BEEP?LegacyProtocol.BEEP:LegacyProtocol.VIBRATION; }
    public String timing(){ return count+" "+name()+(count==1?"":"s")+(signal==SHOCK?" · fixed pulse width":" · "+durationMs+" ms each"); }
    public String description(){ return timing()+" at "+strength+"%"; }
    public byte[] command(String firmware) {
        if(signal==SHOCK)return LegacyProtocol.shock(strength,firmware,count);
        return signal==BEEP?LegacyProtocol.beep(strength,count,durationMs,640):LegacyProtocol.vibration(strength,count,durationMs,640);
    }
    public byte[] alarm(String firmware,int repeatMask,Calendar time) {
        if(signal==SHOCK)return LegacyProtocol.alarm(firmware,repeatMask,time,0,0,0,0,strength,count,false,10,false,false);
        return signal==BEEP?LegacyProtocol.beepAlarm(firmware,repeatMask,time,strength,count):LegacyProtocol.vibrationAlarm(firmware,repeatMask,time,strength,count);
    }
}
