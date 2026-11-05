package cn.shenlink.guard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class AppPrefs {
    final SharedPreferences data;
    AppPrefs(Context c) { data = c.getSharedPreferences("guard", Context.MODE_PRIVATE); }
    int sim(String country) { return data.getInt("sim_" + country, -1); }
    void sim(String country, int id) { data.edit().putInt("sim_" + country, id).apply(); }
    boolean locationEnabled() { return data.getBoolean("location", true); }
    String portName() { return Ports.NAMES[data.getInt("port",0)]; } double portLat() { return Ports.LAT[data.getInt("port",0)]; } double portLon() { return Ports.LON[data.getInt("port",0)]; } int radius() { return data.getInt("radius", 1200); }
    boolean wanted() { return data.getBoolean("wanted", false); }
    void wanted(boolean on) { data.edit().putBoolean("wanted", on).apply(); }
    void status(String value) { data.edit().putString("status", value).apply(); }
    String status() { return data.getString("status", "还没有开启通勤守护"); }
    synchronized void log(String title, String body) {
        try {
            JSONArray old = new JSONArray(data.getString("history", "[]"));
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("time", new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date()));
            item.put("title", title); item.put("body", body);
            next.put(item);
            for (int i = 0; i < Math.min(old.length(), 39); i++) next.put(old.get(i));
            data.edit().putString("history", next.toString()).apply();
        } catch (Exception ignored) { }
    }
    String history() {
        try {
            JSONArray arr = new JSONArray(data.getString("history", "[]"));
            if (arr.length() == 0) return "还没有提醒记录。开启守护后，提醒会保存在这里。";
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                out.append(row.optString("time")).append("  ").append(row.optString("title")).append("\n")
                   .append(row.optString("body")).append("\n\n");
            }
            return out.toString().trim();
        } catch (Exception e) { return "暂无记录"; }
    }
}
