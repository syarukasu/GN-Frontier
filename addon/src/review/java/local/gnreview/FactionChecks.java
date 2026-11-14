package local.gnreview;

import java.nio.file.Path;
import java.lang.reflect.*;
import java.util.*;
import local.gnfrontier.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

/** Test-only driver, restricted to the disposable r23 world. */
public final class FactionChecks {
    public static Map<String,Object> diagnose(ServerPlayer p,Path evidence)throws Exception{
        owned(p);setup(p);List<Map<String,Object>> rows=new ArrayList<>();
        for(int attempt=0;attempt<20;attempt++){
            clear(p);Mob unit=spawn(p,"tacz_sewv:ru_engineer",.5,6.5,true),monster=spawn(p,"minecraft:zombie",.5,.5,false);unit.setNoAi(true);isolate(p,unit,monster);
            List<String> events=new ArrayList<>();java.util.concurrent.atomic.AtomicBoolean listen=new java.util.concurrent.atomic.AtomicBoolean(true);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,LivingChangeTargetEvent.class,e->{if(listen.get()&&e.getEntity()==unit)events.add("new="+e.getNewTarget()+", cancelled="+e.isCanceled());});
            try{
                for(int i=0;i<512&&unit.getTarget()!=monster;i++){unit.tickCount++;unit.getSensing().tick();unit.targetSelector.tick();}
                if(unit.getTarget()==monster)continue;
                Map<String,Object> row=new LinkedHashMap<>();row.put("events",events);row.put("goals",unit.targetSelector.getAvailableGoals().stream().map(g->g.getGoal().getClass().getSimpleName()+":"+g.isRunning()+":"+g.getGoal().getFlags()).toList());
                for(Field f:unit.targetSelector.getClass().getDeclaredFields()){f.setAccessible(true);row.put("selector."+f.getName(),String.valueOf(f.get(unit.targetSelector)));}
                Goal goal=unit.targetSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(g->g.getClass().getSimpleName().equals("SupportMonsterGoal")).findFirst().orElseThrow();
                boolean ready=false;for(int i=0;i<512;i++)if(goal.canUse()){ready=true;break;}row.put("directReady",ready);
                for(Class<?> c=goal.getClass();c!=null;c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(LivingEntity.class.isAssignableFrom(f.getType())){f.setAccessible(true);row.put("goal."+f.getName(),String.valueOf(f.get(goal)));}
                if(ready)goal.start();row.put("directStartTarget",String.valueOf(unit.getTarget()));rows.add(row);break;
            }finally{listen.set(false);}
        }
        return Map.of("failures",rows);
    }
    public static Map<String,Object> pdaPrepare(ServerPlayer p,Path evidence)throws Exception{
        owned(p);clear(p);p.getInventory().clearContent();WorldState world=WorldState.get(p.serverLevel());world.enabled=true;
        world.account(p.getUUID()).balance=1000;
        world.campaign(p.getUUID()).merchantAccepted.add("review_preserved");
        p.getInventory().add(new ItemStack(Frontier.PDA.get()));p.getInventory().add(new ItemStack(Items.STICK,7));
        new Gameplay().login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
        if(TradeService.hasPda(p)||TradeService.count(p,"minecraft:stick")!=7||world.account(p.getUUID()).balance!=1000||!world.campaign(p.getUUID()).merchantAccepted.contains("review_preserved"))throw new AssertionError("Legacy terminal migration changed unrelated inventory or saved progress");
        p.getInventory().clearContent();world.campaign(p.getUUID()).merchantAccepted.remove("review_preserved");
        cmd(p,"gamerule doMobSpawning false","time set 6000","weather clear","gamemode creative @p");
        p.serverLevel().getChunk(-404>>4,188>>4);
        int y=p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,-404,188);
        if(y<=p.level().getMinBuildHeight())throw new AssertionError("PDA review surface chunk not ready: "+y);
        p.teleportTo(p.serverLevel(),-404.5,y+2,188.5,90,10);p.getInventory().add(new ItemStack(Frontier.ROUGH_PICKAXE.get()));
        p.getInventory().add(new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation("create:wrench")))));
        ((net.minecraft.world.level.saveddata.SavedData)world).setDirty();FrontierNetwork.forget(p.getUUID());
        return Map.of("legacyPdaRemoved",true,"otherInventoryPreserved",true,"balancePreserved",true,"questProgressPreserved",true,"pdaItemPresent",false,"initialBalance",1000,"location",p.blockPosition().toShortString());
    }
    public static Map<String,Object> artPrepare(ServerPlayer p,Path evidence)throws Exception{
        owned(p);pdaPrepare(p,evidence);p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);
        List<String> names=List.of("metal_scrap","rough_iron_ingot","aluminum_scrap","scrap_bundle","unlit_torch","rough_iron_pickaxe","cpu_i3_6100","cpu_pentium_g4560","cpu_i5_2500k","gpu_8800_gtx","gpu_gtx_480","waste_battery","plastic_fragment","compost","mining_rig");
        for(String name:names)p.getInventory().add(new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation("gnfrontier",name)))));
        p.inventoryMenu.broadcastChanges();return Map.of("items",names,"handAuthoredRedraws",true,"ownedWorldOnly",true);
    }
    public static Map<String,Object> pdaTransaction(ServerPlayer p,Path evidence)throws Exception{
        owned(p);WorldState w=WorldState.get(p.serverLevel());long balance=w.account(p.getUUID()).balance;int count=TradeService.count(p,"survival_instinct:mre");
        if(TradeService.hasPda(p)||balance!=800||count!=2)throw new AssertionError("Hotkey-only purchase failed: balance="+balance+", rations="+count);
        if(w.campaign(p.getUUID()).accepted==0)throw new AssertionError("Daily accept button did not reach the server");
        boolean denied=false;p.setGameMode(GameType.SPECTATOR);
        try{TradeService.trade(p,"ration",local.gnfrontier.core.Economy.Direction.BUY,1);}catch(IllegalArgumentException expected){denied=true;}finally{p.setGameMode(GameType.CREATIVE);}
        if(!denied||w.account(p.getUUID()).balance!=800)throw new AssertionError("Spectator transaction guard lost");
        return Map.of("checks",5,"noPdaRequired",true,"balance",balance,"rations",count,"dailyAccepted",true,"spectatorDenied",true);
    }
    private static final List<String> passed=new ArrayList<>();
    private static final List<String> failed=new ArrayList<>();
    private static boolean probe;
    private static void check(boolean ok,String why){if(!ok){failed.add(why);if(!probe)throw new AssertionError(why+"; previous="+passed.subList(Math.max(0,passed.size()-4),passed.size()));}else passed.add(why);}
    public static Map<String,Object> probe(ServerPlayer p,Path evidence)throws Exception{probe=true;try{return verify(p,evidence);}finally{probe=false;}}
    private static void owned(ServerPlayer p){if(!p.getServer().getWorldData().getLevelName().matches("GN_Review_r(23_factions|24_expert|28_beds)"))throw new IllegalStateException("Owned r23 world required");}
    private static void cmd(ServerPlayer p,String... cs){for(String c:cs)p.getServer().getCommands().performPrefixedCommand(p.getServer().createCommandSourceStack(),c);}
    private static Mob spawn(ServerPlayer p,EntityType<?> type,double x,double y,double z,boolean finalize){
        Entity entity=Objects.requireNonNull(type.create(p.serverLevel()));if(!(entity instanceof Mob mob))throw new AssertionError(type);
        mob.moveTo(x,y,z,0,0);mob.getPersistentData().putBoolean("sewv:companion_rolled",true);if(finalize)mob.finalizeSpawn(p.serverLevel(),p.serverLevel().getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.COMMAND,null,null);
        mob.setPersistenceRequired();mob.getPersistentData().putBoolean("gn_r23_fixture",true);if(!p.serverLevel().addFreshEntity(mob))throw new AssertionError("Fixture join rejected: "+type);return mob;
    }
    private static Mob spawn(ServerPlayer p,String id,double x,double z,boolean finalize){return spawn(p,Objects.requireNonNull(ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id))),x,240,z,finalize);}
    private static void clear(ServerPlayer p){List<Entity> remove=new ArrayList<>();for(Entity e:p.serverLevel().getAllEntities())if(e!=null&&!(e instanceof ServerPlayer)&&e.blockPosition().getY()>235&&e.distanceToSqr(p)<150*150)remove.add(e);remove.forEach(Entity::discard);}
    private static void isolate(ServerPlayer p,Mob a,Mob b){List<Entity> remove=new ArrayList<>();for(Entity e:p.serverLevel().getAllEntities())if(e instanceof Mob&&e!=a&&e!=b&&e.blockPosition().getY()>235&&e.distanceToSqr(p)<150*150)remove.add(e);remove.forEach(Entity::discard);}
    private static Goal mining(Mob m){return m.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(g->g.getClass().getSimpleName().equals("MineTowardsTargetGoal")).findFirst().orElse(null);}
    private static boolean nativeValid(Mob unit,Mob target)throws Exception{
        Class<?> c=Class.forName("com.neoalive.tacz_sewv.entity.ai.core.VehicleTargeting");
        return (Boolean)c.getMethod("isValidHostileTarget",Class.forName("net.nekoyuni.SimpleEnemyMod.entity.unit.AbstractUnit"),LivingEntity.class).invoke(null,unit,target);
    }
    private static void setup(ServerPlayer p){
        cmd(p,"gamemode creative @p","gamerule doMobSpawning false","gamerule doDaylightCycle false","gamerule doWeatherCycle false","gamerule mobGriefing true","difficulty normal","time set 18000","weather clear","tp @p 8.5 240 8.5","fill -6 239 -6 25 239 25 minecraft:smooth_stone","fill -6 240 -6 25 252 25 minecraft:air");
        p.setGameMode(GameType.CREATIVE);p.setHealth(p.getMaxHealth());WorldState w=WorldState.get(p.serverLevel());w.enabled=true;w.players.clear();w.respawns.clear();((net.minecraft.world.level.saveddata.SavedData)w).setDirty();clear(p);
    }
    public static Map<String,Object> verify(ServerPlayer p,Path evidence)throws Exception {
        owned(p);passed.clear();failed.clear();setup(p);
        verifySidearms();
        List<EntityType<?>> types=ForgeRegistries.ENTITY_TYPES.getValues().stream().filter(t->{var id=ForgeRegistries.ENTITY_TYPES.getKey(t);return id!=null&&id.getNamespace().equals("minecraft")&&t.getCategory()==MobCategory.MONSTER;}).sorted(Comparator.comparing(t->ForgeRegistries.ENTITY_TYPES.getKey(t).toString())).toList();
        List<String> matrix=new ArrayList<>();
        for(String side:List.of("simpleenemymod:ruunit","simpleenemymod:usunit","simpleenemymod:pmcunit","tacz_sewv:ru_engineer","tacz_sewv:us_engineer","tacz_sewv:ru_combat_engineer","tacz_sewv:us_combat_engineer","tacz_sewv:pmc_commander"))for(EntityType<?> type:types){
            clear(p);String id=ForgeRegistries.ENTITY_TYPES.getKey(type).toString();Mob unit=spawn(p,side,.5,6.5,true);unit.setNoAi(true);
            Mob monster=spawn(p,type,.5,240,.5,false);
            isolate(p,unit,monster);
            if(monster instanceof Slime slime)slime.setSize(2,true);
            check(FactionHostility.enemies(unit,monster)&&FactionHostility.enemies(monster,unit),side+" <-> "+id+": reciprocal hostile policy");
            check(nativeValid(unit,monster),side+" -> "+id+": native Combined Arms targeting accepts the enemy");
            for(int i=0;i<512&&unit.getTarget()!=monster;i++){unit.tickCount++;unit.getSensing().tick();unit.targetSelector.tick();}
            if(unit.getTarget()!=monster)System.out.println("GN_R23_TARGET_DEBUG "+side+" / "+id+" range="+unit.getAttributeValue(Attributes.FOLLOW_RANGE)+" invisible="+monster.isInvisible()+" removed="+monster.isRemoved()+" goals="+unit.targetSelector.getAvailableGoals().stream().map(g->g.getGoal().getClass().getSimpleName()+":"+g.isRunning()+":"+g.getGoal().canUse()).toList());
            check(unit.getTarget()==monster,side+" -> "+id+": native soldier target selector; actual="+(unit.getTarget()==null?"null":ForgeRegistries.ENTITY_TYPES.getKey(unit.getTarget().getType()))+", visible="+unit.getSensing().hasLineOfSight(monster)+", canAttack="+unit.canAttack(monster));
            if(monster instanceof EnderDragon dragon){
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);dragon.tickCount=20;
                new FactionHostility().dragon(new LivingEvent.LivingTickEvent(dragon));
                check(dragon.getTarget()==unit&&dragon.getPhaseManager().getCurrentPhase().getPhase()==EnderDragonPhase.STRAFE_PLAYER,id+" -> "+side+": native dragon strafe phase targets the soldier");
            }else{
                List<Goal> gs=monster.targetSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(g->g instanceof FactionHostility.FactionTargetGoal).toList();
                check(gs.size()==1,id+": exactly one faction target goal");Goal g=gs.get(0);boolean ready=false;
                for(int i=0;i<512;i++)if(g.canUse()){ready=true;break;}
                check(ready,id+" -> "+side+": faction goal discovers soldier without assigning a target in the fixture");g.start();
                check(monster.getTarget()==unit,id+" -> "+side+": active native target is the soldier");
                var brain=monster.getBrain();
                if(brain.checkMemory(MemoryModuleType.ATTACK_TARGET,MemoryStatus.REGISTERED))check(brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null)==unit,id+": native Brain attack memory receives the soldier");
                if(monster instanceof net.minecraft.world.entity.monster.warden.Warden w)check(w.getAngerLevel().isAngry(),"Warden anger enables native combat");
                g.stop();
            }
            matrix.add(side+":"+id);monster.discard();unit.discard();
        }
        Mob unit=spawn(p,"simpleenemymod:ruunit",.5,6.5,true);unit.setNoAi(true);
        for(String id:List.of("minecraft:cow","minecraft:villager","minecraft:iron_golem","minecraft:wolf","minecraft:bee")){
            Mob other=spawn(p,id,4.5,4.5,false);check(!FactionHostility.enemies(other,unit)&&!FactionHostility.enemies(unit,other),"No added faction hostility for "+id);other.discard();
        }
        check(!FactionHostility.enemies(p,unit),"Player diplomacy is not changed by the mob hostility rule");
        unit.discard();
        List<String> humanIds=List.of("simpleenemymod:ruunit","simpleenemymod:usunit","simpleenemymod:pmcunit","tacz_sewv:ru_medic","tacz_sewv:us_medic","tacz_sewv:ru_engineer","tacz_sewv:us_engineer","tacz_sewv:ru_combat_engineer","tacz_sewv:us_combat_engineer","tacz_sewv:pmc_commander");
        for(String left:humanIds)for(String right:List.of("simpleenemymod:ruunit","simpleenemymod:usunit","simpleenemymod:pmcunit")){
            clear(p);Mob one=spawn(p,left,.5,.5,true),two=spawn(p,right,.5,6.5,true);one.setNoAi(true);two.setNoAi(true);
            check(FactionHostility.force(one)&&FactionHostility.faction(one)>=0,"Registered armed faction includes "+left);
            boolean enemy=FactionHostility.faction(one)!=FactionHostility.faction(two);
            check(FactionHostility.enemies(one,two)==enemy&&FactionHostility.enemies(two,one)==enemy,left+" / "+right+": same faction friendly, different factions hostile");
            check(nativeValid(one,two)==enemy&&nativeValid(two,one)==enemy,left+" / "+right+": native target validation agrees both ways");
            if(enemy&&!left.endsWith("_medic")){
                for(int i=0;i<512&&(one.getTarget()!=two||two.getTarget()!=one);i++){one.targetSelector.tick();two.targetSelector.tick();}
                check(one.getTarget()==two&&two.getTarget()==one,left+" <-> "+right+": native human faction AI acquires mutual targets");
            }else if(left.endsWith("_medic")){
                check(one.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal().getClass().getSimpleName().equals("MedicFleeGoal")),left+": native medical/retreat role retained");
            }
            one.discard();two.discard();
        }
        unit=spawn(p,"simpleenemymod:ruunit",.5,6.5,true);unit.setNoAi(true);
        for(String id:List.of("minecraft:slime","minecraft:magma_cube")){
            Slime slime=(Slime)spawn(p,id,.5,6.5,false);slime.setSize(2,true);slime.moveTo(.5,240,6.5,0,0);unit.invulnerableTime=0;unit.setHealth(unit.getMaxHealth());float health=unit.getHealth();
            slime.push(unit);check(unit.getHealth()<health,id+": native contact damage hurts an enemy soldier");slime.discard();
        }
        Zombie miner=null;for(int i=0;i<256;i++){Zombie z=(Zombie)spawn(p,"minecraft:zombie",.5,-2.5,false);if(mining(z)!=null){miner=z;break;}z.discard();}
        check(miner!=null,"Native miner spawned for tool checks");Goal g=mining(miner);miner.setTarget(unit);miner.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);miner.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.STONE_PICKAXE));
        cmd(p,"fill -2 240 0 2 243 0 minecraft:oak_planks");check(g.canUse(),"Held off-hand pickaxe permits native mining toward a soldier");g.start();for(int i=0;i<5;i++)g.tick();
        miner.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);check(!g.canUse()&&!g.canContinueToUse(),"Losing the pickaxe disables start and continuation");for(int i=0;i<180;i++)g.tick();
        check(p.level().getBlockState(new BlockPos(0,241,0)).is(Blocks.OAK_PLANKS),"A tool-less miner cannot finish breaking a soft wall");g.stop();
        miner.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.IRON_AXE));check(!g.canUse(),"An axe is not accepted as a pickaxe");miner.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        miner.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));cmd(p,"fill -2 240 0 2 243 0 minecraft:stone");check(g.canUse(),"Main-hand pickaxe also permits mining");g.start();
        BlockPos wall=new BlockPos(0,241,0);for(int i=0;i<400&&!p.level().getBlockState(wall).isAir();i++)g.tick();check(p.level().getBlockState(wall).isAir(),"Native mining uses the main-hand pickaxe to break stone");g.stop();
        miner.discard();unit.discard();p.getServer().saveEverything(false,true,true);
        return Map.of("checks",passed.size(),"passed",List.copyOf(passed),"failed",List.copyOf(failed),"vanillaHostileTypes",types.size(),"matrix",matrix,"scope","Controlled native target-goal/Brain/dragon phase and mining integration checks; autonomous combat is recorded separately.");
    }
    private static void verifySidearms()throws Exception{
        String[][] sources={{"com.neoalive.tacz_sewv.entity.ai.support.UnitHolster","ENGINEER_SIDEARM_POOL"},{"com.neoalive.tacz_sewv.entity.unit.PmcCommanderEntity","COMMANDER_SIDEARM_POOL"}};
        for(String[] source:sources){
            var config=(net.minecraftforge.common.ForgeConfigSpec.ConfigValue<List<? extends String>>)Class.forName("com.neoalive.tacz_sewv.config.SewvConfig").getField(source[1]).get(null);
            List<? extends String> original=config.get();
            Method builder=Class.forName(source[0]).getDeclaredMethod("buildSidearm",net.minecraft.util.RandomSource.class);builder.setAccessible(true);
            try{
                for(String requested:List.of("tacz:m9a1","tacz:m1911","tacz:glock_17")){
                    config.set(List.of(requested));ItemStack stack=(ItemStack)builder.invoke(null,net.minecraft.util.RandomSource.create(123));
                    var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
                    check(gun!=null,source[1]+": native builder equips "+requested);
                    if(gun!=null)check(gun.getGunId(stack).toString().equals(requested.equals("tacz:m9a1")?"tacz:m9a4":requested),source[1]+": only retired M9A1 maps to available M9A4");
                }
                config.set(List.of("gnreview:missing"));
                check(((ItemStack)builder.invoke(null,net.minecraft.util.RandomSource.create(123))).isEmpty(),source[1]+": unrelated missing custom guns are not replaced");
            }finally{config.set(original);}
        }
    }
    private static void combatEvent(LivingEntity victim,Entity attacker,String key){
        if(attacker instanceof Mob a&&victim instanceof Mob b&&FactionHostility.enemies(a,b)&&a.getPersistentData().contains("gn_r23_case")&&a.getPersistentData().getString("gn_r23_case").equals(b.getPersistentData().getString("gn_r23_case"))){
            a.getPersistentData().putInt(key,a.getPersistentData().getInt(key)+1);
        }
    }
    private static void record(ServerPlayer p,LivingEntity entity){
        if(entity.getPersistentData().contains("gn_r23_case")){
            CompoundTag copy=entity.getPersistentData().copy();copy.putFloat("health",entity.getHealth());copy.putString("type",ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString());
            p.getPersistentData().getCompound("gn_r23_records").put(entity.getUUID().toString(),copy);
        }
    }
    public static Map<String,Object> combatStart(ServerPlayer p,Path evidence)throws Exception{
        owned(p);setup(p);cmd(p,"gamerule mobGriefing false","fill -80 239 -80 80 239 80 minecraft:smooth_stone","tp @p 0.5 244 24.5 180 15");
        // Only the simultaneous fixture population is lifted; the shipped patrol config is untouched.
        Field cap=Class.forName("com.semcontrol.PatrolConfig").getDeclaredField("maxActiveSoldiers");cap.setAccessible(true);
        if(!p.getPersistentData().contains("gn_r23_original_cap"))p.getPersistentData().putInt("gn_r23_original_cap",cap.getInt(null));cap.setInt(null,64);
        p.getPersistentData().put("gn_r23_records",new CompoundTag());
        if(!p.getPersistentData().getBoolean("gn_r23_observer_v2")){
        p.getPersistentData().putBoolean("gn_r23_observer_v2",true);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,LivingChangeTargetEvent.class,e->{if(e.getNewTarget()!=null)combatEvent(e.getNewTarget(),e.getEntity(),"gn_r23_acquired");record(p,e.getEntity());});
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,LivingDamageEvent.class,e->{if(e.getAmount()>0)combatEvent(e.getEntity(),e.getSource().getEntity(),"gn_r23_damage");if(e.getSource().getEntity() instanceof LivingEntity a)record(p,a);record(p,e.getEntity());});
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,LivingDeathEvent.class,e->record(p,e.getEntity()));
        }
        ListTag cases=new ListTag();String[][] ids={{"minecraft:zombie","simpleenemymod:ruunit"},{"minecraft:skeleton","simpleenemymod:usunit"},{"minecraft:spider","simpleenemymod:pmcunit"},{"minecraft:slime","simpleenemymod:ruunit"},{"minecraft:piglin","simpleenemymod:usunit"},{"minecraft:hoglin","simpleenemymod:pmcunit"},
            {"simpleenemymod:ruunit","simpleenemymod:usunit"},{"simpleenemymod:ruunit","simpleenemymod:pmcunit"},{"simpleenemymod:usunit","simpleenemymod:pmcunit"},{"minecraft:pillager","simpleenemymod:ruunit"},{"minecraft:pillager","simpleenemymod:usunit"},{"minecraft:pillager","simpleenemymod:pmcunit"},
            {"minecraft:zombie","tacz_sewv:ru_engineer"},{"minecraft:skeleton","tacz_sewv:us_engineer"},{"minecraft:zombie","tacz_sewv:ru_combat_engineer"},{"minecraft:skeleton","tacz_sewv:us_combat_engineer"}};
        for(int i=0;i<ids.length;i++){
            String label=ids[i][0]+" / "+ids[i][1];int x=(i%4)*32-48,z=(i/4)*32-48;
            cmd(p,"fill "+(x-13)+" 239 "+(z-13)+" "+(x+13)+" 250 "+(z+13)+" minecraft:barrier hollow");
            Mob unit=spawn(p,ids[i][1],x+.5,z+3.5,true);
            Mob monster=spawn(p,ids[i][0],x+.5,z+.5,true);if(monster instanceof Slime s)s.setSize(2,true);
            if(monster instanceof Pillager bandit){
                Class<?> manager=Class.forName("net.chaoscraft.chaoszprojectbandits.PillagerRaidManager");Field following=manager.getDeclaredField("followingPillagers"),last=manager.getDeclaredField("lastPlayerPositions");following.setAccessible(true);last.setAccessible(true);
                ((List<Pillager>)following.get(null)).add(bandit);((Map<Pillager,net.minecraft.world.phys.Vec3>)last.get(null)).put(bandit,p.position());
            }
            if(monster instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin)piglin.setImmuneToZombification(true);
            if(monster instanceof net.minecraft.world.entity.monster.hoglin.Hoglin hoglin)hoglin.setImmuneToZombification(true);
            for(Mob m:List.of(unit,monster)){
                m.getPersistentData().putString("gn_r23_case",label);m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);m.setHealth(1000);m.setPersistenceRequired();
            }
            CompoundTag row=new CompoundTag();row.putString("mob",label);row.putUUID("unit",unit.getUUID());row.putUUID("monster",monster.getUUID());row.putString("monsterWeapon",ForgeRegistries.ITEMS.getKey(monster.getMainHandItem().getItem()).toString());row.putString("unitWeapon",ForgeRegistries.ITEMS.getKey(unit.getMainHandItem().getItem()).toString());cases.add(row);
        }
        p.getPersistentData().put("gn_r23_cases",cases);p.getPersistentData().putLong("gn_r23_start",p.level().getGameTime());
        return Map.of("cases",List.of(ids),"assignedTargets",false,"naturalServerTicks",true,"healthRaisedOnlyInFixture",1000,"temporaryFixturePopulationCap",64,"originalPopulationCap",p.getPersistentData().getInt("gn_r23_original_cap"));
    }
    public static Map<String,Object> combatFinish(ServerPlayer p,Path evidence){
        owned(p);passed.clear();long ticks=p.level().getGameTime()-p.getPersistentData().getLong("gn_r23_start");check(ticks>=160,"Autonomous combat ran for at least eight seconds of server ticks");
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Tag tag:p.getPersistentData().getList("gn_r23_cases",10)){
            CompoundTag row=(CompoundTag)tag;Mob a=(Mob)p.serverLevel().getEntity(row.getUUID("monster")),b=(Mob)p.serverLevel().getEntity(row.getUUID("unit"));
            if(a!=null)record(p,a);if(b!=null)record(p,b);CompoundTag records=p.getPersistentData().getCompound("gn_r23_records");var an=records.getCompound(row.getUUID("monster").toString());var bn=records.getCompound(row.getUUID("unit").toString());
            rows.add(Map.of("mob",row.getString("mob"),"monsterTargets",an.getInt("gn_r23_acquired"),"soldierTargets",bn.getInt("gn_r23_acquired"),"monsterHits",an.getInt("gn_r23_damage"),"soldierHits",bn.getInt("gn_r23_damage"),"monsterHealth",an.getFloat("health"),"soldierHealth",bn.getFloat("health"),"monsterWeapon",row.getString("monsterWeapon"),"unitWeapon",row.getString("unitWeapon")));
            check(an.getInt("gn_r23_acquired")>0&&bn.getInt("gn_r23_acquired")>0,"Both sides autonomously acquired enemies: "+row.getString("mob"));
            check(an.getInt("gn_r23_damage")>0&&bn.getInt("gn_r23_damage")>0,"Both sides caused real combat damage: "+row.getString("mob"));
        }
        return Map.of("checks",passed.size(),"passed",List.copyOf(passed),"failed",List.copyOf(failed),"elapsedServerTicks",ticks,"pairs",rows,"scope","Sixteen unforced encounter fixtures in separate pens, including four engineer roles and three actual Bandits manager-tracked pillagers. Health raised to keep both actors observable; target and attack AI run through normal server ticks. Hit counters accept only the matching opponent.");
    }
    public static Map<String,Object> combatProbe(ServerPlayer p,Path evidence){probe=true;try{failed.clear();return combatFinish(p,evidence);}finally{probe=false;}}
}

