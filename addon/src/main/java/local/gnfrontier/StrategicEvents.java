package local.gnfrontier;
import local.gnfrontier.core.Campaign;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

public final class StrategicEvents {
    private static boolean military(Mob mob){var id=ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());return id!=null&&id.getNamespace().equals("simpleenemymod");}
    public static boolean protectedPlayer(ServerPlayer p){var w=WorldState.get(p.serverLevel());return w.enabled && Campaign.deterrenceOnline(w.campaign(p.getUUID()),p.serverLevel().getServer().overworld().getGameTime());}
    @SubscribeEvent public void target(LivingChangeTargetEvent e){if(e.getEntity() instanceof Mob mob && military(mob)&&e.getNewTarget() instanceof ServerPlayer p&&protectedPlayer(p))e.setNewTarget(null);}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||e.getServer().overworld().getGameTime()%20!=0)return;
        for(ServerPlayer p:e.getServer().getPlayerList().getPlayers())if(protectedPlayer(p))
            for(Mob mob:p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(128),m->military(m)&&m.getTarget()==p)){mob.setTarget(null);mob.getNavigation().stop();}}
    @SubscribeEvent public void placed(BlockEvent.EntityPlaceEvent e){if(e.isCanceled()||!(e.getLevel() instanceof net.minecraft.server.level.ServerLevel l)||!(e.getEntity() instanceof Player)||!l.dimension().location().getNamespace().equals("ad_astra"))return;
        WorldState w=WorldState.get(l);if(w.enabled){w.placedBlocks.add(Gameplay.key(l,e.getPos()));w.setDirty();}}
}
