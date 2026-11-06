package cn.shenlink.guard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class GuardActivity extends Activity {
    private static final int INK = Color.rgb(25,48,42), MUTED = Color.rgb(101,117,109), GREEN = Color.rgb(19,121,101), BG = Color.rgb(246,247,243);
    private AppPrefs prefs;
    private LinearLayout content; private LinearLayout[] panels;
    private TextView headline, status, detail, cnValue, hkValue, permissions, radiusValue, directionValue;
    private Button toggle;
    private boolean pendingStart;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable repaint = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 2000); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); prefs = new AppPrefs(this); Notifications.channels(this); build();
    }
    @Override public void onResume() { super.onResume(); handler.removeCallbacks(repaint); handler.post(repaint); }
    @Override public void onPause() { handler.removeCallbacks(repaint); super.onPause(); }
    private int dp(float x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLineSpacing(dp(3), 1); t.setIncludeFontPadding(false);
        return t;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(top); parent.addView(child, lp);
    }
    private LinearLayout card(int color) {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(20),dp(20),dp(20),dp(20));
        c.setBackground(background(color, 24)); add(content, c, 14); return c;
    }
    private Button button(String title, int color, int foreground, View.OnClickListener click) {
        Button b = new Button(this); b.setText(title); b.setTextSize(15); b.setTextColor(foreground); b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); b.setBackgroundTintList(null); b.setBackground(background(color,16));
        b.setStateListAnimator(null); b.setElevation(0); b.setClipToOutline(true); b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18000000),background(color,16),null)); b.setPadding(dp(12),dp(14),dp(12),dp(14)); b.setMinHeight(dp(52)); b.setOnClickListener(click);
        return b;
    }
    private TextView setting(LinearLayout parent, String title, String hint, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(14),dp(15),dp(14),dp(15));
        row.setBackground(background(Color.rgb(246,248,244),14)); row.setOnClickListener(onClick); row.setFocusable(true);
        row.addView(text(title + "  ›",15,INK,true));
        TextView value = text(hint,13,MUTED,false); add(row,value,7); add(parent,row,12); return value;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        // Android 15+ edge-to-edge: apply actual system-bar insets, including gesture navigation.
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets x = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top=x.top; bottom=x.bottom; left=x.left; right=x.right;
            } else { top=insets.getSystemWindowInsetTop(); bottom=insets.getSystemWindowInsetBottom(); left=insets.getSystemWindowInsetLeft(); right=insets.getSystemWindowInsetRight(); }
            v.setPadding(left,top,right,bottom); return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20),dp(20),dp(20),dp(32));
        scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,-1)); setContentView(root);
        root.requestApplyInsets();
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); TextView brand=text("换卡守护",20,INK,true); brand.setOnClickListener(v -> finish()); brand.setContentDescription("返回通勤助手主页"); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1)); header.addView(button("☰  菜单",Color.WHITE,GREEN,v -> openMenu())); add(content,header,0);
        add(content,button("‹ 返回通勤助手",Color.rgb(234,242,235),GREEN,v -> finish()),7);

        LinearLayout hero = card(Color.rgb(19,81,65));
        TextView tag = text("跨境通勤助手",12,Color.rgb(206,232,204),true); add(hero,tag,0);
        LinearLayout route = new LinearLayout(this); route.setGravity(Gravity.CENTER_VERTICAL);
        for(String city : new String[]{"深圳", "⇄ 口岸 ⇄", "香港"}) {
            TextView node=text(city,14,Color.rgb(211,237,190),true);
            node.setGravity(Gravity.CENTER); node.setSingleLine(true);
            node.setEllipsize(android.text.TextUtils.TruncateAt.END);
            route.addView(node,new LinearLayout.LayoutParams(0,dp(44),1));
        }
        add(hero,route,8);
        headline = text("守护未开启",24,Color.WHITE,true); add(hero,headline,2);
        status = text("出发前开启，过关时多一份提醒。",14,Color.rgb(222,235,226),false); add(hero,status,8);
        toggle = button("开启通勤守护",Color.rgb(218,240,163),INK,v -> {
            if (MonitorService.running) {
                prefs.wanted(false); stopService(new Intent(this,MonitorService.class));
                Notifications.manager(this).cancel(Notifications.ALERT_ID); handler.postDelayed(this::refresh,300);
            } else explainStart();
        }); add(hero,toggle,18);

        LinearLayout data = card(Color.WHITE);
        add(data,text("现在用哪张卡",18,INK,true),0);
        detail = text("正在读取…",14,MUTED,false); add(data,detail,13);
        add(data,button("打开手机换卡设置  ↗",Color.rgb(234,242,235),GREEN,v -> SettingsShortcutActivity.open(this)),14);

        LinearLayout setup = card(Color.WHITE);
        add(setup,text("两地上网卡",18,INK,true),0);
        add(setup,text("按你的套餐指定。香港卡在内地使用时，可能产生漫游费用。",13,MUTED,false),8);
        cnValue = setting(setup,"在内地用", "点击选择",v -> chooseSim("cn"));
        hkValue = setting(setup,"在香港用", "点击选择",v -> chooseSim("hk"));

        LinearLayout border = card(Color.WHITE);
        add(border,text("口岸附近，提前提醒",18,INK,true),0);
        setting(border,"选择口岸",prefs.portName(),v -> new AlertDialog.Builder(this).setTitle("选择常用口岸").setSingleChoiceItems(Ports.NAMES,prefs.data.getInt("port",0),(d,i)->{prefs.data.edit().putInt("port",i).apply(); ((TextView)((LinearLayout)v).getChildAt(1)).setText(prefs.portName()); d.dismiss();}).setNegativeButton("取消",null).show()); Switch loc = new Switch(this); loc.setText("使用位置提醒"); loc.setTextColor(INK); loc.setTextSize(15); loc.setPadding(0,dp(12),0,dp(8)); loc.setChecked(prefs.locationEnabled());
        loc.setOnCheckedChangeListener((b,on) -> {
            prefs.data.edit().putBoolean("location",on).apply();
            if (on && !PhoneState.allowed(this,Manifest.permission.ACCESS_FINE_LOCATION)) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},102);
            } else restartIfRunning();
        }); add(border,loc,4);
        radiusValue = setting(border,"提醒范围", "",v -> chooseRadius());
        directionValue = setting(border,"这次的方向", "",v -> chooseDirection());
        add(border,text("位置提醒和网络检查各自工作。没有新位置时，仍可检查网络变化。",12,MUTED,false),12);

        LinearLayout health = card(Color.WHITE);
        add(health,text("让提醒及时到达",18,INK,true),0);
        permissions = text("",13,MUTED,false); add(health,permissions,12);
        add(health,button("检查权限",Color.rgb(234,242,235),GREEN,v -> askPermissions(false)),12);
        add(health,button("发送一条测试提醒",Color.rgb(234,242,235),GREEN,v -> testNotification()),10);
        add(health,button("ColorOS 后台设置指南",Color.rgb(246,248,244),INK,v -> backgroundHelp()),10);
        add(health,button("声音、横幅与锁屏通知",Color.rgb(246,248,244),INK,v -> notificationSettings()),10);

        LinearLayout tools = card(Color.WHITE);
        add(tools,text("提醒记录与说明",18,INK,true),0);
        add(tools,button("查看最近提醒",Color.rgb(246,248,244),INK,v -> history()),12);
        add(tools,button("使用说明与隐私",Color.rgb(246,248,244),INK,v -> about()),10);
        panels = new LinearLayout[]{setup,border,health,tools}; for(LinearLayout panel:panels) { content.removeView(panel); } TextView footer=text("跨境通勤 · 本机运行\n更多设置与提醒记录，点击右上角菜单",12,MUTED,false); footer.setGravity(Gravity.CENTER); add(content,footer,24);
        refresh();
    }

    private void openMenu() {
        String[] items={"两地上网卡", "位置与通勤方向", "通知与后台权限", "提醒记录与说明"};
        new AlertDialog.Builder(this).setTitle("通勤设置").setItems(items,(d,index)->{
            LinearLayout panel=panels[index];
            if(panel.getParent()!=null) ((android.view.ViewGroup)panel.getParent()).removeView(panel);
            ScrollView sheet=new ScrollView(this); sheet.setPadding(dp(8),0,dp(8),dp(8)); sheet.addView(panel);
            new AlertDialog.Builder(this).setView(sheet).setPositiveButton("完成",(dialog,w)->refresh())
                .setOnDismissListener(dialog->{sheet.removeView(panel);refresh();}).show();
        }).setNegativeButton("返回首页",null).show();
    }
    private void refresh() {
        if (prefs == null) return;
        PhoneState.Snapshot s=PhoneState.read(this);
        boolean live=MonitorService.running;
        headline.setText(live ? "通勤守护中" : prefs.wanted() ? "守护已中断" : "守护未开启");
        status.setText(live ? prefs.status() : prefs.wanted() ? "请重新开启，并检查后台运行设置。" : "出发前开启，过关时多一份提醒。");
        toggle.setText(live ? "停止通勤守护" : "开启通勤守护");
        String info = s.permission ? "默认上网卡\n"+s.label(s.defaultId)+(s.activeId >= 0 && s.activeId != s.defaultId ? "\n实际数据卡："+s.label(s.activeId) : "") : "允许电话状态权限后，可识别上网卡。";
        info += "\n蜂窝网络：" + PhoneState.region(s.country) + (s.operator.isEmpty()?"":" · "+s.operator);
        info += "\n当前连接："+s.transport;
        if(s.roaming) info += "\n系统报告：当前网络处于漫游状态";
        if(live) info += "\n"+MonitorService.locationStatus;
        detail.setText(info);
        cnValue.setText(mappingLabel(s,"cn")); hkValue.setText(mappingLabel(s,"hk"));
        radiusValue.setText(prefs.radius() < 1000 ? prefs.radius()+" 米" : String.format(java.util.Locale.CHINA,"%.1f 公里",prefs.radius()/1000f));
        String direction=prefs.data.getString("direction","auto");
        directionValue.setText("hk".equals(direction)?"深圳 → 香港":"cn".equals(direction)?"香港 → 深圳":"双向提醒");
        String location=PhoneState.allowed(this,Manifest.permission.ACCESS_FINE_LOCATION)?"精确位置已允许":PhoneState.allowed(this,Manifest.permission.ACCESS_COARSE_LOCATION)?"仅大致位置，口岸提醒可能不可用":"位置未授权，仍可用网络提醒";
        NotificationChannel channel=Notifications.manager(this).getNotificationChannel(Notifications.ALERT);
        String alert = !Notifications.ready(this) ? "通知未开启，无法守护" : channel.getImportance() < NotificationManager.IMPORTANCE_HIGH ? "通知可用，建议开启横幅与声音" : "提醒通知已开启";
        PowerManager power=getSystemService(PowerManager.class);
        String battery=power.isIgnoringBatteryOptimizations(getPackageName())?"系统电池优化已豁免":"请检查 ColorOS 后台活动限制";
        permissions.setText((s.permission?"✓ 电话状态已允许":"○ 电话状态待授权")+"\n"+(Notifications.ready(this)?"✓ ":"○ ")+alert+"\n"+location+"\n"+battery);
    }
    private String mappingLabel(PhoneState.Snapshot s,String region) {
        int id=prefs.sim(region);
        return id<0?"点击选择上网卡":!s.permission?"已保存；授权后可查看":!s.has(id)?"原来的卡暂不可用，请重新选择":s.label(id);
    }
    private void chooseSim(String region) {
        if(!PhoneState.allowed(this,Manifest.permission.READ_PHONE_STATE)) {
            new AlertDialog.Builder(this).setTitle("识别两张上网卡").setMessage("需要电话状态权限来读取卡槽、运营商和数据卡选择，不读取电话号码、通话记录或短信。")
                .setPositiveButton("允许读取",(d,w)->requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE},101)).setNegativeButton("暂不",null).show(); return;
        }
        PhoneState.Snapshot s=PhoneState.read(this);
        if(s.sims.isEmpty()) { message("还没有识别到 SIM 卡","请确认手机已启用 SIM 卡，并在权限管理中允许读取电话状态，再回来选择。"); return; }
        String[] labels=new String[s.sims.size()+1]; labels[0]="暂不指定";
        int checked=0;
        for(int i=0;i<s.sims.size();i++) {labels[i+1]=PhoneState.simLabel(s.sims.get(i));if(s.sims.get(i).getSubscriptionId()==prefs.sim(region)) checked=i+1;}
        new AlertDialog.Builder(this).setTitle("在"+PhoneState.region(region)+"使用哪张卡？").setSingleChoiceItems(labels,checked,(d,index)->{
            prefs.sim(region,index==0?-1:s.sims.get(index-1).getSubscriptionId()); d.dismiss(); refresh();
        }).setNegativeButton("取消",null).show();
    }
    private void chooseRadius() {
        int[] values={600,1200,2000}; String[] names={"600 米 · 更靠近口岸","1.2 公里 · 默认","2 公里 · 更早提醒"};
        int selected=prefs.radius()==600?0:prefs.radius()==2000?2:1;
        new AlertDialog.Builder(this).setTitle("口岸附近提醒范围").setSingleChoiceItems(names,selected,(d,i)->{prefs.data.edit().putInt("radius",values[i]).apply(); d.dismiss(); refresh();}).setNegativeButton("取消",null).show();
    }
    private void chooseDirection() {
        String[] keys={"auto","hk","cn"}; String[] labels={"双向提醒","深圳 → 香港","香港 → 深圳"};
        new AlertDialog.Builder(this).setTitle("这次的通勤方向").setItems(labels,(d,i)->{prefs.data.edit().putString("direction",keys[i]).apply();refresh();}).show();
    }
    private void explainStart() {
        if (PhoneState.allowed(this,Manifest.permission.READ_PHONE_STATE) && Notifications.ready(this)) { startGuard(); return; }
        new AlertDialog.Builder(this).setTitle("开启通勤守护").setMessage("通知：发送换卡提醒。\n电话状态：识别当前上网卡及其网络。\n位置（可选）：接近罗湖时提前提醒。\n\n开启后，通知栏会显示守护状态。位置只在本机处理，不会上传。")
            .setPositiveButton("继续开启",(d,w)->askPermissions(true)).setNegativeButton("暂不",null).show();
    }
    private void askPermissions(boolean start) {
        pendingStart=start;
        List<String> needed=new ArrayList<>();
        if(!PhoneState.allowed(this,Manifest.permission.READ_PHONE_STATE)) needed.add(Manifest.permission.READ_PHONE_STATE);
        if(Build.VERSION.SDK_INT>=33 && !PhoneState.allowed(this,Manifest.permission.POST_NOTIFICATIONS)) needed.add(Manifest.permission.POST_NOTIFICATIONS);
        if(prefs.locationEnabled() && !PhoneState.allowed(this,Manifest.permission.ACCESS_FINE_LOCATION)) {needed.add(Manifest.permission.ACCESS_FINE_LOCATION);needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);}
        if(needed.isEmpty()) {
            if(!Notifications.ready(this)) notificationSettings();
            else if(start) startGuard();
            else {refresh();message("权限检查完成","电话状态和通知权限已允许。建议发送一条测试提醒，再到 ColorOS 后台设置中允许此应用后台运行。");}
        } else requestPermissions(needed.toArray(new String[0]),100);
    }
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] results) {
        super.onRequestPermissionsResult(code,perms,results); refresh();
        if(code==100 && pendingStart) {
            pendingStart=false;
            if(PhoneState.allowed(this,Manifest.permission.READ_PHONE_STATE) && Notifications.ready(this)) startGuard();
            else new AlertDialog.Builder(this).setTitle("还需要电话状态和通知权限").setMessage("没有这些权限，无法可靠识别上网卡并提醒。你可以在应用设置中开启；位置权限可选。")
                .setPositiveButton("打开应用设置",(d,w)->appSettings()).setNegativeButton("稍后",null).show();
        } else if(code==102 || code==100) restartIfRunning();
    }
    private void startGuard() {
        if(prefs.locationEnabled() && !PhoneState.allowed(this,Manifest.permission.ACCESS_COARSE_LOCATION)) Toast.makeText(this,"当前使用网络提醒；位置未授权",Toast.LENGTH_LONG).show();
        try { startForegroundService(new Intent(this,MonitorService.class).setAction("start")); handler.postDelayed(this::refresh,600); }
        catch(RuntimeException e) { message("暂时无法开启","请检查通知和电话状态权限，并允许应用在后台运行。"); }
    }
    private void restartIfRunning() {
        if(MonitorService.running) try {startForegroundService(new Intent(this,MonitorService.class).setAction("refresh"));} catch(RuntimeException ignored) { }
        refresh();
    }
    private void testNotification() {
        if(!Notifications.ready(this)) {
            new AlertDialog.Builder(this).setTitle("先允许提醒通知").setMessage("开启通知后，再发送测试提醒。")
                .setPositiveButton("检查权限",(d,w)->askPermissions(false)).setNegativeButton("稍后",null).show();return;
        }
        if(Notifications.alert(this,"测试提醒 · 过关记得换卡","这是一条测试通知。请检查声音、振动、横幅和锁屏显示；点击「去换卡」可打开系统设置。",true)) {
            prefs.log("发送测试通知","请在通知栏确认是否收到，并按需开启声音、横幅和锁屏显示。");
            Toast.makeText(this,"已发送测试提醒，请查看通知栏",Toast.LENGTH_LONG).show();
        }
    }
    private void appSettings() { launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))); }
    private void notificationSettings() {
        launch(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,Notifications.ALERT));
    }
    private void launch(Intent intent) {
        try {startActivity(intent);} catch(RuntimeException e) {try {startActivity(new Intent(Settings.ACTION_SETTINGS));} catch(RuntimeException ignored) {Toast.makeText(this,"请手动打开手机设置",Toast.LENGTH_LONG).show();}}
    }
    private void backgroundHelp() {
        new AlertDialog.Builder(this).setTitle("OPPO / ColorOS 后台运行").setMessage("1. 在应用信息或设置搜索中找到「应用耗电管理 / 电池」，允许后台活动。\n\n2. 如果有「自启动 / 关联启动」，允许本应用；在最近任务中锁定本应用卡片（若系统提供）。\n\n3. 在「过关换卡提醒」通知类别中允许声音、振动、横幅与锁屏通知。勿扰模式可能静音。\n\n4. 开启守护后，可返回桌面或锁屏。若手动强行停止、系统清理或重启，请重新打开并开启守护。\n\nColorOS 17 的菜单名称可能随系统版本变化，以手机实际显示为准。")
            .setPositiveButton("打开应用信息",(d,w)->appSettings())
            .setNeutralButton("电池优化设置",(d,w)->launch(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))
            .setNegativeButton("知道了",null).show();
    }
    private void history() {
        TextView t=text(prefs.history(),14,INK,false);t.setPadding(dp(20),dp(12),dp(20),dp(12)); t.setTextIsSelectable(true);
        ScrollView view=new ScrollView(this);view.addView(t);
        new AlertDialog.Builder(this).setTitle("最近提醒 · 最多40条").setView(view).setPositiveButton("关闭",null)
            .setNeutralButton("清空记录",(d,w)->{prefs.data.edit().remove("history").apply();Toast.makeText(this,"记录已清空",Toast.LENGTH_SHORT).show();}).show();
    }
    private void about() {
        message("使用说明与隐私", "先指定内地、香港各自要用的卡，再开启守护。两地可选择同一张卡。换卡或重新安装 eSIM 后，请重新检查设置。\n\n罗湖预警：默认约1.2公里，位置需足够新且精确。室内、地铁、定位关闭或省电模式可能影响提醒。\n\n网络检查：内地／香港网络稳定约15秒后检查数据卡；与预设不一致时，约每3分钟再次提醒。可暂停提醒10分钟。网络国家来自基站，不等于实际所在地。\n\n后台：守护以常驻通知运行，手机厂商仍可能清理。应用不会自动切卡、断网、代扣费或判断你的套餐价格，提醒也不能保证在产生费用前到达。\n\n隐私：应用没有联网权限，无账号、广告或统计服务。换卡守护的位置只在内存中计算口岸距离。另行开启通勤定位时，会在本机保存到达节点与时间，不保存完整轨迹。卡配置和最多40条提醒保存在本机；不读取手机号、短信、联系人或通话记录。卸载会删除本机数据。\n\n版本 2.0.0 · 通勤助手内置换卡守护");
    }
    private void message(String title,String body) {new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("知道了",null).show();}
}
