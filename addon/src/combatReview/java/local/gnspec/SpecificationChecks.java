package local.gnspec;

import java.util.*;
import local.gnfrontier.*;
import local.gnfrontier.core.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.registries.ForgeRegistries;

/** Actual entity deaths, native sunlight ticks, Forge spawn vetoes and registered-bed ownership. */
public final class SpecificationChecks {
    private final List<String> checks=new ArrayList<>();
    private final List<Entity> fixtures=new ArrayList<>();
    private ServerLevel level;
    private BlockPos origin;
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);}
    private Mob mob(String id){
        Mob mob=(Mob)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id)).create(level);
        mob.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5,0,0);
        mob.getPersistentData().putBoolean("sewv:companion_rolled",true);mob.setNoAi(true);mob.setNoGravity(true);
        mob.finalizeSpawn(level,level.getCurrentDifficultyAt(origin),MobSpawnType.COMMAND,null,null);
        fixtures.add(mob);if(!level.addFreshEntity(mob))throw new IllegalStateException("Fixture spawn vetoed: "+id);return mob;
    }
    public static Map<String,Object> run(ServerLevel level,ServerPlayer one,ServerPlayer two)throws Exception{return new SpecificationChecks().verify(level,one,two);}
    @SuppressWarnings({"rawtypes","unchecked"})
    private Map<String,Object> verify(ServerLevel world,ServerPlayer one,ServerPlayer two)throws Exception{
        level=world;origin=level.getSharedSpawnPos().atY(level.getMaxBuildHeight()-20).offset(12,0,12);
        var state=WorldState.get(level);var beforeOne=state.players.containsKey(one.getUUID())?WorldState.encode(state.players.get(one.getUUID())):null;
        var beforeTwo=state.players.containsKey(two.getUUID())?WorldState.encode(state.players.get(two.getUUID())):null;
        var beforeCampaign=state.campaigns.containsKey(one.getUUID())?state.campaigns.get(one.getUUID()).save():null;
        var beforeCycle=state.dailyCycle.save();long beforeSun=level.getDayTime();var mode=one.gameMode.getGameModeForPlayer();
        Map<BlockPos,BlockState> blocks=new HashMap<>();
        Class<?> cacheOwner=Class.forName("mcjty.incontrol.InControl");Object setup=cacheOwner.getField("setup").get(null);
        var cacheField=setup.getClass().getField("cache");Object cache=cacheField.get(setup);boolean added=false;
        try{
            one.setGameMode(GameType.SURVIVAL);
            var account=state.account(one.getUUID());account.reputation[0]=-30;account.reputation[1]=-30;account.alert[0]=account.alert[1]=0;DiplomacyBridge.sync(one,account);
            var campaign=state.refreshCampaign(one.getUUID(),level);
            List<String> regular=List.of("simpleenemymod:ruunit","simpleenemymod:usunit","tacz_sewv:ru_medic","tacz_sewv:us_medic","tacz_sewv:ru_engineer","tacz_sewv:us_engineer","tacz_sewv:ru_combat_engineer","tacz_sewv:us_combat_engineer");
            var mission=new Campaign.Mission("spec_soldiers","敵兵の排除",Campaign.Kind.SOLDIER,"","",16,100,Economy.Merchant.BLACK,"",regular,"SOLDIER|spec",DailyMissions.hash("spec_soldiers"));
            campaign.dailyInitialized=true;campaign.dailyMissions=List.of(mission);campaign.accepted=1;campaign.claimed=0;Arrays.fill(campaign.progress,0);campaign.issuedMissions.add(mission.id());campaign.issuedAppearances.add(mission.appearance());campaign.issuedContents.add(mission.contentKey());
            Cow enemy=EntityType.COW.create(level);enemy.setPos(origin.getX()+4,origin.getY(),origin.getZ());fixtures.add(enemy);
            int expected=0;
            for(String id:regular){
                Mob soldier=mob(id);soldier.setTarget(enemy);int faction=FactionHostility.faction(soldier);
                check(faction==0||faction==1,"regular/support faction classification: "+id);
                int rep=account.reputation[faction],alert=account.alert[faction];
                check(soldier.hurt(level.damageSources().playerAttack(one),10000)&&!soldier.isAlive(),"actual player-attributed death: "+id);
                check(campaign.progress[0]==++expected,"bounty counts a hostile soldier fighting a non-player: "+id);
                check(account.reputation[faction]==Math.max(-100,rep-5)&&account.alert[faction]==Math.min(100,alert+10),"GN standing and alert reflect this regular/support death: "+id);
            }
            account.reputation[0]=40;account.alert[0]=0;DiplomacyBridge.sync(one,account);
            Mob ally=mob("simpleenemymod:ruunit");ally.setTarget(one);
            check(!FactionRelations.hostileTo(ally,one),"alliance overrides a stale player target");
            ally.hurt(level.damageSources().playerAttack(one),10000);check(campaign.progress[0]==expected,"allied kill is not a bounty before its penalty is applied");
            account.reputation[0]=0;account.alert[0]=0;DiplomacyBridge.sync(one,account);
            Mob neutral=mob("simpleenemymod:ruunit");neutral.setTarget(two);
            neutral.hurt(level.damageSources().playerAttack(one),10000);check(campaign.progress[0]==expected,"another player's neutral enemy is not automatically my bounty");
            BlockPos head=origin.offset(0,0,4),foot=head.north();
            for(BlockPos p:List.of(head,foot,head.below(),foot.below()))blocks.put(p,level.getBlockState(p));
            level.setBlock(head.below(),Blocks.STONE.defaultBlockState(),2);level.setBlock(foot.below(),Blocks.STONE.defaultBlockState(),2);
            level.setBlock(foot,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH).setValue(BedBlock.PART,BedPart.FOOT),2);
            level.setBlock(head,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH).setValue(BedBlock.PART,BedPart.HEAD),2);
            account.spawnDimension=level.dimension().location().toString();account.spawnPosition=head.asLong();account.reputation[0]=-30;account.alert[0]=0;
            Mob breacher=mob("simpleenemymod:ruunit");breacher.moveTo(head.getX()+.5,head.getY(),head.getZ()+1.8,0,0);
            check(RespawnRules.attackableBed(level,head,breacher),"hostile regular can target its enemy owner's bed");
            net.minecraft.world.entity.ai.goal.Goal goal=new RegisteredBedGoal(breacher);check(goal.canUse(),"native bed goal starts for a reachable hostile bed");
            account.reputation[0]=40;check(!goal.canContinueToUse(),"becoming allied stops an already selected bed attack");
            check(!RespawnRules.attackableBed(level,head,breacher),"allied owner's bed is excluded");
            account.reputation[0]=0;check(!RespawnRules.attackableBed(level,head,breacher),"neutral owner's bed is excluded without personal aggression");
            breacher.setTarget(one);check(RespawnRules.attackableBed(level,head,breacher),"neutral soldier personally attacking the owner may breach");
            var other=state.account(two.getUUID());other.spawnDimension=account.spawnDimension;other.spawnPosition=account.spawnPosition;other.reputation[0]=40;other.alert[0]=0;
            check(!RespawnRules.attackableBed(level,head,breacher),"a shared bed respects its allied co-owner");
            Mob companion=mob("simpleenemymod:pmcunit");companion.getClass().getMethod("setOwner",UUID.class).invoke(companion,one.getUUID());
            check(!FactionRelations.hostileTo(companion,one),"owned PMC companion is excluded from autonomous bed attacks and hostile bounties");
            Mob commander=mob("tacz_sewv:pmc_commander");NpcEquipment.prepare(commander);
            var weapon=commander.getMainHandItem();var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(weapon);
            check(gun!=null&&!gun.useDummyAmmo(weapon),"PMC commander gun has no virtual or infinite ammunition");
            int reserves=ammo(commander);check(reserves>0&&reserves<1000,"commander virtual ammunition becomes bounded physical reserves");
            var exact=NpcEquipment.contents(commander).stream().map(s->s.save(new CompoundTag()).toString()).toList();NpcEquipment.prepare(commander);
            check(exact.equals(NpcEquipment.contents(commander).stream().map(s->s.save(new CompoundTag()).toString()).toList()),"finite ammunition migration never reissues ammo or rerolls the gun");
            var api=new com.tacz.guns.item.ModernKineticGunScriptAPI();
            api.setShooter(commander);api.setItemStack(weapon);api.setDataHolder(com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(commander).getDataHolder());
            check(api.consumeAmmoFromPlayer(1)==1&&ammo(commander)==reserves-1,"native PMC commander reload consumes a physical round");
            var body=Frontier.FALLEN_SOLDIER.get().create(level);body.capture(commander,NpcEquipment.contents(commander));fixtures.add((Entity)body);((Entity)body).setNoGravity(true);
            var originalGun=body.inventory.getItem(0).getTag().copy();body.inventory.getItem(0).getOrCreateTag().putInt("DummyAmmo",9999);body.inventory.getItem(0).getOrCreateTag().putInt("MaxDummyAmmo",Integer.MAX_VALUE);
            ((Entity)body).tick();check(!gun.useDummyAmmo(body.inventory.getItem(0))&&originalGun.equals(body.inventory.getItem(0).getTag()),"legacy corpse migration clears virtual ammo but retains all real gun NBT");
            var corpseContents=java.util.stream.IntStream.range(0,54).mapToObj(i->body.inventory.getItem(i).save(new CompoundTag()).toString()).toList();((Entity)body).tick();
            check(corpseContents.equals(java.util.stream.IntStream.range(0,54).mapToObj(i->body.inventory.getItem(i).save(new CompoundTag()).toString()).toList()),"corpse migration is applied once without repeat ammo gains");
            level.setDayTime(6000);Zombie zombie=(Zombie)mob("minecraft:zombie");
            zombie.setSecondsOnFire(10);int flame=zombie.getRemainingFireTicks();zombie.tick();
            check(zombie.getRemainingFireTicks()>0&&zombie.getRemainingFireTicks()>=flame-1,"daylight immunity retains external fire through a real zombie tick");
            zombie.clearFire();for(int i=0;i<30;i++)zombie.tick();check(!zombie.isOnFire(),"daylight does not ignite the zombie");
            check(level.players().isEmpty(),"spawn-cap fixture has no connected players");added=level.players().add(one);
            var cacheType=cache.getClass();Object fresh=cacheType.getConstructor().newInstance();cacheField.set(setup,fresh);cacheType.getMethod("performCount",net.minecraft.world.level.LevelAccessor.class).invoke(fresh,level);
            check(Gameplay.daySpawnAllowed(zombie,level),"daytime addition reaches In Control and is allowed below cap");
            for(int i=0;i<64;i++)mob("minecraft:zombie");
            fresh=cacheType.getConstructor().newInstance();cacheField.set(setup,fresh);cacheType.getMethod("performCount",net.minecraft.world.level.LevelAccessor.class).invoke(fresh,level);
            check(!Gameplay.daySpawnAllowed(zombie,level),"native In Control denies the daytime addition at the shared population cap");
            return Map.of("checks",checks,"regularDeaths",regular.size(),"targetWasNonPlayer",true,"daySpawnVetoVerified",true,"finitePmcAmmoVerified",true,"screenUsed",false);
        }finally{
            if(added)level.players().remove(one);cacheField.set(setup,cache);level.setDayTime(beforeSun);state.dailyCycle=DailyCycle.load(beforeCycle);one.setGameMode(mode);
            if(beforeOne==null)state.players.remove(one.getUUID());else state.players.put(one.getUUID(),WorldState.decode(beforeOne));
            if(beforeTwo==null)state.players.remove(two.getUUID());else state.players.put(two.getUUID(),WorldState.decode(beforeTwo));
            if(beforeCampaign==null)state.campaigns.remove(one.getUUID());else state.campaigns.put(one.getUUID(),Campaign.State.load(beforeCampaign));
            blocks.forEach((p,s)->level.setBlock(p,s,2));fixtures.forEach(Entity::discard);
            for(var body:level.getEntitiesOfClass(FallenSoldier.class,new net.minecraft.world.phys.AABB(origin).inflate(3)))((Entity)body).discard();
            ((net.minecraft.world.level.saveddata.SavedData)state).setDirty();
        }
    }
    private static int ammo(Mob mob){
        var inv=NpcEquipment.inventory(mob);int count=0;for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i).getItem() instanceof com.tacz.guns.api.item.IAmmo)count+=inv.getStackInSlot(i).getCount();return count;
    }
}
