package cn.shenlink.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class ActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        AppPrefs p = new AppPrefs(c);
        if ("stop".equals(i.getAction())) {
            p.wanted(false);
            c.stopService(new Intent(c, MonitorService.class));
            Notifications.manager(c).cancel(Notifications.ALERT_ID);
        } else if ("snooze".equals(i.getAction())) {
            p.data.edit().putLong("snooze_elapsed", SystemClock.elapsedRealtime() + 600_000).apply();
            Notifications.manager(c).cancel(Notifications.ALERT_ID);
        }
    }
}
