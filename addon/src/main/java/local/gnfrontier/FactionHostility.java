package local.gnfrontier;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Completes vanilla -> armed-faction hostility; SEM supplies soldiers' combat goals. */
public final class FactionHostility {
    public static final TagKey<EntityType<?>> FORCES=TagKey.create(Registries.ENTITY_TYPE,Frontier.id("armed_factions"));
    private static final TagKey<EntityType<?>> EAST=TagKey.create(Registries.ENTITY_TYPE,new ResourceLocation("simpleenemymod","ru_units"));
    private static final TagKey<EntityType<?>> WEST=TagKey.create(Registries.ENTITY_TYPE,new ResourceLocation("simpleenemymod","us_units"));
    private static final TagKey<EntityType<?>> PMC=TagKey.create(Registries.ENTITY_TYPE,new ResourceLocation("simpleenemymod","pmc_units"));
    public static boolean force(Entity entity){return entity instanceof Mob&&entity.getType().is(FORCES);}
    public static int faction(Entity entity){
        if(!force(entity))return -1;
        var type=entity.getType();String id=ForgeRegistries.ENTITY_TYPES.getKey(type).toString();
        if(type.is(EAST)||id.equals("tacz_sewv:ru_combat_engineer"))return 0;
        if(type.is(WEST)||id.equals("tacz_sewv:us_combat_engineer"))return 1;
        return type.is(PMC)||id.equals("tacz_sewv:pmc_commander")?2:-1;
    }
    public static boolean vanillaHostile(Entity entity){
        if(entity==null)return false;
        var id=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return entity instanceof Mob&&id!=null&&id.getNamespace().equals("minecraft")&&entity.getType().getCategory()==MobCategory.MONSTER;
    }
    public static boolean enemies(Entity a,Entity b){
        if(a==null||b==null||a==b||!RespawnRules.enabled(a.level()))return false;
        int one=faction(a),two=faction(b);
        if(one>=0&&two>=0)return one!=two;
        return (vanillaHostile(a)&&force(b))||(force(a)&&vanillaHostile(b));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void join(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!(event.getEntity() instanceof Mob mob)||!vanillaHostile(mob))return;
        if(!(mob instanceof EnderDragon)&&mob.targetSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof FactionTargetGoal))
            mob.targetSelector.addGoal(3,new FactionTargetGoal(mob));
        // The command-only giant has no vanilla melee goal despite being a hostile mob.
        if(mob instanceof Giant&&mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof GiantFactionMeleeGoal))
            mob.goalSelector.addGoal(2,new GiantFactionMeleeGoal((Giant)mob));
    }
    private static final class GiantFactionMeleeGoal extends MeleeAttackGoal {
        GiantFactionMeleeGoal(Giant mob){super(mob,1.0,true);}
        @Override public boolean canUse(){return enemies(mob,mob.getTarget())&&super.canUse();}
        @Override public boolean canContinueToUse(){return enemies(mob,mob.getTarget())&&super.canContinueToUse();}
    }
    public static void supportTargets(Mob unit,GoalSelector targets){
        if(targets.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof SupportMonsterGoal))
            targets.addGoal(3,new SupportMonsterGoal(unit));
    }
    private static final class SupportMonsterGoal extends NearestAttackableTargetGoal<Mob> {
        SupportMonsterGoal(Mob mob){super(mob,Mob.class,10,true,false,FactionHostility::vanillaHostile);}
        @Override public boolean canUse(){return RespawnRules.enabled(mob.level())&&force(mob)&&super.canUse();}
        @Override public boolean canContinueToUse(){return enemies(mob,mob.getTarget())&&super.canContinueToUse();}
    }
    public static final class FactionTargetGoal extends NearestAttackableTargetGoal<Mob> {
        public FactionTargetGoal(Mob mob){super(mob,Mob.class,10,true,false,FactionHostility::force);}
        @Override public boolean canUse(){return RespawnRules.enabled(mob.level())&&super.canUse();}
        @Override public boolean canContinueToUse(){return RespawnRules.enabled(mob.level())&&force(mob.getTarget())&&super.canContinueToUse();}
        @Override public void start(){super.start();rememberTarget();}
        private void rememberTarget(){
            if(target==null||!target.isAlive())return;
            var brain=mob.getBrain();
            if(brain.checkMemory(MemoryModuleType.ANGRY_AT,MemoryStatus.REGISTERED))brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT,target.getUUID(),600L);
            if(mob instanceof Warden w){w.increaseAngerAt(target,80,false);w.setAttackTarget(target);}
            else if(brain.checkMemory(MemoryModuleType.ATTACK_TARGET,MemoryStatus.REGISTERED))brain.setMemory(MemoryModuleType.ATTACK_TARGET,target);
        }
        @Override public void stop(){
            var brain=mob.getBrain();
            if(brain.checkMemory(MemoryModuleType.ATTACK_TARGET,MemoryStatus.REGISTERED)&&brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null)==target)
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            if(mob.getTarget()==target)super.stop();
            target=null;
        }
    }
    @SubscribeEvent public void dragon(LivingEvent.LivingTickEvent event){
        if(!(event.getEntity() instanceof EnderDragon dragon)||dragon.level().isClientSide||dragon.isNoAi()||dragon.tickCount%20!=0||!RespawnRules.enabled(dragon.level()))return;
        if(dragon.getPhaseManager().getCurrentPhase().getPhase()!=EnderDragonPhase.HOLDING_PATTERN)return;
        // Dragons bypass GoalSelector. Reuse their native strafe phase, which accepts any LivingEntity.
        Mob target=dragon.level().getEntitiesOfClass(Mob.class,dragon.getBoundingBox().inflate(64),
                m->force(m)&&m.isAlive()&&dragon.canAttack(m)&&dragon.hasLineOfSight(m)).stream()
                .min(java.util.Comparator.comparingDouble(dragon::distanceToSqr)).orElse(null);
        if(target!=null){dragon.setTarget(target);dragon.getPhaseManager().setPhase(EnderDragonPhase.STRAFE_PLAYER);dragon.getPhaseManager().getPhase(EnderDragonPhase.STRAFE_PLAYER).setTarget(target);}
    }
}
