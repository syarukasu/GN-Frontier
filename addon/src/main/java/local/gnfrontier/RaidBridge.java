package local.gnfrontier;

import java.lang.reflect.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;

/** Public Combined Arms spawners supply their normal armed crew, ammunition and vehicle AI. */
public final class RaidBridge {
    private static Class<?> factionClass;
    private static Method infantry,ground,rotor;
    private static Object east,west;
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void initialize() {
        try {
            factionClass=Class.forName("com.neoalive.tacz_sewv.spawn.TankSpawner$TankFaction");
            east=Enum.valueOf((Class)factionClass,"RU");west=Enum.valueOf((Class)factionClass,"US");
            Class<?> tank=Class.forName("com.neoalive.tacz_sewv.spawn.TankSpawner");
            infantry=Class.forName("com.neoalive.tacz_sewv.spawn.InfantrySpawner").getMethod("spawn",ServerLevel.class,BlockPos.class,factionClass,UUID.class,int.class);
            ground=tank.getMethod("spawnGroundWithCrew",ServerLevel.class,BlockPos.class,factionClass,UUID.class);
            rotor=tank.getMethod("spawnRotorWithCrew",ServerLevel.class,BlockPos.class,factionClass,UUID.class);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Combined Arms 0.1.7 public spawner changed",e);}
    }
    public static Entity spawn(ServerPlayer target,BlockPos pos,int faction,String kind) {
        if(infantry==null)initialize();
        try {
            Object f=faction==0?east:west;ServerLevel level=target.serverLevel();
            Object result=kind.equals("infantry")?infantry.invoke(null,level,pos,f,null,0)
                :(kind.equals("rotor")?rotor:ground).invoke(null,level,pos,f,null);
            if(!(result instanceof Entity entity))return null;
            long expires=level.getGameTime()+12000;
            mark(entity,target,expires,faction);for(Entity crew:entity.getPassengers())mark(crew,target,expires,faction);
            return entity;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Faction reinforcement could not be spawned",e);}
    }
    private static void mark(Entity entity,ServerPlayer target,long expires,int faction) {
        var n=entity.getPersistentData();n.putBoolean("gnfrontier_raid",true);n.putLong("gnfrontier_raid_expires",expires);
        n.putUUID("gnfrontier_raid_target",target.getUUID());n.putInt("gnfrontier_faction",faction);
        if(entity instanceof Mob mob)mob.setTarget(target);
        CampaignEvents.track(entity);
    }
}
