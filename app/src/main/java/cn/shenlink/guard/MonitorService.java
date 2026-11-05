package cn.shenlink.guard;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import java.util.ArrayList;
import java.util.List;

public class MonitorService extends Service {
    public static volatile boolean running;
    public static volatile String locationStatus = "位置提醒未开启";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ReminderEngine engine = new ReminderEngine();
    private AppPrefs prefs;
    private LocationManager locations;
    private boolean listenersReady, locationRegistered, receiverRegistered, networkRegistered;
    private boolean hasLocationType;
    private String lastOngoing = "", displayedRisk = "", simSignature = "";
    private long lastFixElapsed = -1;
    private final List<Runnable> phoneCleanup = new ArrayList<>();
    private SubscriptionManager subscriptions;
    private SubscriptionManager.OnSubscriptionsChangedListener subListener;
    private ConnectivityManager connectivity;

    private final Runnable periodic = new Runnable() {
        @Override public void run() { if (!running) return; tick(); main.postDelayed(this, 10_000); }
    };
    private final Runnable refresh = () -> { if (running) tick(); };
    private void scheduleRefresh() { main.removeCallbacks(refresh); main.postDelayed(refresh, 400); }
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { scheduleRefresh(); }
    };
    private final ConnectivityManager.NetworkCallback network = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { scheduleRefresh(); }
        @Override public void onLost(Network n) { scheduleRefresh(); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) { scheduleRefresh(); }
    };
    private final LocationListener locationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) { acceptLocation(location); }
        @Override public void onProviderEnabled(String provider) { scheduleRefresh(); }
        @Override public void onProviderDisabled(String provider) { scheduleRefresh(); }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    };

    @Override public void onCreate() {
        super.onCreate(); prefs = new AppPrefs(this); Notifications.channels(this);
        locations = getSystemService(LocationManager.class);
        long savedPort = prefs.data.getLong("port_elapsed", -1);
        if (savedPort <= SystemClock.elapsedRealtime()) engine.restorePortTime(savedPort);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        boolean explicitStart = intent != null && "start".equals(intent.getAction());
        if (!prefs.wanted() && !explicitStart) { stopSelf(); return START_NOT_STICKY; }
        if (!PhoneState.allowed(this, Manifest.permission.READ_PHONE_STATE) || !Notifications.ready(this)) {
            prefs.status("权限不完整，请打开应用检查通知和电话权限");
            prefs.wanted(false); stopSelf(); return START_NOT_STICKY;
        }
        boolean useLocation = prefs.locationEnabled() && (PhoneState.allowed(this, Manifest.permission.ACCESS_COARSE_LOCATION) || PhoneState.allowed(this, Manifest.permission.ACCESS_FINE_LOCATION));
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                if (useLocation) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
                try {
                    startForeground(Notifications.ONGOING_ID, Notifications.ongoing(this, "正在检查网络和上网卡…"), types);
                    hasLocationType = useLocation;
                } catch (SecurityException e) {
                    // A system restart can lack while-in-use location eligibility. Keep network monitoring honest.
                    startForeground(Notifications.ONGOING_ID, Notifications.ongoing(this, "网络守护中；请打开应用恢复位置提醒"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                    hasLocationType = false;
                }
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(Notifications.ONGOING_ID, Notifications.ongoing(this, "正在检查网络和上网卡…"), useLocation ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);
                hasLocationType = useLocation;
            } else {
                startForeground(Notifications.ONGOING_ID, Notifications.ongoing(this, "正在检查网络和上网卡…"));
                hasLocationType = useLocation;
            }
        } catch (RuntimeException e) {
            prefs.status("系统未允许启动守护，请在应用内重新开启");
            prefs.wanted(false); stopSelf(); return START_NOT_STICKY;
        }
        running = true; prefs.wanted(true);
        if (explicitStart) {
            prefs.data.edit().remove("snooze_elapsed").apply();
            engine.clearSnooze();
            Notifications.manager(this).cancel(Notifications.BOOT_ID);
        }
        attachListeners(); configureLocations();
        main.removeCallbacks(periodic); main.post(periodic);
        return START_STICKY;
    }

    private void attachListeners() {
        if (listenersReady) return;
        listenersReady = true;
        IntentFilter filter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction("android.telephony.action.NETWORK_COUNTRY_CHANGED");
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else registerReceiver(receiver, filter);
            receiverRegistered = true;
        } catch (RuntimeException ignored) { }
        try {
            connectivity = getSystemService(ConnectivityManager.class);
            connectivity.registerDefaultNetworkCallback(network, main);
            networkRegistered = true;
        } catch (RuntimeException ignored) { }
        try {
            subscriptions = getSystemService(SubscriptionManager.class);
            subListener = new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override public void onSubscriptionsChanged() { simSignature = ""; scheduleRefresh(); }
            };
            if (Build.VERSION.SDK_INT >= 30) subscriptions.addOnSubscriptionsChangedListener(getMainExecutor(), subListener);
            else subscriptions.addOnSubscriptionsChangedListener(subListener);
        } catch (RuntimeException ignored) { }
    }

    private void attachPhoneListeners(PhoneState.Snapshot s) {
        StringBuilder signature = new StringBuilder("s:");
        for (SubscriptionInfo info : s.sims) signature.append(info.getSubscriptionId()).append(',');
        if (simSignature.equals(signature.toString())) return;
        simSignature = signature.toString();
        for (Runnable clean : phoneCleanup) clean.run();
        phoneCleanup.clear();
        TelephonyManager base = getSystemService(TelephonyManager.class);
        if (base == null) return;
        for (SubscriptionInfo info : s.sims) {
            TelephonyManager tm = base.createForSubscriptionId(info.getSubscriptionId());
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    class ChangeCallback extends TelephonyCallback implements TelephonyCallback.ServiceStateListener, TelephonyCallback.ActiveDataSubscriptionIdListener {
                        @Override public void onServiceStateChanged(ServiceState state) { scheduleRefresh(); }
                        @Override public void onActiveDataSubscriptionIdChanged(int id) { scheduleRefresh(); }
                    }
                    ChangeCallback callback = new ChangeCallback();
                    tm.registerTelephonyCallback(getMainExecutor(), callback);
                    phoneCleanup.add(() -> { try { tm.unregisterTelephonyCallback(callback); } catch (RuntimeException ignored) { } });
                } else {
                    PhoneStateListener listener = new PhoneStateListener() {
                        @Override public void onServiceStateChanged(ServiceState state) { scheduleRefresh(); }
                    };
                    tm.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE);
                    phoneCleanup.add(() -> { try { tm.listen(listener, PhoneStateListener.LISTEN_NONE); } catch (RuntimeException ignored) { } });
                }
            } catch (RuntimeException ignored) { }
        }
    }

    private void configureLocations() {
        if (locationRegistered) { try { locations.removeUpdates(locationListener); } catch (RuntimeException ignored) { } }
        locationRegistered = false;
        if (!prefs.locationEnabled()) { locationStatus = "位置提醒已关闭"; return; }
        if (!hasLocationType) { locationStatus = "请打开应用授权位置并重新开启守护"; return; }
        boolean fine = PhoneState.allowed(this, Manifest.permission.ACCESS_FINE_LOCATION);
        for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
            if (provider.equals(LocationManager.GPS_PROVIDER) && !fine) continue;
            try {
                if (!locations.getAllProviders().contains(provider)) continue;
                locations.requestLocationUpdates(provider, 30_000, 50, locationListener, Looper.getMainLooper());
                locationRegistered = true;
                Location cached = locations.getLastKnownLocation(provider);
                if (cached != null) acceptLocation(cached);
            } catch (RuntimeException ignored) { }
        }
        if (lastFixElapsed < 0) locationStatus = locationRegistered ? "正在等待新位置；室内可能需要更久" : "未取得定位服务，网络提醒仍在运行";
    }

    private void syncSnooze(long now) {
        long until = prefs.data.getLong("snooze_elapsed", 0);
        if (until > now && until - now <= 600_000) engine.snooze(now, until - now);
        else engine.clearSnooze();
    }

    private void acceptLocation(Location l) {
        if (!running || !prefs.locationEnabled() || !hasLocationType || !l.hasAccuracy()) return;
        long now = SystemClock.elapsedRealtime();
        long age = (SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()) / 1_000_000;
        if (age < 0 || age > ReminderEngine.MAX_FIX_AGE_MS) return;
        lastFixElapsed = now - age;
        double meters = ReminderEngine.distanceMeters(l.getLatitude(), l.getLongitude(), prefs.portLat(), prefs.portLon());
        locationStatus = l.getAccuracy() > 250 ? "定位精度不足，等待更准确的位置" : "距" + prefs.portName() + "约 " + (meters < 1000 ? Math.round(meters) + " 米" : String.format(java.util.Locale.CHINA, "%.1f 公里", meters / 1000));
        syncSnooze(now);
        engine.setPort(prefs.portLat(),prefs.portLon()); ReminderEngine.Event event = engine.location(now, l.getLatitude(), l.getLongitude(), l.getAccuracy(), age, prefs.radius());
        if (event != null) {
            prefs.data.edit().putLong("port_elapsed", engine.lastPortTime()).apply();
            String direction = prefs.data.getString("direction", "auto");
            String advice = "hk".equals(direction) ? "前往香港：过关后记得切到你指定的香港上网卡。" : "cn".equals(direction) ? "返回深圳：过关后记得切到你指定的内地上网卡。" : "去香港用香港上网卡，回深圳用内地上网卡。";
            emit("快到" + prefs.portName() + "了，检查上网卡", advice + "当前只表示接近口岸，不代表已经过关。");
        }
    }

    private void tick() {
        if (!PhoneState.allowed(this, Manifest.permission.READ_PHONE_STATE)) {
            prefs.status("电话权限已关闭，守护已停止"); prefs.wanted(false); stopSelf(); return;
        }
        if (!Notifications.ready(this)) {
            prefs.status("提醒通知已关闭，守护已停止"); prefs.wanted(false); stopSelf(); return;
        }
        long now = SystemClock.elapsedRealtime(); syncSnooze(now);
        PhoneState.Snapshot s = PhoneState.read(this);
        attachPhoneListeners(s);
        ReminderEngine.Sample sample = s.sample(prefs, now);
        ReminderEngine.Event event = engine.evaluate(sample);
        if (event != null) {
            if ("risk".equals(event.kind)) {
                String wanted = s.label(sample.preferredId);
                String body = "上网卡连接到了" + PhoneState.region(s.country) + "网络。";
                if (event.activeWrong) body += "当前数据卡「" + s.label(s.activeId) + "」与预设不同。";
                else body += "默认上网卡「" + s.label(s.defaultId) + "」与预设不同。";
                body += "请检查是否应切到「" + wanted + "」。边境信号可能越界，请结合实际位置和套餐确认。";
                emit("检查上网卡，留意漫游费用", body);
                displayedRisk = event.key;
            } else emit("网络变为" + PhoneState.region(s.country) + "，检查上网卡", "请结合实际位置检查默认上网卡。在应用里指定两地用卡后，可以识别是否与预设一致。");
        }
        boolean knownCorrect = s.configured(prefs) && s.country.equals(engine.stableCountry()) &&
            (s.defaultId >= 0 || s.activeId >= 0) && !engine.isMismatch(sample);
        if (!displayedRisk.isEmpty() && knownCorrect) {
            Notifications.manager(this).cancel(Notifications.ALERT_ID);
            prefs.log("上网卡已符合当前网络预设", "当前连接" + PhoneState.region(s.country) + "网络；仍请以实际位置和套餐为准。");
            displayedRisk = "";
        }
        if (prefs.locationEnabled() && hasLocationType) {
            boolean locationOn;
            try { locationOn = locations.isProviderEnabled(LocationManager.GPS_PROVIDER) || locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER); }
            catch (RuntimeException e) { locationOn = false; }
            if (!locationOn) locationStatus = "手机定位已关闭，当前仅检查网络";
            else if (lastFixElapsed >= 0 && now - lastFixElapsed > ReminderEngine.MAX_FIX_AGE_MS) locationStatus = "位置已过期，等待更新；网络提醒仍在运行";
            else if (!PhoneState.allowed(this, Manifest.permission.ACCESS_COARSE_LOCATION)) locationStatus = "位置权限已关闭，当前仅检查网络";
        }
        String status;
        if (engine.isSnoozed(now)) status = "提醒暂停中（剩余 " + ((engine.snoozeUntil() - now + 59_999) / 60_000) + " 分钟）";
        else if (s.country.isEmpty()) status = "暂未识别内地／香港网络；等待手机恢复信号";
        else if (!s.configured(prefs)) status = "已连接" + PhoneState.region(s.country) + "网络 · 请设置该地区的上网卡";
        else if (engine.isMismatch(sample)) status = "已连接" + PhoneState.region(s.country) + "网络 · 上网卡与预设不同";
        else if (!s.country.equals(engine.stableCountry())) status = "正在确认" + PhoneState.region(s.country) + "网络…";
        else status = PhoneState.region(s.country) + "网络 · 上网卡符合预设";
        prefs.status(status);
        String ongoing = status + "\n" + locationStatus;
        if (!ongoing.equals(lastOngoing)) {
            Notifications.manager(this).notify(Notifications.ONGOING_ID, Notifications.ongoing(this, ongoing));
            lastOngoing = ongoing;
        }
    }

    private void emit(String title, String body) {
        if (Notifications.alert(this, title, body, false)) prefs.log(title, body);
    }

    @Override public void onDestroy() {
        running = false; main.removeCallbacksAndMessages(null);
        if (receiverRegistered) try { unregisterReceiver(receiver); } catch (RuntimeException ignored) { }
        if (networkRegistered) try { connectivity.unregisterNetworkCallback(network); } catch (RuntimeException ignored) { }
        if (subscriptions != null && subListener != null) try { subscriptions.removeOnSubscriptionsChangedListener(subListener); } catch (RuntimeException ignored) { }
        for (Runnable clean : phoneCleanup) clean.run();
        if (locations != null) try { locations.removeUpdates(locationListener); } catch (RuntimeException ignored) { }
        locationStatus = "位置提醒未运行";
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
