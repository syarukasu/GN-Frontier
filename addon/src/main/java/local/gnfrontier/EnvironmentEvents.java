package local.gnfrontier;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import top.theillusivec4.curios.api.CuriosApi;

/** Preserve Anomaly Zone's actual emission state machine; add gas filtration and hypoxia. */
public final class EnvironmentEvents {
    private static final String FILTER="gnfrontier_filter_ticks";
    private static final List<String> EMISSION_FIELDS=List.of("nextEmission","ticker","emissionActive","emissionTicker","emissionDamageTicker",
        "warned1","warned2","forcedPending","artifactWindowActive","artifactWindowTicker","artifactWindowId");
    private static final Map<String,Field> emissionFields=new LinkedHashMap<>();
    private static boolean running;
    private static ItemStack mask(ServerPlayer p) {
        ItemStack head=p.getItemBySlot(EquipmentSlot.HEAD);if(head.is(Frontier.GAS_MASKS))return head;
        return CuriosApi.getCuriosInventory(p).resolve().flatMap(x->x.findFirstCurio(stack->stack.is(Frontier.GAS_MASKS)))
            .map(x->x.stack()).orElse(ItemStack.EMPTY);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void filter(PlayerInteractEvent.RightClickItem e) {
        if(!(e.getEntity() instanceof ServerPlayer p) || !WorldState.get(p.serverLevel()).enabled || !e.getItemStack().is(Frontier.FILTER.get()))return;
        ItemStack mask=mask(p);e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
        if(mask.isEmpty()){p.sendSystemMessage(Component.literal("先に防毒マスクを装備してください"));return;}
        int capacity=PrototypeConfig.FILTER_TICKS.get();
        if(mask.getOrCreateTag().getInt(FILTER)>capacity-200){p.sendSystemMessage(Component.literal("フィルターは十分に残っています"));return;}
        mask.getOrCreateTag().putInt(FILTER,capacity);if(!p.isCreative())e.getItemStack().shrink(1);
        p.getInventory().setChanged();p.sendSystemMessage(Component.literal("フィルターを交換しました"));
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || e.getServer().overworld().getGameTime()%20!=0)return;
        if(!WorldState.get(e.getServer().overworld()).enabled)return;
        for(ServerPlayer p:e.getServer().getPlayerList().getPlayers()) {
            if(!p.isAlive() || p.isCreative() || p.isSpectator())continue;
            if(PrototypeConfig.GAS.get() && p.level().dimension()==Level.OVERWORLD && p.getY()<PrototypeConfig.GAS_BELOW_Y.get()) {
                ItemStack m=mask(p);int left=m.isEmpty()?0:m.getOrCreateTag().getInt(FILTER);
                if(left>=20){m.getOrCreateTag().putInt(FILTER,left-20);if(left==20)p.sendSystemMessage(Component.literal("防毒フィルターが消耗しました"));}
                else {p.addEffect(new MobEffectInstance(MobEffects.POISON,60,0));p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,60,0));
                    p.displayClientMessage(Component.literal(m.isEmpty()?"有毒ガス：防毒マスクが必要です":"有毒ガス：フィルターを交換してください"),true);}
            }
            if(PrototypeConfig.HYPOXIA.get() && p.isUnderWater() && p.getAirSupply()<=80
                && !p.hasEffect(MobEffects.WATER_BREATHING) && !p.hasEffect(MobEffects.CONDUIT_POWER)) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS,60,0));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,p.getAirSupply()<=0?3:1));
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,60,2));
                if(p.getAirSupply()<=0)p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,60,0));
                else p.hurt(p.damageSources().drown(),2);
                p.displayClientMessage(Component.literal("低酸素：浮上してください"),true);
            }
        }
    }
    @SubscribeEvent public void started(ServerStartedEvent e) {
        activate(e.getServer());
    }
    public static void activate(net.minecraft.server.MinecraftServer server) {
        WorldState data=WorldState.get(server.overworld());if(!data.enabled || running)return;
        try {
            Class<?> handler=Class.forName("com.anomalyzone.EmissionHandler");
            for(String name:EMISSION_FIELDS){Field f=handler.getDeclaredField(name);f.setAccessible(true);emissionFields.put(name,f);}
            if(!data.emission.isEmpty())for(var entry:emissionFields.entrySet()) {
                String name=entry.getKey();Field f=entry.getValue();
                if(!data.emission.contains(name))throw new IllegalStateException("Incomplete emission save: "+name);
                if(f.getType()==boolean.class)f.setBoolean(null,data.emission.getBoolean(name));
                else f.setInt(null,data.emission.getInt(name));
            }
            running=true;
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("Anomaly Zone 2.1 emission adapter changed",error);}
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(!(e.getEntity() instanceof ServerPlayer p) || !running)return;
        try {
            boolean active=emissionFields.get("emissionActive").getBoolean(null),warning=emissionFields.get("warned1").getBoolean(null);
            Class<?> sky=Class.forName("com.anomalyzone.EmissionSkyPacket");
            Class<?> network=Class.forName("com.anomalyzone.EmissionNetwork");
            network.getMethod("sendToPlayer",ServerPlayer.class,sky).invoke(null,p,sky.getConstructor(boolean.class,boolean.class).newInstance(active||warning,active));
            if(active||warning) {
                Class<?> type=Class.forName("com.anomalyzone.EmissionSoundPacket$Type");
                Object phase=type.getField(active?"ACTIVE":"WARNING").get(null);
                Object packet=Class.forName("com.anomalyzone.EmissionSoundPacket").getConstructor(type).newInstance(phase);
                ((net.minecraftforge.network.simple.SimpleChannel)network.getField("CHANNEL").get(null)).send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->p),packet);
            }
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Anomaly emission reconnect sync failed",error);}
    }
    public static CompoundTag captureEmission(CompoundTag fallback) {
        if(!running)return fallback;
        try {CompoundTag n=new CompoundTag();for(var entry:emissionFields.entrySet()) {
            Field f=entry.getValue();if(f.getType()==boolean.class)n.putBoolean(entry.getKey(),f.getBoolean(null));else n.putInt(entry.getKey(),f.getInt(null));
        }return n;}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void stopping(ServerStoppingEvent e) {
        WorldState w=WorldState.get(e.getServer().overworld());w.emission=captureEmission(w.emission);w.setDirty();running=false;
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent e) {running=false;emissionFields.clear();}
}
