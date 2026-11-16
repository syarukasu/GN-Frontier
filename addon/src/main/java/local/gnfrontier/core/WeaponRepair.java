package local.gnfrontier.core;

/** Integer durability planning; one kit restores a bounded fraction of maximum durability. */
public final class WeaponRepair {
    private WeaponRepair(){}
    public record Plan(int before,int after,int maximum){
        public int restored(){return before-after;}
    }
    public static Plan plan(int maximum,int damage,int percent){
        if(maximum<=0||damage<0||percent<1||percent>100)throw new IllegalArgumentException("Invalid repair parameters");
        long amount=Math.max(1,((long)maximum*percent+99)/100);
        return new Plan(damage,(int)Math.max(0,(long)damage-amount),maximum);
    }
}
