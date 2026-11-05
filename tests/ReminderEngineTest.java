package cn.shenlink.guard;

public final class ReminderEngineTest {
    private static int checks;
    private static void ok(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static ReminderEngine.Sample sample(long time,String country,int def,int active,int preferred,boolean configured) {
        return new ReminderEngine.Sample(time,country,def,active,preferred,configured);
    }
    private static ReminderEngine ready(String country,int def,int active,int preferred,boolean configured) {
        ReminderEngine e=new ReminderEngine();
        e.evaluate(sample(0,country,def,active,preferred,configured));
        return e;
    }
    public static void main(String[] args) {
        ReminderEngine e=ready("cn",2,2,1,true);
        ok(e.evaluate(sample(14_999,"cn",2,2,1,true))==null,"Do not fire before debounce");
        ReminderEngine.Event event=e.evaluate(sample(15_000,"cn",2,2,1,true));
        ok(event!=null && event.activeWrong && event.defaultWrong,"HK data SIM on mainland network must warn");
        ok(e.evaluate(sample(20_000,"cn",2,2,1,true))==null,"Do not repeat every poll");
        ok(e.evaluate(sample(195_000,"cn",2,2,1,true))!=null,"Unresolved mismatch repeats after 3 minutes");
        ok(e.evaluate(sample(200_000,"cn",1,1,1,true))==null,"Switching to configured card resolves risk");
        ok(!e.isMismatch(sample(200_000,"cn",1,1,1,true)),"Resolved check");
        ok(e.evaluate(sample(210_000,"cn",2,2,1,true))!=null,"New mismatch after resolution alerts");
        e=ready("hk",1,1,2,true);
        ok(e.evaluate(sample(15_000,"hk",1,1,2,true))!=null,"Reverse direction is supported");
        e=ready("cn",2,1,1,true);
        event=e.evaluate(sample(15_000,"cn",2,1,1,true));
        ok(event!=null && !event.activeWrong && event.defaultWrong,"Wrong default still matters under automatic data switching");
        e=ready("cn",1,2,1,true);
        event=e.evaluate(sample(15_000,"cn",1,2,1,true));
        ok(event!=null && event.activeWrong && !event.defaultWrong,"Wrong active SIM matters even with correct default");
        e=ready("cn",2,2,-1,false);
        ok(e.evaluate(sample(15_000,"cn",2,2,-1,false))==null,"Unconfigured startup cannot assert wrong SIM");
        ok(e.evaluate(sample(30_000,"hk",2,2,-1,false))==null,"Country transition settles");
        event=e.evaluate(sample(45_000,"hk",2,2,-1,false));
        ok(event!=null && "network".equals(event.kind),"Unconfigured country change gets generic reminder");
        e=ready("cn",1,1,1,true);
        e.evaluate(sample(15_000,"cn",1,1,1,true));
        e.evaluate(sample(20_000,"hk",1,1,2,true));
        e.evaluate(sample(25_000,"cn",1,1,1,true));
        ok(e.evaluate(sample(40_000,"cn",1,1,1,true))==null,"Short cross-border cell bounce is ignored");
        e.evaluate(sample(45_000,"hk",1,1,2,true));
        e.evaluate(sample(50_000,"",1,1,2,true));
        ok(e.evaluate(sample(65_000,"hk",1,1,2,true))==null,"Signal loss resets pending transition");
        ok(e.evaluate(sample(80_000,"hk",1,1,2,true))!=null,"Restored network settles before warning");
        e=ready("cn",2,2,1,true); e.snooze(10_000,600_000);
        ok(e.evaluate(sample(15_000,"cn",2,2,1,true))==null,"User snooze suppresses risk");
        ok(e.evaluate(sample(610_000,"cn",2,2,1,true))!=null,"Risk resumes after snooze");
        e=ready("cn",-1,-1,1,true);
        ok(e.evaluate(sample(15_000,"cn",-1,-1,1,true))==null,"Unknown data IDs cannot assert mismatch");
        e=ready("cn",2,2,1,false);
        ok(e.evaluate(sample(15_000,"cn",2,2,1,false))==null,"Removed or disabled preferred SIM invalidates mapping");
        e=ready("hk",2,2,2,true);
        ok(e.evaluate(sample(15_000,"hk",2,2,2,true))==null,"Correct SIM is quiet");
        e=ready("cn",1,1,1,true);e.evaluate(sample(15_000,"cn",1,1,1,true));
        e.evaluate(sample(20_000,"hk",1,1,1,true));
        ok(e.evaluate(sample(35_000,"hk",1,1,1,true))==null,"Same cross-border plan can be selected for both regions");
        ok("".equals(ReminderEngine.normalize("us")),"Other countries are not inferred as CN or HK");
        ok("hk".equals(ReminderEngine.normalize("HK")),"Country normalization");
        e=new ReminderEngine(); double lat=ReminderEngine.LUOHU_LAT,lon=ReminderEngine.LUOHU_LON;
        ok(e.location(100_000,lat,lon,10,121_000,1200)==null,"Stale positions cannot alert");
        ok(e.location(100_000,lat,lon,800,0,1200)==null,"Coarse positions cannot alert");
        ok(e.location(100_000,lat,lon,Float.NaN,0,1200)==null,"Invalid accuracy cannot alert");
        ok(e.location(100_000,Double.NaN,lon,10,0,1200)==null,"Invalid latitude cannot alert");
        ok(e.location(100_000,lat,lon,10,-1,1200)==null,"Future fixes cannot alert");
        ok(e.location(100_000,lat+0.05,lon,10,0,1200)==null,"Far from Luohu stays quiet");
        ok(e.location(100_000,lat,lon,10,0,1200)!=null,"Arrival at Luohu alerts even on first valid fix");
        ok(e.location(200_000,lat,lon,10,0,1200)==null,"Staying at checkpoint does not spam");
        e.location(250_000,lat+0.03,lon,10,0,1200);
        ok(e.location(300_000,lat,lon,10,0,1200)==null,"Exit/reentry cooldown prevents GPS bounce");
        e.location(1_900_001,lat+0.03,lon,10,0,1200);
        ok(e.location(1_900_002,lat,lon,10,0,1200)!=null,"Later return trip alerts again");
        e=new ReminderEngine();e.snooze(0,600_000);
        ok(e.location(100_000,lat,lon,10,0,1200)==null,"Snooze also covers location alerts");
        ok(e.location(610_000,lat,lon,10,0,1200)!=null,"Location reminder resumes after snooze");
        e=new ReminderEngine();e.restorePortTime(50_000);
        ok(e.location(100_000,lat,lon,10,0,1200)==null,"Restart preserves checkpoint cooldown");
        ok(ReminderEngine.distanceMeters(lat,lon,lat,lon)<0.001,"Distance at center");
        ok(ReminderEngine.distanceMeters(lat,lon,lat+0.01,lon)>1100 && ReminderEngine.distanceMeters(lat,lon,lat+0.01,lon)<1120,"Distance conversion sanity check");
        System.out.println("PASS: "+checks+" behavioral checks");
    }
}
