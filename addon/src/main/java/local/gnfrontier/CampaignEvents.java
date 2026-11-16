package local.gnfrontier;

import local.gnfrontier.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class CampaignEvents {
    private static final Map<UUID,Entity> raids=new HashMap<>();
    public static void track(Entity entity){raids.put(entity.getUUID(),entity);}
    @SubscribeEvent public void joined(EntityJoinLevelEvent e){if(!e.getLevel().isClientSide && e.getEntity().getPersistentData().getBoolean("gnfrontier_raid"))track(e.getEntity());}
    @SubscribeEvent public void left(EntityLeaveLevelEvent e){raids.remove(e.getEntity().getUUID());}
    @SubscribeEvent public void started(ServerStartedEvent e) {
        if(WorldState.get(e.getServer().overworld()).enabled){RaidBridge.initialize();AtmosphereOverrides.refresh(e.getServer());}
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(e.getEntity() instanceof ServerPlayer p)AtmosphereOverrides.send(p);
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent e){AtmosphereOverrides.reset();raids.clear();}
    public static void vehicleDestroyed(Entity vehicle,DamageSource source,long stamp) {
        if(!(vehicle.level() instanceof ServerLevel level) || !WorldState.get(level).enabled || source==null
            || !(source.getEntity() instanceof ServerPlayer p) || p.isCreative() || level.getGameTime()-stamp>40 || level.getGameTime()<stamp)return;
        // Exclude player-operated vehicles. Combined Arms records its last NPC driver on the vehicle.
        boolean military=vehicle.getPersistentData().getBoolean("gnfrontier_raid");
        try {
            Object driver=vehicle.getClass().getMethod("getLastDriver").invoke(vehicle);
            if(driver instanceof Player)return;
            if(driver instanceof Entity entity) {
                ResourceLocation id=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
                military|=id!=null && id.getNamespace().equals("simpleenemymod");
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Vehicle attribution API changed",e);}
        if(!military)return;
        String id=Objects.toString(ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType()),"");
        Campaign.Kind kind=null;
        if(id.contains("boat") || id.contains("ship") || id.endsWith(":type_22"))kind=Campaign.Kind.SHIP;
        if(id.contains("ah_6") || id.contains("ah_64") || id.contains("mi_24") || id.contains("mi_28") || id.contains("helicopter") || id.contains("uh_60"))kind=Campaign.Kind.HELICOPTER;
        if(kind!=null)credit(p,kind,vehicle.getUUID(),id);
    }
    private static void credit(ServerPlayer p,Campaign.Kind kind,UUID target,String entityId) {
        WorldState w=WorldState.get(p.serverLevel());Campaign.State c=w.refreshCampaign(p.getUUID(),p.serverLevel());
        if(Campaign.credit(c,kind,target,entityId)){w.setDirty();p.sendSystemMessage(Component.literal("PDA：討伐依頼の進捗を更新しました"));}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void killed(LivingDeathEvent e) {
        if(!e.isCanceled() && e.getSource().getEntity() instanceof ServerPlayer p
            && WorldState.get(p.serverLevel()).enabled) {
            ResourceLocation id=ForgeRegistries.ENTITY_TYPES.getKey(e.getEntity().getType());
            if(id==null)return;
            if(!p.isCreative()&&!p.isSpectator()&&!e.getEntity().isAlliedTo(p)
                    &&!(e.getEntity() instanceof net.minecraft.world.entity.TamableAnimal pet&&pet.isTame())){
                if(e.getEntity() instanceof Mob mob&&FactionHostility.force(mob)){
                    if(FactionRelations.hostileTo(mob,p))credit(p,Campaign.Kind.SOLDIER,e.getEntity().getUUID(),id.toString());
                }else if(e.getEntity() instanceof net.minecraft.world.entity.monster.Monster)credit(p,Campaign.Kind.HUNT,e.getEntity().getUUID(),id.toString());
            }
            // Determine bounty eligibility before this kill changes the relationship.
            FactionRelations.recordAggression(e.getEntity(),p);
        }
    }
    @SubscribeEvent public void crafted(PlayerEvent.ItemCraftedEvent e) {
        if(!(e.getEntity() instanceof ServerPlayer p) || !WorldState.get(p.serverLevel()).enabled)return;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(e.getCrafting().getItem());if(id==null)return;
        WorldState w=WorldState.get(p.serverLevel());Campaign.State c=w.campaign(p.getUUID());
        if(Set.of("create:encased_fan","tfmg:steel_ingot","tfmg:diesel_bucket","ad_astra:tier_1_rocket","gnfrontier:mining_rig").contains(id.toString())) {
            if(c.milestones.add(id.toString()))p.sendSystemMessage(Component.literal("PDA：進行記録 "+e.getCrafting().getHoverName().getString()));w.setDirty();
        }
    }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if(e.getEntity() instanceof ServerPlayer p && WorldState.get(p.serverLevel()).enabled) {
            WorldState w=WorldState.get(p.serverLevel());w.campaign(p.getUUID()).milestones.add("visit:"+e.getTo().location());w.setDirty();AtmosphereOverrides.send(p);
        }
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;long now=e.getServer().overworld().getGameTime();if(now%200!=0)return;
        WorldState w=WorldState.get(e.getServer().overworld());if(!w.enabled)return;
        // Visit only our tracked loaded reinforcements. A player-captured vehicle is retained.
        for(Entity entity:new ArrayList<>(raids.values())) {
            if(entity.isRemoved()){raids.remove(entity.getUUID());continue;}
            boolean captured=entity.getPassengers().stream().anyMatch(x->x instanceof Player);
            if(!captured && entity.getType().getDescriptionId().startsWith("entity.superbwarfare.")) {
                try{captured=entity.getClass().getMethod("getLastDriver").invoke(entity) instanceof Player;}catch(ReflectiveOperationException ignored){}
            }
            if(captured){entity.getPersistentData().remove("gnfrontier_raid");raids.remove(entity.getUUID());continue;}
            if(now>=entity.getPersistentData().getLong("gnfrontier_raid_expires")
                && entity.level().getNearestPlayer(entity,64)==null)entity.discard();
        }
        for(ServerPlayer p:e.getServer().getPlayerList().getPlayers()) {
            if(!p.isAlive() || p.isSpectator() || p.isCreative())continue;
            IndustrialProgress.steelUnlocked(p);
            Campaign.State c=w.refreshCampaign(p.getUUID(),p.serverLevel());Economy.State account=w.account(p.getUUID());
            if(c.nextInterference==0)c.nextInterference=now+24000+p.getRandom().nextInt(48000);
            if(now>=c.nextInterference) {
                c.jamUntil=now+2400+p.getRandom().nextInt(3600);c.nextInterference=c.jamUntil+24000+p.getRandom().nextInt(48000);
                if(!c.satellite)p.sendSystemMessage(Component.literal("PDA：衛星回線に障害。数分後に再接続します"));
            }
            w.setDirty();
            if(!PrototypeConfig.RAIDS.get() || now<c.nextRaid || p.level().dimension()!=Level.OVERWORLD
                || p.level().getDifficulty()==Difficulty.PEACEFUL || !p.level().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING))continue;
            int faction=account.alert[0]>=account.alert[1]?0:1,tier=Campaign.raidTier(account.alert[faction]);
            if(tier==0 || Campaign.deterrenceOnline(c,now))continue;
            var nearby=p.serverLevel().getEntities(p,p.getBoundingBox().inflate(128),entity->entity.getPersistentData().getBoolean("gnfrontier_raid"));
            int cap=PrototypeConfig.RAID_CAP.get();
            if(nearby.size()+2*tier+4>cap || raids.size()+2*tier+4>64){c.nextRaid=now+1200;continue;}
            BlockPos pos=raidPosition(p);if(pos==null){c.nextRaid=now+1200;continue;}
            int spawned=0;for(int i=0;i<2*tier;i++) {
                BlockPos point=pos.offset((i%3)*3,0,(i/3)*3);
                if(Gameplay.protectedAt(p.serverLevel(),point))continue;
                if(RaidBridge.spawn(p,point,faction,"infantry")!=null)spawned++;
            }
            if(tier>=2 && RaidBridge.spawn(p,pos.offset(10,0,0),faction,tier>=3 && MilitarySites.near(p.serverLevel(),pos.offset(10,0,0))?"rotor":"ground")!=null)spawned++;
            c.nextRaid=now+PrototypeConfig.RAID_INTERVAL.get();
            if(spawned>0)p.sendSystemMessage(Component.literal("PDA："+(faction==0?"東側":"西側")+"の増援が接近。警戒段階 "+tier));
        }
    }
    private static BlockPos raidPosition(ServerPlayer p) {
        ServerLevel l=p.serverLevel();for(int attempt=0;attempt<8;attempt++) {
            double angle=p.getRandom().nextDouble()*Math.PI*2;int x=p.getBlockX()+(int)(Math.cos(angle)*56),z=p.getBlockZ()+(int)(Math.sin(angle)*56);
            boolean loaded=true;for(int dx=-32;dx<=32;dx+=16)for(int dz=-32;dz<=32;dz+=16)loaded&=l.hasChunk((x+dx)>>4,(z+dz)>>4);
            if(!loaded)continue;BlockPos pos=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(x,0,z));
            if(!l.getWorldBorder().isWithinBounds(pos) || Gameplay.protectedAt(l,pos) || !l.getFluidState(pos.below()).isEmpty() || !l.getBlockState(pos).isAir())continue;
            if(!l.getEntitiesOfClass(Player.class,new net.minecraft.world.phys.AABB(pos).inflate(24)).isEmpty())continue;return pos;
        }return null;
    }
}
