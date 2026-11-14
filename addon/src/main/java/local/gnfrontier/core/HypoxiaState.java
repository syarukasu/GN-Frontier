package local.gnfrontier.core;
/** One step per second. Recovery requires continuous breathable air, not a momentary surface touch. */
public final class HypoxiaState {
    public int debt,safeSeconds;
    public boolean collapsed;
    public void step(boolean dangerous,boolean critical) {
        if(dangerous){debt=Math.min(30,debt+(critical?2:1));safeSeconds=0;}
        else {debt=Math.max(0,debt-2);safeSeconds=Math.min(5,safeSeconds+1);}
        if(debt>=8)collapsed=true;
        if(collapsed && !dangerous && safeSeconds>=5){collapsed=false;debt=0;}
    }
}
