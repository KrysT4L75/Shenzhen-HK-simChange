package cn.shenlink.guard;

/** Platform-free rules. Network country is radio evidence, never proof of a physical crossing. */
public final class ReminderEngine {
    public static final long SETTLE_MS = 15_000L;
    public static final long REPEAT_MS = 180_000L;
    public static final long PORT_COOLDOWN_MS = 1_800_000L;
    public static final long MAX_FIX_AGE_MS = 120_000L;
    public static final double LUOHU_LAT = 22.5319;
    public static final double LUOHU_LON = 114.1144;

    public static final class Sample {
        public final long now;
        public final String country;
        public final int defaultId, activeId, preferredId;
        public final boolean configured;
        public Sample(long now, String country, int defaultId, int activeId, int preferredId, boolean configured) {
            this.now = now;
            this.country = normalize(country);
            this.defaultId = defaultId;
            this.activeId = activeId;
            this.preferredId = preferredId;
            this.configured = configured;
        }
    }

    public static final class Event {
        public final String kind, country, key;
        public final boolean activeWrong, defaultWrong;
        public Event(String kind, String country, String key, boolean activeWrong, boolean defaultWrong) {
            this.kind = kind; this.country = country; this.key = key;
            this.activeWrong = activeWrong; this.defaultWrong = defaultWrong;
        }
    }

    private double portLat=LUOHU_LAT, portLon=LUOHU_LON; public void setPort(double lat,double lon) { if(portLat!=lat || portLon!=lon) {portInside=false;lastPortAt=-1;} portLat=lat;portLon=lon; } private String candidate = "", stable = "", riskKey = "";
    private long candidateSince, lastRiskAt = -1, snoozeUntil;
    private boolean portInside;
    private long lastPortAt = -1;

    public String stableCountry() { return stable; }
    public void snooze(long now, long durationMs) { snoozeUntil = now + durationMs; }
    public long snoozeUntil() { return snoozeUntil; }
    public void clearSnooze() { snoozeUntil = 0; }
    public void restorePortTime(long elapsed) { lastPortAt = elapsed; }
    public long lastPortTime() { return lastPortAt; }
    public boolean isSnoozed(long now) { return now < snoozeUntil; }

    public Event evaluate(Sample s) {
        if (s.country.isEmpty()) { candidate = ""; return null; }
        if (!s.country.equals(candidate)) { candidate = s.country; candidateSince = s.now; return null; }
        if (s.now - candidateSince < SETTLE_MS) return null;
        String before = stable;
        boolean changed = !stable.isEmpty() && !stable.equals(s.country);
        stable = s.country;
        boolean activeWrong = s.configured && s.activeId >= 0 && s.activeId != s.preferredId;
        boolean defaultWrong = s.configured && s.defaultId >= 0 && s.defaultId != s.preferredId;
        if (activeWrong || defaultWrong) {
            String key = s.country + ":" + s.defaultId + ":" + s.activeId + ":" + s.preferredId;
            boolean newRisk = !key.equals(riskKey);
            riskKey = key;
            if (s.now < snoozeUntil) return null;
            if (newRisk || lastRiskAt < 0 || s.now - lastRiskAt >= REPEAT_MS) {
                lastRiskAt = s.now;
                return new Event("risk", s.country, key, activeWrong, defaultWrong);
            }
        } else {
            riskKey = "";
            lastRiskAt = -1;
            if (changed && !s.configured && s.now >= snoozeUntil)
                return new Event("network", s.country, before + ">" + s.country, false, false);
        }
        return null;
    }

    public boolean isMismatch(Sample s) {
        return s.configured && !s.country.isEmpty() && s.country.equals(stable) &&
                ((s.activeId >= 0 && s.activeId != s.preferredId) || (s.defaultId >= 0 && s.defaultId != s.preferredId));
    }

    public Event location(long now, double lat, double lon, float accuracy, long ageMs, int radiusM) {
        if (ageMs < 0 || ageMs > MAX_FIX_AGE_MS || accuracy < 0 || accuracy > 250 ||
            !Double.isFinite(lat) || !Double.isFinite(lon) || !Float.isFinite(accuracy) ||
            Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
        double distance = distanceMeters(lat, lon, portLat, portLon);
        if (distance - accuracy > radiusM + 500) portInside = false;
        if (distance + accuracy > radiusM || portInside || now < snoozeUntil) return null;
        portInside = true;
        if (lastPortAt >= 0 && now - lastPortAt < PORT_COOLDOWN_MS) return null;
        lastPortAt = now;
        return new Event("port", "", "luohu", false, false);
    }

    public static String normalize(String value) {
        return "cn".equalsIgnoreCase(value) ? "cn" : "hk".equalsIgnoreCase(value) ? "hk" : "";
    }
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
    }
}
