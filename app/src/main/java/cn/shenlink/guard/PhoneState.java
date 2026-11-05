package cn.shenlink.guard;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.util.ArrayList;
import java.util.List;

final class PhoneState {
    static final class Snapshot {
        List<SubscriptionInfo> sims = new ArrayList<>();
        int defaultId = -1, activeId = -1;
        String country = "", operator = "", transport = "暂未联网";
        boolean permission, roaming;
        boolean has(int id) { for (SubscriptionInfo s : sims) if (s.getSubscriptionId() == id) return true; return false; }
        String label(int id) {
            for (SubscriptionInfo s : sims) if (s.getSubscriptionId() == id) return simLabel(s);
            return id < 0 ? "暂未识别" : "卡信息暂不可用";
        }
        boolean configured(AppPrefs p) { return !country.isEmpty() && p.sim(country) >= 0 && has(p.sim(country)); }
        ReminderEngine.Sample sample(AppPrefs p, long now) {
            return new ReminderEngine.Sample(now, country, defaultId, activeId, p.sim(country), configured(p));
        }
    }
    static boolean allowed(Context c, String permission) { return c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    static String region(String country) { return "cn".equals(country) ? "内地" : "hk".equals(country) ? "香港" : "暂未识别"; }
    static String simLabel(SubscriptionInfo info) {
        String name = info.getDisplayName() == null ? "SIM" : info.getDisplayName().toString();
        // The app never requests phone numbers or persistent hardware identifiers.
        return "卡 " + (info.getSimSlotIndex() + 1) + " · " + name;
    }
    static Snapshot read(Context c) {
        Snapshot s = new Snapshot();
        s.permission = allowed(c, Manifest.permission.READ_PHONE_STATE);
        if (s.permission) try {
            SubscriptionManager sm = c.getSystemService(SubscriptionManager.class);
            List<SubscriptionInfo> list = sm.getActiveSubscriptionInfoList();
            if (list != null) s.sims.addAll(list);
            s.defaultId = SubscriptionManager.getDefaultDataSubscriptionId();
            if (Build.VERSION.SDK_INT >= 30) s.activeId = SubscriptionManager.getActiveDataSubscriptionId();
        } catch (RuntimeException ignored) { }
        try {
            TelephonyManager tm = c.getSystemService(TelephonyManager.class);
            int id = s.activeId >= 0 ? s.activeId : s.defaultId;
            if (id >= 0) tm = tm.createForSubscriptionId(id);
            s.country = ReminderEngine.normalize(tm.getNetworkCountryIso());
            s.operator = tm.getNetworkOperatorName();
            s.roaming = tm.isNetworkRoaming();
        } catch (RuntimeException ignored) { }
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) s.transport = "VPN（底层连接由系统管理）";
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s.transport = "Wi-Fi";
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) s.transport = "移动数据";
                else s.transport = "其他网络";
            }
        } catch (RuntimeException ignored) { }
        return s;
    }
}
