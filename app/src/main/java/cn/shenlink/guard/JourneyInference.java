package cn.shenlink.guard;

/** Conservative landmark confirmation, never fare, border crossing, or bus-versus-taxi classification. */
final class JourneyInference {
    static final String[] NAMES={"罗湖附近","文锦渡附近","上水站附近","大学站附近"};
    static final double[] LAT={22.5319,22.5370,22.5012,22.4130};
    static final double[] LON={114.1144,114.1290,114.1280,114.2101};
    private int candidate=-1,confirmed=-1;
    private long candidateAt=-1,lastFix=-1;
    int accept(long elapsed,double lat,double lon,float accuracy,long age){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat < -90||lat > 90||lon < -180||lon > 180||!Float.isFinite(accuracy)||accuracy<0||accuracy>150||age<0||age>120000)return -1;
        long fix=elapsed-age;if(fix<=lastFix)return -1;
        if(lastFix>=0&&fix-lastFix>120000){candidate=-1;candidateAt=-1;}
        lastFix=fix;int closest=-1;double best=Double.MAX_VALUE;
        for(int i=0;i<NAMES.length;i++){double dist=ReminderEngine.distanceMeters(lat,lon,LAT[i],LON[i]);if(dist<best){best=dist;closest=i;}}
        if(best+accuracy>400){candidate=-1;candidateAt=-1;if(best>650)confirmed=-1;return -1;}
        if(candidate!=closest){candidate=closest;candidateAt=fix;return -1;}
        if(fix-candidateAt<5000||confirmed==closest)return -1;
        confirmed=closest;return closest;
    }
    static String movement(float speed){return !Float.isFinite(speed)||speed<0?"交通方式待确认":speed>=3.5f?"可能正在乘车 · 公交或打车请手动确认":speed>=0.6f?"正在缓慢移动 · 交通方式待确认":"停留或缓慢移动";}
}
