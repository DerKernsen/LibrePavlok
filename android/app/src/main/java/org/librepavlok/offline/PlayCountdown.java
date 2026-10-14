package org.librepavlok.offline;

/** A cancellable, one-use plan; it never communicates with a wearable itself. */
public final class PlayCountdown {
    public final boolean shock;
    public final int strength;
    private final long deadline;
    private boolean pending=true;

    public PlayCountdown(boolean shock,int requestedStrength,int delaySeconds,long now) {
        if(requestedStrength<10||requestedStrength>100||requestedStrength%10!=0)
            throw new IllegalArgumentException("Choose a strength in steps of ten");
        if(delaySeconds<3||delaySeconds>8)throw new IllegalArgumentException("Delay must be 3–8 seconds");
        this.shock=shock; strength=requestedStrength;
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
