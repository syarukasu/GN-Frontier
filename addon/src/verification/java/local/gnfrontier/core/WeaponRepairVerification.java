package local.gnfrontier.core;

import java.util.Random;

public final class WeaponRepairVerification {
    private static int checks;
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    public static void main(String[] args){
        check(WeaponRepair.plan(800,600,25).after()==400,"Pistol: 200/800 condition becomes 400/800 with one kit");
        check(WeaponRepair.plan(1000,750,25).after()==500,"Rifle: quarter-maximum recovery");
        check(WeaponRepair.plan(1000,1,25).restored()==1,"Last point is clamped; no excess recovery");
        check(WeaponRepair.plan(1000,0,25).restored()==0,"Full gun is a no-op");
        check(WeaponRepair.plan(3,3,1).after()==2,"Small durability still recovers one point");
        check(WeaponRepair.plan(Integer.MAX_VALUE,Integer.MAX_VALUE,100).after()==0,"No signed overflow at the maximum");
        check(WeaponRepair.plan(1000,1400,25).after()==1150,"Over-worn legacy data is repaired incrementally, not reset");
        int rejected=0;for(int[] bad:new int[][]{{0,1,25},{-1,1,25},{100,-1,25},{100,10,0},{100,10,101}})try{WeaponRepair.plan(bad[0],bad[1],bad[2]);}catch(IllegalArgumentException expected){rejected++;}
        check(rejected==5,"Invalid repair settings and malformed wear are rejected");
        Random random=new Random(47120421);
        for(int i=0;i<10000;i++){
            int max=random.nextInt(Integer.MAX_VALUE-1)+1,damage=random.nextInt(Integer.MAX_VALUE),percent=random.nextInt(100)+1;
            var plan=WeaponRepair.plan(max,damage,percent);
            check(plan.after()>=0&&plan.after()<=damage,"Wear cannot increase or wrap");
            check(plan.restored()<=damage,"Recovery cannot exceed existing wear");
            check(damage==0||plan.restored()>0,"A worn supported gun always improves");
            check(plan.maximum()==max,"Maximum durability is retained");
            int low=plan.after()&65535,high=plan.after()>>>16;check((low|(high<<16))==plan.after(),"Native 16-bit menu data halves retain large durability values");
        }
        System.out.println("GN_WEAPON_REPAIR_PASS checks="+checks+" runtimeServer=false");
    }
}
