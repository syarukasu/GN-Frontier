package local.gnfrontier;

import java.util.UUID;
import local.gnfrontier.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Pillager;

/** Player ownership is checked even when the owner is offline or the soldier changes targets. */
public final class FactionRelations {
    private static final ClassValue<java.util.Optional<java.lang.reflect.Method>> OWNER=new ClassValue<>(){
        @Override protected java.util.Optional<java.lang.reflect.Method> computeValue(Class<?> type){try{return java.util.Optional.of(type.getMethod("getOwnerUUID"));}catch(NoSuchMethodException absent){return java.util.Optional.empty();}}
    };
    private FactionRelations() {}
    public static boolean owned(Mob mob){
        try{var getter=OWNER.get(mob.getClass());return getter.isPresent()&&getter.get().invoke(mob)!=null;}
        catch(ReflectiveOperationException error){throw new IllegalStateException("PMC ownership API changed",error);}
    }
    public static boolean hostileTo(Mob mob,UUID owner,Economy.State account){
        int faction=FactionHostility.faction(mob);
        if(faction==0||faction==1){
            var relation=FactionStanding.relation(account,faction);
            if(relation==FactionStanding.Relation.ALLY)return false;
            if(relation==FactionStanding.Relation.ENEMY)return true;
        }else if(faction==2){
            // Hired companions are not autonomous bed raiders or generic hostile bounties.
            if(owned(mob))return false;
            return true;
        }else if(mob instanceof Pillager&&mob.getPersistentData().getBoolean("gnfrontier_bandit"))return true;
        else return false;
        var data=mob.getPersistentData();
        return data.hasUUID("gnfrontier_raid_target")&&data.getUUID("gnfrontier_raid_target").equals(owner)
                ||mob.getTarget()!=null&&mob.getTarget().getUUID().equals(owner);
    }
    public static boolean hostileTo(Mob mob,ServerPlayer player){
        return hostileTo(mob,player.getUUID(),WorldState.get(player.serverLevel()).account(player.getUUID()));
    }
    public static void recordAggression(LivingEntity victim,ServerPlayer killer){
        int faction=FactionHostility.faction(victim);
        if(faction<0||faction>1)return;
        WorldState world=WorldState.get(killer.serverLevel());var account=world.account(killer.getUUID());
        Economy.aggression(account,faction==0?Economy.Faction.EAST:Economy.Faction.WEST);
        world.setDirty();DiplomacyBridge.sync(killer,account);
    }
}
