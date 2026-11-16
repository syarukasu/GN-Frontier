package local.gnnight;

import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import local.gnfrontier.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/** Native player damage and entity locomotion, using only the registered isolated world. */
public final class NightZombieChecks {
    private final List<String> checks=new ArrayList<>();
    private final List<Entity> fixtures=new ArrayList<>();
    private final Map<String,Object> result=new LinkedHashMap<>();
    private void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);checks.add(message);}
    private static boolean near(double a,double b){return Math.abs(a-b)<.0001;}
    private static double value(Zombie z,net.minecraft.world.entity.ai.attributes.Attribute a){return z.getAttributeValue(a);}
    private static final class ProbePlayer extends Player {
        ProbePlayer(ServerLevel level,BlockPos pos){super(level,pos,0,new GameProfile(UUID.randomUUID(),"GN_NightDamage"));setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);}
        @Override public boolean isSpectator(){return false;}
        @Override public boolean isCreative(){return false;}
    }
    private Zombie spawn(ServerLevel level,long time,boolean baby,BlockPos pos,Consumer<Zombie> prepare){
        level.setDayTime(time);Zombie z=EntityType.ZOMBIE.create(level);Objects.requireNonNull(z);
        z.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,-90,0);
        z.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),MobSpawnType.COMMAND,new Zombie.ZombieGroupData(baby,false),null);
        z.setBaby(baby);z.setNoAi(true);z.setNoGravity(true);z.setCanPickUpLoot(false);
        for(var slot:EquipmentSlot.values())z.setItemSlot(slot,ItemStack.EMPTY);
        prepare.accept(z);fixtures.add(z);
        if(!level.addFreshEntity(z))throw new IllegalStateException("Zombie fixture rejected");
        return z;
    }
    private double attack(ServerLevel level,Zombie zombie){
        ProbePlayer target=new ProbePlayer(level,zombie.blockPosition().offset(0,0,2));fixtures.add(target);
        target.setNoGravity(true);
        target.getAbilities().invulnerable=false;target.setInvulnerable(false);target.setHealth(20);target.setAbsorptionAmount(0);target.invulnerableTime=0;
        zombie.clearFire();check(zombie.doHurtTarget(target),"native zombie melee reaches the unarmored player damage pipeline");
        return 20-target.getHealth();
    }
    public static Map<String,Object> run(ServerLevel level,Mob humanTarget){
        var test=new NightZombieChecks();
        try{return test.verify(level,humanTarget);}catch(RuntimeException error){System.out.println("GN_NIGHT_PARTIAL "+new com.google.gson.Gson().toJson(test.result));throw error;}
    }
    private double creatureAttack(Zombie attacker,LivingEntity target){
        target.setInvulnerable(false);target.setAbsorptionAmount(0);target.setHealth(target.getMaxHealth());
        target.invulnerableTime=0;target.hurtTime=0;attacker.clearFire();float before=target.getHealth();
        check(attacker.doHurtTarget(target),"native zombie melee can damage non-player "+target.getType());
        return before-target.getHealth();
    }
    private Map<String,Object> verify(ServerLevel level,Mob humanTarget){
        long oldTime=level.getDayTime();Map<BlockPos,BlockState> floor=new LinkedHashMap<>();
        boolean oldEnabled=PrototypeConfig.NIGHT_ZOMBIES.get();
        BlockPos area=new BlockPos(level.getSharedSpawnPos().getX()+32,level.getMaxBuildHeight()-32,level.getSharedSpawnPos().getZ()+32);
        try{
            check(level.getDifficulty()==Difficulty.HARD,"night damage is checked under the pack's actual Hard difficulty");
            check(oldEnabled&&near(PrototypeConfig.NIGHT_ZOMBIE_DAMAGE.get(),8)&&near(PrototypeConfig.NIGHT_ZOMBIE_SPEED.get(),1.35),"server loads night attack 8 and speed multiplier 1.35 defaults");
            for(long time:new long[]{0,6000,12999,13000,18000,22999,23000,42000,-6000}){
                long t=Math.floorMod(time,24000L);check(NightZombies.nightTime(time)==(t>=13000&&t<23000),"clock-only night window at "+time);
            }
            Zombie day=spawn(level,6000,false,area,z->{});
            check(!day.getPersistentData().getBoolean(NightZombies.MARKER),"day-born zombie has no night profile");
            check(near(value(day,Attributes.ATTACK_DAMAGE),3)&&near(day.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),20),"day-born zombie retains native base attack and health");
            Zombie night=spawn(level,18000,false,area.offset(0,0,3),z->{});
            check(night.getPersistentData().getBoolean(NightZombies.MARKER),"fresh night zombie receives its persistent birth marker");
            check(near(night.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),30)&&near(value(night,Attributes.ATTACK_DAMAGE),8),"night base health floor 30 and attack floor 8 are applied");
            check(near(value(night,Attributes.MOVEMENT_SPEED),value(day,Attributes.MOVEMENT_SPEED)*1.35),"adult night speed is exactly 1.35 times its native value");
            double dayDamage=attack(level,day),nightDamage=attack(level,night);
            result.put("hardMeleeDamage",Map.of("dayUnarmored",dayDamage,"nightUnarmored",nightDamage));
            check(near(dayDamage,4.5),"native day zombie deals 4.5 damage on Hard");
            check(near(nightDamage,12),"night zombie deals 12 damage or six hearts on Hard against an unarmored player");
            var creature=EntityType.COW.create(level);fixtures.add(creature);creature.setNoAi(true);creature.setNoGravity(true);
            creature.moveTo(area.getX()+3.5,area.getY(),area.getZ()+7.5,0,0);
            double creatureDay=creatureAttack(day,creature),creatureNight=creatureAttack(night,creature);
            check(near(creatureDay,3)&&near(creatureNight,8),"zombie attack strength also rises from 3 to 8 against an unarmored non-player");
            creature.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.NETHERITE_HELMET));creature.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.NETHERITE_CHESTPLATE));creature.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.NETHERITE_LEGGINGS));creature.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.NETHERITE_BOOTS));
            creature.tick();check(creature.getArmorValue()>0,"native equipment tick activates armor on a non-player target");
            double creatureArmored=creatureAttack(night,creature);
            check(creatureArmored>0&&creatureArmored<creatureNight,"ordinary armor still reduces the strengthened zombie melee damage");
            fixtures.add(humanTarget);humanTarget.moveTo(area.getX()+4.5,area.getY(),area.getZ()+7.5,0,0);
            humanTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);humanTarget.setHealth(humanTarget.getMaxHealth());humanTarget.tick();
            double humanDay=creatureAttack(day,humanTarget),humanNight=creatureAttack(night,humanTarget);
            check(humanNight>humanDay&&humanDay>0,"equipped regular infantry also takes a stronger hit from a night-born zombie");
            result.put("nonPlayerMeleeDamage",Map.of("unarmoredCreatureDay",creatureDay,"unarmoredCreatureNight",creatureNight,
                "armoredCreatureNight",creatureArmored,"equippedInfantryDay",humanDay,"equippedInfantryNight",humanNight,"targetType",net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(humanTarget.getType()).toString()));
            double speed=value(night,Attributes.MOVEMENT_SPEED);
            check(!NightZombies.initializeFresh(night,level)&&near(speed,value(night,Attributes.MOVEMENT_SPEED)),"repeat initialization does not stack night speed");
            level.setDayTime(6000);night.tick();
            check(near(value(night,Attributes.ATTACK_DAMAGE),8)&&near(value(night,Attributes.MOVEMENT_SPEED),speed),"night-born profile is retained through dawn");
            Zombie loaded=EntityType.ZOMBIE.create(level);fixtures.add(loaded);loaded.load(night.saveWithoutId(new CompoundTag()));
            new Gameplay().join(new EntityJoinLevelEvent(loaded,level,true));
            check(loaded.getPersistentData().getBoolean(NightZombies.MARKER)&&near(value(loaded,Attributes.MOVEMENT_SPEED),speed)&&near(value(loaded,Attributes.ATTACK_DAMAGE),8),"save and reload retain night attack and the single permanent speed modifier");
            Zombie legacy=EntityType.ZOMBIE.create(level);fixtures.add(legacy);legacy.getPersistentData().putBoolean("gnfrontier_spawned",true);level.setDayTime(18000);
            new Gameplay().join(new EntityJoinLevelEvent(legacy,level,true));
            check(!legacy.getPersistentData().getBoolean(NightZombies.MARKER)&&near(value(legacy,Attributes.ATTACK_DAMAGE),3),"unknown legacy birth time is not guessed from the current night");
            Zombie babyDay=spawn(level,6000,true,area.offset(4,0,0),z->{}),babyNight=spawn(level,18000,true,area.offset(4,0,3),z->{});
            check(near(value(babyNight,Attributes.MOVEMENT_SPEED),value(babyDay,Attributes.MOVEMENT_SPEED)*1.35),"native baby speed multiplier is retained alongside night speed");
            Zombie strong=spawn(level,18000,false,area.offset(6,0,0),z->{z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(16);z.setHealth(100);});
            check(near(strong.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),100)&&near(value(strong,Attributes.ATTACK_DAMAGE),16),"stronger pre-existing mob base attributes are never lowered to the night floors");
            Zombie hurt=spawn(level,18000,false,area.offset(6,0,3),z->{z.setHealth(5);z.getPersistentData().putDouble("GNReviewWoundedFraction",z.getHealth()/z.getMaxHealth());});
            check(near(hurt.getHealth()/hurt.getMaxHealth(),hurt.getPersistentData().getDouble("GNReviewWoundedFraction")),"night health adjustment retains the native wounded fraction");
            PrototypeConfig.NIGHT_ZOMBIES.set(false);
            Zombie disabled=spawn(level,18000,false,area.offset(8,0,0),z->{});
            check(!disabled.getPersistentData().getBoolean(NightZombies.MARKER)&&near(value(disabled,Attributes.ATTACK_DAMAGE),3),"night profile can be disabled with config");
            PrototypeConfig.NIGHT_ZOMBIES.set(oldEnabled);
            ServerLevel nether=level.getServer().getLevel(Level.NETHER);Zombie other=EntityType.ZOMBIE.create(nether);fixtures.add(other);
            check(!NightZombies.initializeFresh(other,nether),"night profile leaves other dimensions' native mobs unchanged");
            // Compare native movement on identical level ground with navigation inputs kept equal.
            for(int x=0;x<28;x++)for(int z=0;z<8;z++)for(int dy=-1;dy<=2;dy++){
                BlockPos pos=area.offset(x,dy,z);floor.put(pos,level.getBlockState(pos));level.setBlock(pos,(dy==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
            }
            for(Entity fixture:fixtures)if(fixture!=day&&fixture!=night)fixture.discard();
            day.moveTo(area.getX()+1.5,area.getY(),area.getZ()+2.5,-90,0);night.moveTo(area.getX()+1.5,area.getY(),area.getZ()+5.5,-90,0);
            for(Zombie mob:List.of(day,night)){
                mob.goalSelector.removeAllGoals(goal->true);mob.targetSelector.removeAllGoals(goal->true);mob.setTarget(null);
                mob.setNoAi(false);mob.setNoGravity(false);mob.setOnGround(true);mob.setDeltaMovement(0,0,0);
            }
            double start=day.getX();
            for(int tick=0;tick<50;tick++){
                day.getMoveControl().setWantedPosition(area.getX()+26.5,area.getY(),area.getZ()+2.5,1);
                night.getMoveControl().setWantedPosition(area.getX()+26.5,area.getY(),area.getZ()+5.5,1);
                day.tick();night.tick();
            }
            double walkedDay=day.getX()-start,walkedNight=night.getX()-start;
            check(!day.getPersistentData().getBoolean(NightZombies.MARKER)&&near(value(day,Attributes.ATTACK_DAMAGE),3),"day-born zombie stays at native strength when night arrives");
            result.put("nativeWalking",Map.of("ticks",50,"dayBlocks",walkedDay,"nightBlocks",walkedNight,"ratio",walkedDay>0?walkedNight/walkedDay:0));
            check(walkedDay>.1&&walkedNight>walkedDay*1.2,"night zombie actually walks farther with identical native movement input");
            result.put("checks",checks);result.put("speedMultiplier",1.35);result.put("birthTimeWindow",List.of(13000,23000));result.put("screenUsed",false);
            return result;
        }finally{
            PrototypeConfig.NIGHT_ZOMBIES.set(oldEnabled);level.setDayTime(oldTime);
            fixtures.forEach(Entity::discard);floor.forEach((pos,state)->level.setBlock(pos,state,18));
        }
    }
}
