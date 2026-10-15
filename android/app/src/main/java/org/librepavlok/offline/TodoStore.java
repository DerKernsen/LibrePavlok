package org.librepavlok.offline;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TodoStore {
    private final SharedPreferences prefs;
    TodoStore(Context context){ prefs=context.getSharedPreferences("todos",Context.MODE_PRIVATE); }
    boolean enabled(){ return prefs.getBoolean("enabled",false); }
    boolean setEnabled(boolean enabled){ return prefs.edit().putBoolean("enabled",enabled).commit(); }
    List<TodoItem> load() {
        ArrayList<TodoItem> result=new ArrayList<>();
        try {
            JSONArray rows=new JSONArray(prefs.getString("items","[]"));
            for(int i=0;i<rows.length();i++) {
                JSONObject j=rows.getJSONObject(i);
                PulsePlan pulse=new PulsePlan(j.optInt("signal",PulsePlan.SHOCK),j.getInt("strength"),j.optInt("durationMs",640),j.optInt("pulseCount",1));
                TodoItem t=new TodoItem(j.getString("id"),j.getString("title"),j.getLong("due"),j.getInt("reminder"),pulse);
                t.completed=j.optBoolean("completed"); t.reminded=j.optBoolean("reminded"); t.handled=j.optBoolean("handled"); t.outcome=j.optString("outcome");
                result.add(t);
            }
        } catch(Exception e){ throw new IllegalStateException("Could not read the saved to-do list. Monitoring is stopped.",e); }
        Collections.sort(result,(a,b) -> a.completed!=b.completed?(a.completed?1:-1):Long.compare(a.dueAt,b.dueAt));
        return result;
    }
    boolean save(List<TodoItem> items) {
        JSONArray rows=new JSONArray();
        try {
            for(TodoItem t:items) {
                JSONObject j=new JSONObject(); j.put("id",t.id); j.put("title",t.title); j.put("due",t.dueAt);
                j.put("reminder",t.reminderMinutes); j.put("strength",t.strength); j.put("completed",t.completed);
                j.put("signal",t.signal); j.put("durationMs",t.durationMs); j.put("pulseCount",t.pulseCount);
                j.put("reminded",t.reminded); j.put("handled",t.handled); j.put("outcome",t.outcome); rows.put(j);
            }
        } catch(Exception e){ return false; }
        return prefs.edit().putString("items",rows.toString()).commit();
    }
    static boolean hasPending(List<TodoItem> items){ for(TodoItem t:items)if(!t.completed&&!t.handled)return true; return false; }
}
