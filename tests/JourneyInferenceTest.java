package cn.shenlink.guard;
public class JourneyInferenceTest {
    static int count;
    static void equal(int want,int got,String label){count++;if(want!=got)throw new AssertionError(label+": wanted "+want+", got "+got);}
    static void check(boolean ok,String label){count++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        JourneyInference j=new JourneyInference();
        equal(-1,j.accept(10000,22.5319,114.1144,20,0),"first fix only creates candidate");
        equal(-1,j.accept(10000,22.5319,114.1144,20,0),"duplicate fix cannot confirm");
        equal(0,j.accept(16000,22.5319,114.1144,20,0),"second separated fix confirms Luohu");
        equal(-1,j.accept(25000,22.5319,114.1144,20,0),"repeat fixes do not duplicate node");
        equal(-1,j.accept(30000,22.5370,114.1290,500,0),"inaccurate fix rejected");
        equal(-1,j.accept(30000,22.5370,114.1290,20,120001),"stale fix rejected");
        equal(-1,j.accept(30000,Double.NaN,114.1290,20,0),"invalid coordinates rejected");
        equal(-1,j.accept(30000,22.5370,114.1290,Float.NaN,0),"invalid accuracy rejected");
        equal(-1,j.accept(30000,22.5370,114.1290,20,0),"first Wenjindu fix");
        equal(1,j.accept(37000,22.5370,114.1290,20,0),"Wenjindu is distinct from Luohu");
        equal(-1,j.accept(45000,22.5012,114.1280,20,0),"first Sheung Shui fix");
        equal(2,j.accept(53000,22.5012,114.1280,20,0),"Sheung Shui confirmation");
        equal(-1,j.accept(70000,22.4130,114.2101,20,0),"first University fix");
        equal(3,j.accept(78000,22.4130,114.2101,20,0),"University confirmation");
        JourneyInference gap=new JourneyInference();gap.accept(10000,22.5319,114.1144,20,0);
        equal(-1,gap.accept(200000,22.5319,114.1144,20,0),"long gap resets candidate");
        check(JourneyInference.movement(10).contains("请手动确认"),"vehicle movement is not taxi classification");
        check(JourneyInference.movement(-1).contains("待确认"),"missing speed is unknown");
        System.out.println("PASS: "+count+" journey inference checks");
    }
}
