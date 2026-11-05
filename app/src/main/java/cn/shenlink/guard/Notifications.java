package cn.shenlink.guard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

final class Notifications {
    static final String WATCH = "watch", ALERT = "crossing_alerts";
    static final int ONGOING_ID = 1, ALERT_ID = 2, BOOT_ID = 3, TEST_ID = 4;
    static NotificationManager manager(Context c) { return c.getSystemService(NotificationManager.class); }
    static void channels(Context c) {
        NotificationChannel watch = new NotificationChannel(WATCH, "守护运行状态", NotificationManager.IMPORTANCE_LOW);
        watch.setDescription("显示通勤守护是否正在运行");
        watch.setShowBadge(false);
        NotificationChannel alert = new NotificationChannel(ALERT, "过关换卡提醒", NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("罗湖附近与上网卡网络变化提醒，请允许横幅、声音和锁屏显示");
        alert.enableVibration(true);
        alert.setVibrationPattern(new long[]{0, 400, 180, 400});
        manager(c).createNotificationChannel(watch);
        manager(c).createNotificationChannel(alert);
    }
    static boolean ready(Context c) {
        channels(c);
        return manager(c).areNotificationsEnabled() && manager(c).getNotificationChannel(ALERT).getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    static PendingIntent home(Context c, int request) {
        return PendingIntent.getActivity(c, request, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent action(Context c, String action, int request) {
        return PendingIntent.getBroadcast(c, request, new Intent(c, ActionReceiver.class).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static Notification ongoing(Context c, String text) {
        Notification.Builder b = new Notification.Builder(c, WATCH)
            .setSmallIcon(R.drawable.ic_notification).setColor(Color.rgb(19,121,101))
            .setContentTitle("过关换卡 · 通勤守护中").setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(home(c, 1)).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(null, "停止守护", action(c, "stop", 10)).build());
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }
    static boolean alert(Context c, String title, String body, boolean test) {
        if (!ready(c)) return false;
        PendingIntent settings = PendingIntent.getActivity(c, 20, new Intent(c, SettingsShortcutActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, ALERT)
            .setSmallIcon(R.drawable.ic_notification).setColor(Color.rgb(19,121,101))
            .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
            .setContentIntent(home(c, 2)).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(new Notification.Action.Builder(null, "去换卡", settings).build());
        if (!test) b.addAction(new Notification.Action.Builder(null, "暂停提醒10分钟", action(c, "snooze", 11)).build());
        manager(c).notify(test ? TEST_ID : ALERT_ID, b.build());
        return true;
    }
    static void boot(Context c) {
        channels(c);
        if (!ready(c)) return;
        manager(c).notify(BOOT_ID, new Notification.Builder(c, ALERT)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("点此恢复通勤守护")
            .setContentText("手机重启或应用更新后，请打开过关换卡，重新开启守护。")
            .setContentIntent(home(c, 3)).setAutoCancel(true).build());
    }
}
