package org.librepavlok.offline;

/** Persisted one-shot plan. Claim an event before attempting any external action. */
final class TodoItem {
    static final long MAX_LATENESS_MS=60000;
    final String id;
    String title, outcome="";
    long dueAt;
    int reminderMinutes, strength;
    boolean completed, reminded, handled;

    TodoItem(String id,String title,long dueAt,int reminderMinutes,int strength) {
        if(id==null||id.isEmpty()||title==null||title.trim().isEmpty()||title.trim().length()>160)
            throw new IllegalArgumentException("Enter a title (up to 160 characters).");
        if(dueAt<=0||reminderMinutes<0||reminderMinutes>10080||strength<10||strength>100||strength%10!=0)
            throw new IllegalArgumentException("Check the deadline, reminder and strength.");
        this.id=id; this.title=title.trim(); this.dueAt=dueAt;
        this.reminderMinutes=reminderMinutes; this.strength=strength;
    }
    long reminderAt(){ return dueAt-reminderMinutes*60000L; }
    boolean reminderDue(long now){ return !completed&&!handled&&!reminded&&reminderMinutes>0&&now>=reminderAt()&&now<dueAt; }
    boolean deadlineDue(long now){ return !completed&&!handled&&now>=dueAt; }
    boolean canShock(long now){ return deadlineDue(now)&&now-dueAt<=MAX_LATENESS_MS; }
    long nextEvent(long now) {
        if(completed||handled)return Long.MAX_VALUE;
        if(reminderMinutes>0&&!reminded&&reminderAt()>now)return reminderAt();
        return dueAt;
    }
}
