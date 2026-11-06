package cn.shenlink.guard;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;
public class CalendarReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        try{String id=intent.getStringExtra("id");JSONArray tasks=new JSONObject(c.getSharedPreferences("linkday",Context.MODE_PRIVATE).getString("data","{}")).optJSONArray("tasks");if(tasks==null)return;
            for(int i=0;i<tasks.length();i++){JSONObject t=tasks.getJSONObject(i);if(!t.optString("id").equals(id)||t.optBoolean("done")||t.isNull("remind"))continue;
                long due=CalendarReminders.due(t),expected=due-t.optLong("remind",0)*60000L;if(expected>System.currentTimeMillis()+5000||due+3600000<System.currentTimeMillis())return;
                android.content.SharedPreferences prefs=c.getSharedPreferences("linkday",Context.MODE_PRIVATE);
                JSONObject delivered=new JSONObject(prefs.getString("delivered","{}"));if(delivered.optLong(id,-1)==expected)return;
                CalendarReminders.channel(c);if(!Notifications.manager(c).areNotificationsEnabled()||Notifications.manager(c).getNotificationChannel(CalendarReminders.CHANNEL).getImportance()==0)return;
                String body=("exam".equals(t.optString("type"))?"考试时间：":"截止时间：")+t.optString("at").replace('T',' ')+"（深圳 / 香港时间）";
                if(!t.optString("place").isEmpty())body+=" · "+t.optString("place");
                Notification n=new Notification.Builder(c,CalendarReminders.CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(t.optString("title")).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(Notifications.home(c,120)).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_REMINDER).build();Notifications.manager(c).notify("deadline:"+id,120,n);delivered.put(id,expected);prefs.edit().putString("delivered",delivered.toString()).apply();return;
            }
        }catch(Exception ignored){}
    }
}
