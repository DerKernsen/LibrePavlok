package org.librepavlok.offline;

import java.util.Calendar;
import java.util.GregorianCalendar;

public final class PulsePlanTest {
    private static int checks;
    private static void check(boolean value){ checks++; if(!value)throw new AssertionError("Pulse check "+checks); }
    private static void bytes(String expected,byte[] actual){ check(expected.equals(LegacyProtocol.hex(actual))); }
    private static void reject(Runnable action){ try{ action.run(); throw new AssertionError("Expected invalid pulse rejection"); }catch(IllegalArgumentException expected){ checks++; } }
    public static void main(String[] args) {
        bytes("83 FF 64 20",new PulsePlan(PulsePlan.VIBRATION,100,2000,3).command("2.4.99"));
        bytes("87 FF FF 20",new PulsePlan(PulsePlan.BEEP,100,5100,7).command("2.4.99"));
        bytes("81 FF 07 00",new PulsePlan(PulsePlan.SHOCK,100,640,7).command("2.4.99"));
        bytes("81 20 01 00",new PulsePlan(PulsePlan.SHOCK,10,5100,1).command("A4.4.85"));
        check(new PulsePlan(PulsePlan.SHOCK,10,5100,1).description().contains("fixed pulse width"));
        check(!new PulsePlan(PulsePlan.SHOCK,10,5100,1).description().contains("5100"));
        check(new PulsePlan(PulsePlan.BEEP,10,5100,7).description().contains("5100 ms each"));
        Calendar date=new GregorianCalendar(2026,Calendar.OCTOBER,15,10,30,0);
        for(String firmware:new String[]{"A4.4.85","A4.4.86","2.4.99"}) {
            for(int signal=0;signal<3;signal++)for(int count=1;count<=7;count++) {
                PulsePlan p=new PulsePlan(signal,100,5100,count);
                byte[] record=p.alarm(firmware,128,date);
                int field=signal==PulsePlan.SHOCK?8:signal==PulsePlan.VIBRATION?9:10;
                check((record[field]&255)==(0xc0|count));
                byte[] response=new byte[record.length+1]; response[0]=6;response[1]=1;
                System.arraycopy(record,1,response,2,record.length-1);
                check(LegacyProtocol.matchesAlarmReadback(record,1,response));
            }
        }
        for(int count=1;count<=7;count++) {
            check((LegacyProtocol.shock(50,"2.4.99",count)[2]&255)==count);
            check(LegacyProtocol.hex(new PulsePlan(PulsePlan.SHOCK,50,20,count).command("2.4.99")).equals(LegacyProtocol.hex(new PulsePlan(PulsePlan.SHOCK,50,5100,count).command("2.4.99"))));
        }
        PlayCountdown captured=new PlayCountdown(new PulsePlan(PulsePlan.BEEP,100,5100,7),3,0);
        check(!captured.shock&&captured.pulse.durationMs==5100&&captured.pulse.count==7);
        check(captured.take(3000,true,false,false,false));
        check(!captured.take(3001,true,false,false,false));
        TodoItem t=new TodoItem("task","Timing",100000,0,new PulsePlan(PulsePlan.VIBRATION,30,2000,4));
        check(t.signal==PulsePlan.VIBRATION&&t.durationMs==2000&&t.pulseCount==4);
        bytes("84 5F 64 20",t.pulse().command("2.4.99"));
        TodoItem legacy=new TodoItem("old","Previous task",100000,0,100);
        check(legacy.signal==PulsePlan.SHOCK&&legacy.pulseCount==1&&legacy.durationMs==640);
        reject(()->new PulsePlan(-1,10,640,1)); reject(()->new PulsePlan(3,10,640,1));
        reject(()->new PulsePlan(0,0,640,1)); reject(()->new PulsePlan(0,15,640,1));
        reject(()->new PulsePlan(0,10,0,1)); reject(()->new PulsePlan(0,10,21,1));
        reject(()->new PulsePlan(0,10,5120,1)); reject(()->new PulsePlan(1,10,640,0));
        reject(()->new PulsePlan(1,10,640,8));
        reject(()->LegacyProtocol.shock(10,"2.4.99",0)); reject(()->LegacyProtocol.shock(10,"2.4.99",8));
        reject(()->LegacyProtocol.alarm("2.4.99",128,date,0,0,0,0,10,0,false,10,false,false));
        reject(()->LegacyProtocol.alarm("2.4.99",128,date,0,0,0,0,10,8,false,10,false,false));
        System.out.println("PulsePlanTest: "+checks+" checks passed");
    }
}
