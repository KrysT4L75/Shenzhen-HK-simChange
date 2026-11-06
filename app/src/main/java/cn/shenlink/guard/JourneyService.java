package cn.shenlink.guard;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** User-started location foreground service, independent of phone/SIM permissions. */
public class JourneyService extends Service implements LocationListener {
    static volatile boolean running;
    private static final String CHANNEL="journey_tracking";
    private static final int NOTICE=8;
    private final Handler main=new Handler(Looper.getMainLooper());
    private JourneyInference inference=new JourneyInference();
    private LocationManager manager;
    private long lastFix=-1,started;
    private boolean explicitStop;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("journey",MODE_PRIVATE);}
    static void markStopped(Context c){prefs(c).edit().putBoolean("wanted",false).putString("status","定位已停止").apply();}
    static JSONObject snapshot(Context c){
        JSONObject out=new JSONObject();try{SharedPreferences p=prefs(c);out.put("running",running);out.put("id",p.getString("id",""));out.put("status",!running&&p.getBoolean("wanted",false)?"定位已中断，请在行程中重新开启":p.getString("status","定位未开启"));out.put("nodes",new JSONArray(p.getString("nodes","[]")));}catch(Exception ignored){}return out;
    }
    @Override public void onCreate(){super.onCreate();manager=getSystemService(LocationManager.class);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("stop".equals(intent.getAction())){explicitStop=true;markStopped(this);stopSelf();return START_NOT_STICKY;}
        String id=intent.getStringExtra("id");if(id==null||id.isEmpty()||!PhoneState.allowed(this,Manifest.permission.ACCESS_COARSE_LOCATION)){stopSelf();return START_NOT_STICKY;}
        boolean same=id.equals(prefs(this).getString("id",""));if(running&&same)return START_NOT_STICKY;
        SharedPreferences.Editor editor=prefs(this).edit().putString("id",id).putBoolean("wanted",true);if(!same)editor.putString("nodes","[]");editor.apply();
        Notifications.manager(this).createNotificationChannel(new NotificationChannel(CHANNEL,"通勤定位状态",android.app.NotificationManager.IMPORTANCE_LOW));
        try{if(Build.VERSION.SDK_INT>=29)startForeground(NOTICE,notification("正在等待位置更新"),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(NOTICE,notification("正在等待位置更新"));}
        catch(RuntimeException ex){markStopped(this);prefs(this).edit().putString("status","定位启动失败，请检查位置权限").apply();stopSelf();return START_NOT_STICKY;}
        running=true;started=SystemClock.elapsedRealtime();inference=new JourneyInference();lastFix=-1;explicitStop=false;
        try{manager.removeUpdates(this);}catch(RuntimeException ignored){}
        boolean attached=false;for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
            if(provider.equals(LocationManager.GPS_PROVIDER)&&!PhoneState.allowed(this,Manifest.permission.ACCESS_FINE_LOCATION))continue;
            try{if(manager.getAllProviders().contains(provider)){manager.requestLocationUpdates(provider,15000,0,this,Looper.getMainLooper());attached=true;}}catch(RuntimeException ignored){}
        }
        if(!attached){explicitStop=true;markStopped(this);prefs(this).edit().putString("status","没有可用定位服务，请手动记录").apply();stopSelf();return START_NOT_STICKY;}
        status(PhoneState.allowed(this,Manifest.permission.ACCESS_FINE_LOCATION)?"等待新位置 · 交通方式待确认":"仅允许大致位置，节点识别可能不可用");
        main.removeCallbacks(pulse);main.postDelayed(pulse,30000);return START_NOT_STICKY;
    }
    private final Runnable pulse=new Runnable(){public void run(){
        if(!running)return;long now=SystemClock.elapsedRealtime();
        if(now-started>6*60*60*1000L){explicitStop=true;markStopped(JourneyService.this);prefs(JourneyService.this).edit().putString("status","定位已运行 6 小时并停止，请确认行程").apply();stopSelf();return;}
        if(!PhoneState.allowed(JourneyService.this,Manifest.permission.ACCESS_COARSE_LOCATION)){explicitStop=true;markStopped(JourneyService.this);stopSelf();return;}
        try{if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)&&!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))status("手机定位已关闭，行程可继续手动记录");else if(lastFix>=0&&now-lastFix>120000)status("位置暂时中断 · 请手动确认交通方式");}catch(RuntimeException ignored){}
        main.postDelayed(this,30000);
    }};
    private Notification notification(String text){
        PendingIntent stop=PendingIntent.getService(this,82,new Intent(this,JourneyService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("通勤助手 · 通勤定位中").setContentText(text).setContentIntent(Notifications.home(this,81)).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).addAction(new Notification.Action.Builder(null,"停止定位",stop).build());
        if(Build.VERSION.SDK_INT>=31)b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);return b.build();
    }
    private void status(String text){if(text.equals(prefs(this).getString("status","")))return;prefs(this).edit().putString("status",text).apply();Notifications.manager(this).notify(NOTICE,notification(text));}
    @Override public void onLocationChanged(Location location){
        if(!running||!location.hasAccuracy())return;long now=SystemClock.elapsedRealtime(),age=(SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos())/1000000;
        if(age<0||age>120000)return;lastFix=now-age;
        if(location.getAccuracy()>150){status("定位精度不足 · 等待更准确的位置");return;}
        int node=inference.accept(now,location.getLatitude(),location.getLongitude(),location.getAccuracy(),age);
        if(node>=0){try{JSONArray arr=new JSONArray(prefs(this).getString("nodes","[]"));JSONObject item=new JSONObject();item.put("name",JourneyInference.NAMES[node]);item.put("time",DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.ofHours(8)).format(Instant.now()));item.put("at",System.currentTimeMillis());JSONArray next=new JSONArray();for(int i=Math.max(0,arr.length()-39);i<arr.length();i++)next.put(arr.get(i));next.put(item);prefs(this).edit().putString("nodes",next.toString()).apply();status("已接近"+JourneyInference.NAMES[node]+" · 请确认实际路线");}catch(Exception ignored){}}
        else status(JourneyInference.movement(location.hasSpeed()?location.getSpeed():-1));
    }
    @Override public void onProviderEnabled(String provider){if(running)status("定位已开启，等待新位置");}
    @Override public void onProviderDisabled(String provider){if(running)status("定位服务发生变化，等待位置更新");}
    @Override public void onStatusChanged(String p,int s,Bundle b){}
    @Override public void onDestroy(){running=false;main.removeCallbacksAndMessages(null);try{if(manager!=null)manager.removeUpdates(this);}catch(RuntimeException ignored){}if(explicitStop)prefs(this).edit().putBoolean("wanted",false).apply();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
