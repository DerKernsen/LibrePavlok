package org.librepavlok.offline;

/** A cancellable, one-use plan; it never communicates with a wearable itself. */
public final class PlayCountdown {
    public final boolean shock;
    public final int strength;
    public final PulsePlan pulse;
    private final long deadline;
    private boolean pending=true;

    public PlayCountdown(boolean shock,int requestedStrength,int delaySeconds,long now) {
        this(new PulsePlan(shock?PulsePlan.SHOCK:PulsePlan.VIBRATION,requestedStrength,640,1),delaySeconds,now);
    }
    public PlayCountdown(PulsePlan pulse,int delaySeconds,long now) {
        if(pulse==null)throw new IllegalArgumentException("Choose a pulse.");
        if(delaySeconds<3||delaySeconds>8)throw new IllegalArgumentException("Delay must be 3–8 seconds");
        this.pulse=pulse; this.shock=pulse.signal==PulsePlan.SHOCK; strength=pulse.strength;
        deadline=now+delaySeconds*1000L;
    }
    public boolean isPending(){ return pending; }
    public void cancel(){ pending=false; }
    public int secondsLeft(long now){ return pending?(int)Math.max(0,(deadline-now+999)/1000):0; }
    /** Claims exactly one command, only if the current connection and consent still allow it. */
    public boolean take(long now,boolean connected,boolean shockUnlocked,boolean wearerAgrees,boolean busy) {
        if(!pending)return false;
        if(!connected||busy||(shock&&(!shockUnlocked||!wearerAgrees))){ cancel(); return false; }
        if(now<deadline)return false;
        pending=false; return true;
    }
}
