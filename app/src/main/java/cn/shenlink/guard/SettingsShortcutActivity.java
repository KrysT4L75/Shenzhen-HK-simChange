package cn.shenlink.guard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class SettingsShortcutActivity extends Activity {
    @Override public void onCreate(Bundle state) { super.onCreate(state); open(this); finish(); }
    static void open(Context c) {
        // Public system entry points only; OEMs may route these to a broader network settings screen.
        String[] actions = {"android.settings.DUAL_SIM_SETTINGS", "android.settings.MANAGE_ALL_SIM_PROFILES_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS};
        for (String action : actions) {
            try {
                c.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Toast.makeText(c, "在系统设置中找到「移动网络 / SIM卡」，选择默认上网卡", Toast.LENGTH_LONG).show();
                return;
            } catch (RuntimeException ignored) { }
        }
        Toast.makeText(c, "请手动打开系统设置 → 移动网络 → 默认上网卡", Toast.LENGTH_LONG).show();
    }
}
