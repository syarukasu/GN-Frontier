package local.gnreview;

import java.nio.file.Path;
import java.lang.reflect.*;
import java.util.*;
import local.gnfrontier.*;
import local.gnfrontier.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.fml.ModList;

/** Disposable-world integration fixture. Native goals are advanced synchronously. */
public final class EnhancedAiChecks {
    private static final List<String> passed=new ArrayList<>();
    private static final String BASE="insane96mcp.enhancedai.modules.mobs.";
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label+"; previous="+passed.subList(Math.max(0,passed.size()-4),passed.size()));passed.add(label);}
    private static void owned(ServerPlayer p){if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r22_ai"))throw new IllegalStateException("Owned r22 world required");}
    private static void cmd(ServerPlayer p,String... commands){for(String c:commands)p.getServer().getCommands().performPrefixedCommand(p.getServer().createCommandSourceStack(),c);}
    private static Object field(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static List<Goal> goals(Zombie z){return z.goalSelector.getAvailableGoals().stream().map(w->w.getGoal()).toList();}
    private static Goal goal(Zombie z,String name){return goals(z).stream().filter(g->g.getClass().getSimpleName().equals(name)).findFirst().orElseThrow();}
    private static Goal freshFisher(Zombie z)throws Exception{return (Goal)Class.forName(BASE+"fisher.FishingTargetGoal").getConstructor(Mob.class).newInstance(z);}
    private static Entity hook(Zombie z,ServerPlayer p)throws Exception {
        z.setTarget(p);Goal g=freshFisher(z);check(g.canUse(),"Native fisher can target an unprotected survivor");g.start();g.tick();
        Entity hook=(Entity)field(g,"fishingHook");check(hook!=null&&p.serverLevel().getEntity(hook.getId())==hook,"Native goal spawned its registered fishing hook");
        Method hit=hook.getClass().getDeclaredMethod("m_5790_",EntityHitResult.class);hit.setAccessible(true);hit.invoke(hook,new EntityHitResult(p));
        check(field(hook,"hookedIn")==p,"Native projectile hit handler attached to the player");return hook;
    }
    private static void retrieve(Entity hook,boolean inventory)throws Exception{hook.getClass().getMethod("retrieve",boolean.class).invoke(hook,inventory);}
    private static RespawnPolicy.State noProtection(ServerPlayer p){
        var w=WorldState.get(p.serverLevel());w.enabled=true;w.players.clear();w.respawns.clear();w.players.put(p.getUUID(),new Economy.State());
        var state=new RespawnPolicy.State();state.initialDimension=p.level().dimension().location().toString();state.initialPosition=new BlockPos(120,240,120).asLong();w.respawns.put(p.getUUID(),state);((net.minecraft.world.level.saveddata.SavedData)w).setDirty();return state;
    }
    private static Zombie zombie(ServerPlayer p,int seed,double x,double z){
        Zombie mob=Objects.requireNonNull(EntityType.ZOMBIE.create(p.serverLevel()));mob.getRandom().setSeed(seed);mob.moveTo(x,240,z,0,0);mob.setPersistenceRequired();mob.setNoAi(true);p.serverLevel().addFreshEntity(mob);return mob;
    }
    public static Map<String,Object> verify(ServerPlayer p,Path evidence)throws Exception {
        owned(p);passed.clear();
        check(p.isAlive()&&!p.isDeadOrDying()&&p.deathTime==0,"Fresh living player begins the integration fixture");
        cmd(p,"gamerule doMobSpawning false","gamerule doDaylightCycle false","gamerule doWeatherCycle false","gamerule mobGriefing true","difficulty normal","time set 6000","weather clear","fill -5 239 -5 25 239 25 minecraft:smooth_stone","fill -5 240 -5 25 246 25 minecraft:air","tp @p 0.5 240 6.5");
        p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.getInventory().clearContent();p.setHealth(p.getMaxHealth());
        RespawnPolicy.State state=noProtection(p);
        check(EnhancedAiCompat.present(),"Enhanced AI presence switches GN to native AI");
        check(ModList.get().getModContainerById("enhancedai").orElseThrow().getModInfo().getVersion().toString().equals("3.3.7.5"),"Enhanced AI 3.3.7.5 loaded");
        check(ModList.get().getModContainerById("insanelib").orElseThrow().getModInfo().getVersion().toString().equals("1.23.4.6"),"Required InsaneLib 1.23.4.6 loaded");
        Zombie fisher=null,miner=null,pearler=null;Map<String,Integer> equipment=new TreeMap<>();
        for(int i=0;i<256;i++){
            Zombie z=zombie(p,20261113+i,0.5,-2.5);List<Goal> gs=goals(z);
            check(gs.stream().noneMatch(g->g instanceof RodZombieGoal),"Spawn "+i+": GN fishing goal is absent");
            check(gs.stream().filter(g->g.getClass().getSimpleName().equals("FishingTargetGoal")).count()==1,"Spawn "+i+": one native fishing goal");
            check(gs.stream().filter(g->g instanceof RegisteredBedGoal).count()==1,"Spawn "+i+": one registered-bed goal retained");
            check(z.getMainHandItem().isEmpty(),"Spawn "+i+": GN did not inject a main-hand tool");
            Item item=z.getOffhandItem().getItem();equipment.merge(item.toString(),1,Integer::sum);
            if(item==Items.FISHING_ROD&&fisher==null)fisher=z;
            else if(item==Items.STONE_PICKAXE&&miner==null&&gs.stream().anyMatch(g->g.getClass().getSimpleName().equals("MineTowardsTargetGoal")))miner=z;
            else if(item==Items.ENDER_PEARL&&pearler==null)pearler=z;
            else z.discard();
        }
        check(fisher!=null&&miner!=null&&pearler!=null,"Default native gear produced rod, miner pickaxe and pearls");
        fisher.setTarget(p);Goal fishing=goal(fisher,"FishingTargetGoal");
        check(fishing.canUse(),"Unprotected player can activate native fishing");
        state.initialPosition=p.blockPosition().asLong();check(!fishing.canUse(),"Five-metre spawn protection blocks native fishing acquisition");
        state.initialPosition=new BlockPos(120,240,120).asLong();state.beginGrace(p.getServer().overworld().getGameTime(),200);
        check(!fishing.canUse(),"Respawn time protection blocks native fishing acquisition");state.graceStarted=state.graceUntil=-1;
        p.setGameMode(GameType.CREATIVE);check(!fishing.canUse(),"Creative players cannot be fished");p.setGameMode(GameType.SURVIVAL);
        Entity h=hook(fisher,p);p.setDeltaMovement(Vec3.ZERO);retrieve(h,false);
        check(p.getDeltaMovement().lengthSqr()>0&&h.isRemoved(),"Native retrieval pulls an unprotected player and removes its hook");
        h=hook(fisher,p);state.beginGrace(p.getServer().overworld().getGameTime(),200);p.setDeltaMovement(Vec3.ZERO);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_INGOT,3));
        retrieve(h,true);check(h.isRemoved()&&p.getMainHandItem().getCount()==3&&p.getDeltaMovement().equals(Vec3.ZERO),"Protection gained after casting prevents hand theft and movement");state.graceStarted=state.graceUntil=-1;
        h=hook(fisher,p);state.initialPosition=p.blockPosition().asLong();p.setDeltaMovement(Vec3.ZERO);retrieve(h,false);
        check(h.isRemoved()&&p.getDeltaMovement().equals(Vec3.ZERO),"Entering the protected area after casting cancels retrieval");state.initialPosition=new BlockPos(120,240,120).asLong();
        pearler.setTarget(p);Goal pearl=goal(pearler,"PearlUseGoal");boolean ready=false;for(int i=0;i<60;i++)if(pearl.canUse()){ready=true;break;}
        check(ready,"Native pearl goal activates after its cooldown");int count=pearler.getOffhandItem().getCount();pearl.start();
        Entity projectile=(Entity)field(pearl,"thrownEnderPearl");check(projectile!=null&&projectile.isAlive()&&pearler.getOffhandItem().getCount()==count-1,"Native pearl throw spawns a projectile and consumes one pearl");projectile.discard();pearl.stop();
        miner.setTarget(p);Goal mining=goal(miner,"MineTowardsTargetGoal");BlockPos wall=new BlockPos(0,241,0);
        cmd(p,"fill -2 240 0 2 243 0 minecraft:stone");mining.start();
        check(!((List<?>)field(mining,"targetBlocks")).isEmpty(),"Native miner raycast finds an obstructing wall");
        for(int i=0;i<400&&!p.level().getBlockState(wall).isAir();i++)mining.tick();
        check(p.level().getBlockState(wall).isAir(),"Native mining ticks destroy the actual unprotected wall");mining.stop();
        cmd(p,"fill -2 240 0 2 243 0 minecraft:stone");state.initialPosition=wall.below().asLong();mining.start();
        for(int i=0;i<400;i++)mining.tick();
        check(p.level().getBlockState(wall).is(Blocks.STONE),"GN area protection cancels native miner block destruction");mining.stop();
        state.initialPosition=new BlockPos(120,240,120).asLong();
        cmd(p,"setblock 20 240 21 minecraft:red_bed[facing=north,part=foot]","setblock 20 240 20 minecraft:red_bed[facing=north,part=head]");
        BlockPos head=new BlockPos(20,240,20);check(RespawnRules.registerBed(p,p.serverLevel(),head),"First bed registration succeeds with Enhanced AI present");
        miner.setTarget(null);miner.moveTo(20.5,240,22,0,0);Goal bed=goal(miner,"RegisteredBedGoal");
        check(bed.canUse(),"Native off-hand pickaxe qualifies for the registered-bed goal");bed.start();
        for(int i=0;i<PrototypeConfig.BED_BREAK_TICKS.get()+2;i++)bed.tick();bed.stop();
        check(!RespawnRules.intactBed(p.serverLevel(),head)&&WorldState.get(p.serverLevel()).account(p.getUUID()).spawnDimension.isEmpty(),"Registered-bed AI breaks the bed and invalidates its spawn registration");
        cmd(p,"gamerule mobGriefing false");check(!bed.canUse()&&!mining.canUse(),"mobGriefing=false disables both bed and native mining goals");cmd(p,"gamerule mobGriefing true");
        fisher.discard();miner.discard();pearler.discard();p.setGameMode(GameType.CREATIVE);p.setDeltaMovement(Vec3.ZERO);
        p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Frontier.PDA.get()));
        check(DailyMissions.catalog().variants()==7936,"Generated delivery/hunt catalog still loads all 7936 variants");
        p.getServer().saveEverything(false,true,true);
        return Map.of("passed",List.copyOf(passed),"checks",passed.size(),"joinedZombies",256,"nativeOffhandDistribution",equipment,"scope","Owned integrated client; actual spawn hooks, native goals advanced synchronously, native fishing hit handler invoked; not a long-running multiplayer combat trial");
    }
    public static Map<String,Object> reset(ServerPlayer p,Path evidence){owned(p);cmd(p,"kill @p");return Map.of("awaitingNativeRespawn",true);}
    public static Map<String,Object> stage(ServerPlayer p,Path evidence){
        owned(p);cmd(p,"fill -3 240 0 3 243 0 minecraft:air","tp @p 8.5 240 9.5 180 0");p.setGameMode(GameType.CREATIVE);
        String[] names={"釣り竿を持つゾンビ","採掘するゾンビ","パールを持つゾンビ"};Item[] items={Items.FISHING_ROD,Items.STONE_PICKAXE,Items.ENDER_PEARL};List<String> ids=new ArrayList<>();
        for(int i=0;i<3;i++){Zombie z=zombie(p,10+i,5.5+i*3,5.5);z.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(items[i],1));z.setCustomName(Component.literal(names[i]));z.setCustomNameVisible(true);z.setYRot(0);z.setYHeadRot(0);z.yBodyRot=0;ids.add(z.getUUID().toString());}
        return Map.of("stagedModels",ids,"caption","Actual Enhanced AI equipment; controlled display positions and held items, not random natural-spawn frequency evidence");
    }
}
