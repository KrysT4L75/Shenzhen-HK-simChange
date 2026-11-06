package cn.shenlink.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) return;
        CalendarReminders.sync(c);
        AppPrefs p = new AppPrefs(c);
        p.data.edit().remove("snooze_elapsed").remove("port_elapsed").apply();
        if (p.wanted()) {
            p.status("守护已中断，请重新开启");
            Notifications.boot(c);
        }
    }
}
