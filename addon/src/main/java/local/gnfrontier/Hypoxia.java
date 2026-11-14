package local.gnfrontier;

import local.gnfrontier.core.HypoxiaState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

public final class Hypoxia {
    private static final String KEY="gnfrontier_hypoxia";
    public static boolean down(Player p){return p.getPersistentData().getCompound(KEY).getBoolean("collapsed");}
    public static void step(ServerPlayer p) {
        CompoundTag n=p.getPersistentData().getCompound(KEY);HypoxiaState s=new HypoxiaState();
        s.debt=n.getInt("debt");s.safeSeconds=n.getInt("safe");s.collapsed=n.getBoolean("collapsed");boolean before=s.collapsed;
        boolean enabled=PrototypeConfig.HYPOXIA.get() && WorldState.get(p.serverLevel()).enabled && p.isAlive()&&!p.isCreative()&&!p.isSpectator();
        boolean danger=enabled && p.isUnderWater()&&p.getAirSupply()<=80&&!p.hasEffect(MobEffects.WATER_BREATHING)&&!p.hasEffect(MobEffects.CONDUIT_POWER);
        if(enabled)s.step(danger,p.getAirSupply()<=0);else{s.collapsed=false;s.debt=0;s.safeSeconds=0;}
        n.putInt("debt",s.debt);n.putInt("safe",s.safeSeconds);n.putBoolean("collapsed",s.collapsed);
        if(!before && s.collapsed){n.putDouble("x",p.getX());n.putDouble("z",p.getZ());p.stopUsingItem();p.closeContainer();p.stopRiding();}
        p.getPersistentData().put(KEY,n);
        if(s.collapsed){p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,40,0,false,false));p.setForcedPose(Pose.SWIMMING);}
        else if(before)p.setForcedPose(null);
        if(s.debt>0 || before || s.collapsed)FrontierNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new FrontierNetwork.Condition(p.getId(),s.collapsed,s.debt,s.safeSeconds));
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.player instanceof ServerPlayer p))return;
        if(p.tickCount%20==0)step(p);
        if(!down(p))return;
        p.setSprinting(false);p.stopUsingItem();CompoundTag n=p.getPersistentData().getCompound(KEY);
        double x=n.getDouble("x"),z=n.getDouble("z");
        if((p.getX()-x)*(p.getX()-x)+(p.getZ()-z)*(p.getZ()-z)>256){n.putDouble("x",p.getX());n.putDouble("z",p.getZ());return;}
        p.setDeltaMovement(0,Math.min(p.getDeltaMovement().y, p.isUnderWater()?-.015:0),0);
        if(p.tickCount%5==0 && (Math.abs(p.getX()-x)>.02 || Math.abs(p.getZ()-z)>.02))p.connection.teleport(x,p.getY(),z,p.getYRot(),p.getXRot());
    }
    @SubscribeEvent public void interact(PlayerInteractEvent e){if(e.isCancelable()&&down(e.getEntity())){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);}}
    @SubscribeEvent public void attack(AttackEntityEvent e){if(down(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public void damage(LivingAttackEvent e){if(e.getSource().getEntity() instanceof ServerPlayer p && down(p))e.setCanceled(true);}
    @SubscribeEvent public void tracking(PlayerEvent.StartTracking e){if(e.getEntity() instanceof ServerPlayer watcher && e.getTarget() instanceof Player target){var n=target.getPersistentData().getCompound(KEY);FrontierNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->watcher),new FrontierNetwork.Condition(target.getId(),n.getBoolean("collapsed"),n.getInt("debt"),n.getInt("safe")));}}
    @SubscribeEvent public void clone(PlayerEvent.Clone e){e.getEntity().getPersistentData().remove(KEY);e.getEntity().setForcedPose(null);}
}
