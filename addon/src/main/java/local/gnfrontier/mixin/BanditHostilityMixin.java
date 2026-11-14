package local.gnfrontier.mixin;

import local.gnfrontier.FactionHostility;
import local.gnfrontier.RespawnRules;
import local.gnfrontier.RegisteredBedGoal;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Bandits' periodic player chase must allow encounters with a nearer armed faction. */
@Mixin(targets="net.chaoscraft.chaoszprojectbandits.PillagerRaidManager",remap=false)
public abstract class BanditHostilityMixin {
    @Redirect(method={"spawnPillagers","updatePillagerPositions"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/monster/Pillager;m_6710_(Lnet/minecraft/world/entity/LivingEntity;)V"))
    private static void gn$encounterTarget(Pillager bandit,LivingEntity player){
        RegisteredBedGoal.markBandit(bandit);
        LivingEntity target=player;
        if(RespawnRules.enabled(bandit.level())){
            double range=Math.min(64,bandit.getAttributeValue(Attributes.FOLLOW_RANGE));
            boolean validPlayer=player!=null&&player.isAlive()&&bandit.canAttack(player)&&!(player instanceof Player p&&(p.isCreative()||p.isSpectator()));
            double playerDistance=validPlayer?bandit.distanceToSqr(player):Double.POSITIVE_INFINITY;
            Mob closest=bandit.level().getEntitiesOfClass(Mob.class,bandit.getBoundingBox().inflate(range),
                m->FactionHostility.force(m)&&m.isAlive()&&bandit.canAttack(m)&&bandit.hasLineOfSight(m)&&bandit.distanceToSqr(m)<playerDistance).stream()
                .min(java.util.Comparator.comparingDouble(bandit::distanceToSqr)).orElse(null);
            if(closest!=null)target=closest;
        }
        bandit.setTarget(target);
    }
    @Redirect(method="updatePillagerPositions",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/ai/control/MoveControl;m_6849_(DDDD)V"))
    private static void gn$allowCombatMovement(MoveControl control,double x,double y,double z,double speed){
        Mob bandit=((BanditMoveControlAccess)control).gn$owner();
        if(!RegisteredBedGoal.running(bandit)&&!FactionHostility.enemies(bandit,bandit.getTarget()))control.setWantedPosition(x,y,z,speed);
    }
}
