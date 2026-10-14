package org.librepavlok.offline;

public final class PlayCountdownTest {
    private static int checks;
    public static void main(String[] args) {
        PlayCountdown practice=new PlayCountdown(false,50,5,1000);
        check(practice.secondsLeft(1000)==5);
        check(practice.secondsLeft(1001)==5);
        check(!practice.take(5999,true,false,false,false));
        check(practice.take(6000,true,false,false,false));
        check(!practice.take(6001,true,true,true,false));
        PlayCountdown cancelled=new PlayCountdown(true,100,3,0);
        check(cancelled.strength==100); cancelled.cancel();
        check(!cancelled.take(3000,true,true,true,false));
        PlayCountdown disconnected=new PlayCountdown(true,20,3,0);
        check(!disconnected.take(1000,false,true,true,false));
        check(!disconnected.take(3000,true,true,true,false));
        PlayCountdown locked=new PlayCountdown(true,20,3,0);
        check(!locked.take(3000,true,false,true,false));
        PlayCountdown noConsent=new PlayCountdown(true,20,3,0);
        check(!noConsent.take(3000,true,true,false,false));
        PlayCountdown busy=new PlayCountdown(false,10,3,0);
        check(!busy.take(3000,true,true,true,true));
        PlayCountdown shock=new PlayCountdown(true,10,8,0);
        check(shock.strength==10); check(shock.take(8000,true,true,true,false));
        PlayCountdown fullStrength=new PlayCountdown(true,100,3,0);
        check(fullStrength.strength==100); check(fullStrength.take(3000,true,true,true,false));
        rejects(() -> new PlayCountdown(true,0,3,0));
        rejects(() -> new PlayCountdown(false,15,3,0));
        rejects(() -> new PlayCountdown(false,101,3,0));
        rejects(() -> new PlayCountdown(false,10,2,0));
        rejects(() -> new PlayCountdown(false,10,9,0));
        System.out.println("PlayCountdownTest: "+checks+" checks passed");
    }
    private static void check(boolean value){ checks++; if(!value)throw new AssertionError("Check "+checks); }
    private static void rejects(Runnable action){ checks++; try{ action.run(); }catch(IllegalArgumentException expected){ return; } throw new AssertionError("Expected rejection"); }
}
