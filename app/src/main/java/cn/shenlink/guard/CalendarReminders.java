package cn.shenlink.guard;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.OffsetDateTime;
final class CalendarReminders {
    static final String CHANNEL="calendar_reminders";
    static void channel(Context c){Notifications.manager(c).createNotificationChannel(new NotificationChannel(CHANNEL,"作业与考试提醒",NotificationManager.IMPORTANCE_HIGH));}
    static long due(JSONObject task){try{return OffsetDateTime.parse(task.getString("at")+":00+08:00").toInstant().toEpochMilli();}catch(Exception ex){return -1;}}
    static PendingIntent pending(Context c,String id){return PendingIntent.getBroadcast(c,0,new Intent(c,CalendarReceiver.class).setAction("deadline").setData(Uri.parse("linkday://deadline/"+Uri.encode(id))).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static synchronized void sync(Context c){
        channel(c);android.content.SharedPreferences prefs=c.getSharedPreferences("linkday",Context.MODE_PRIVATE);AlarmManager alarm=c.getSystemService(AlarmManager.class);
        try{JSONArray old=new JSONArray(prefs.getString("alarms","[]"));for(int i=0;i<old.length();i++)alarm.cancel(pending(c,old.getString(i)));
            JSONArray next=new JSONArray(),tasks=new JSONObject(prefs.getString("data","{}")).optJSONArray("tasks");
            JSONObject delivered=new JSONObject(prefs.getString("delivered","{}"));
            if(tasks!=null)for(int i=0;i<tasks.length();i++){JSONObject task=tasks.getJSONObject(i);String id=task.getString("id");if(task.optBoolean("done")||task.isNull("remind")){Notifications.manager(c).cancel("deadline:"+id,120);continue;}long deadline=due(task),at=deadline-task.optLong("remind",0)*60000L;if(deadline+3600000<System.currentTimeMillis()||delivered.optLong(id,-1)==at)continue;alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,Math.max(at,System.currentTimeMillis()+1000),pending(c,id));next.put(id);}
            prefs.edit().putString("alarms",next.toString()).apply();
        }catch(Exception ignored){}
    }
}
