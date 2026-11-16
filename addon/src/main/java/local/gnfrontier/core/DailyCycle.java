package local.gnfrontier.core;

import net.minecraft.nbt.CompoundTag;

/** Monotonic contract days, advanced by natural Overworld dawns. Time commands never reroll a board. */
public final class DailyCycle {
    private boolean initialized;
    private long day,gameTime,dayTime;
    public long day(){return day;}
    public boolean initialized(){return initialized;}
    public long remaining(){return Economy.DAY_TICKS-Math.floorMod(dayTime,Economy.DAY_TICKS);}
    public void observe(long game,long sun,long legacyDay){
        if(!initialized){initialized=true;day=Math.max(0,legacyDay);}
        else{
            long elapsed=game-gameTime,solarElapsed=sun-dayTime;
            if(elapsed>0&&solarElapsed==elapsed){
                long dawns=Math.floorDiv(sun,Economy.DAY_TICKS)-Math.floorDiv(dayTime,Economy.DAY_TICKS);
                if(dawns>0)day=Math.addExact(day,dawns);
            }
        }
        gameTime=game;dayTime=sun;
    }
    public CompoundTag save(){var n=new CompoundTag();n.putBoolean("initialized",initialized);n.putLong("day",day);n.putLong("gameTime",gameTime);n.putLong("dayTime",dayTime);return n;}
    public static DailyCycle load(CompoundTag n){
        var clock=new DailyCycle();clock.initialized=n.getBoolean("initialized");clock.day=Math.max(0,n.getLong("day"));clock.gameTime=n.getLong("gameTime");clock.dayTime=n.getLong("dayTime");return clock;
    }
}
