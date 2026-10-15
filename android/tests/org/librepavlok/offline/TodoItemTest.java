package org.librepavlok.offline;

public final class TodoItemTest {
    private static int checks;
    private static void check(boolean value,String message){ checks++; if(!value)throw new AssertionError(message); }
    private static TodoItem task(){ return new TodoItem("one","Task",10_000_000,30,100); }
    private static void invalid(Runnable make){ try{ make.run(); throw new AssertionError("Accepted invalid task"); }catch(IllegalArgumentException expected){ checks++; } }
    public static void main(String[] args) {
        TodoItem t=task(); long due=t.dueAt,reminder=due-30*60000L;
        check(t.reminderAt()==reminder,"30-minute offset");
        check(t.nextEvent(1)==reminder,"Reminder is first event");
        check(!t.reminderDue(reminder-1),"No early reminder");
        check(t.reminderDue(reminder),"Reminder at exact boundary");
        check(t.reminderDue(due-1),"Late reminder before deadline");
        check(!t.reminderDue(due),"No advance reminder at deadline");
        check(!t.deadlineDue(due-1),"No early deadline");
        check(t.deadlineDue(due),"Deadline at boundary");
        check(t.canShock(due),"Shock at deadline");
        check(t.canShock(due+60000),"Bounded scheduling tolerance");
        check(!t.canShock(due+60001),"No catch-up shock after tolerance");
        check(t.strength==100,"Full strength preserved");
        t.reminded=true;
        check(!t.reminderDue(reminder),"Claimed reminder cannot repeat");
        check(t.nextEvent(reminder)==due,"Next deadline after reminder");
        t.handled=true;
        check(!t.deadlineDue(due),"Claimed deadline cannot repeat");
        check(!t.canShock(due),"No repeated shock");
        check(t.nextEvent(1)==Long.MAX_VALUE,"Handled task unscheduled");
        t=task(); t.completed=true;
        check(!t.reminderDue(reminder),"Done cancels reminder");
        check(!t.deadlineDue(due),"Done cancels deadline");
        check(!t.canShock(due),"Done cancels shock");
        check(t.nextEvent(1)==Long.MAX_VALUE,"Done unscheduled");
        t=task(); t.reminderMinutes=0;
        check(!t.reminderDue(due-1),"Reminder off");
        check(t.nextEvent(1)==due,"No reminder event when off");
        check(new TodoItem("x","  Trim me  ",due,10080,10).title.equals("Trim me"),"Trim title and accept bounds");
        TodoItem a=task(), b=task(); a.completed=true;
        check(b.canShock(due),"Tasks complete independently");
        a=task(); a.handled=true;
        check(b.canShock(due),"Tasks claim independently");
        invalid(()->new TodoItem("","Task",due,0,10));
        invalid(()->new TodoItem("x"," ",due,0,10));
        invalid(()->new TodoItem("x",null,due,0,10));
        invalid(()->new TodoItem("x",new String(new char[161]).replace('\0','a'),due,0,10));
        invalid(()->new TodoItem("x","Task",0,0,10));
        invalid(()->new TodoItem("x","Task",due,-1,10));
        invalid(()->new TodoItem("x","Task",due,10081,10));
        invalid(()->new TodoItem("x","Task",due,0,0));
        invalid(()->new TodoItem("x","Task",due,0,110));
        invalid(()->new TodoItem("x","Task",due,0,15));
        System.out.println("TodoItemTest: "+checks+" checks passed");
    }
}
