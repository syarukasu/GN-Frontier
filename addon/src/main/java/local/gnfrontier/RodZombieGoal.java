package local.gnfrontier;

import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** A visible, colliding hook pulls a target off a ledge; it never pulls through a wall. */
public final class RodZombieGoal extends Goal {
    private final Zombie zombie;
    private long nextCast;
    private int windup;

    public RodZombieGoal(Zombie zombie) {
        this.zombie=zombie;
        setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));
    }
    private boolean eligible() {
        if(!PrototypeConfig.ROD_ZOMBIES.get() || !RespawnRules.enabled(zombie.level())
                || !zombie.getMainHandItem().is(Items.FISHING_ROD)
                || !(zombie.getTarget() instanceof ServerPlayer target))return false;
        double distance=zombie.distanceToSqr(target);
        return target.isAlive() && !target.isCreative() && !target.isSpectator()
                && !RespawnRules.protectedFromPull(target) && distance>=16
                && distance<=Math.pow(PrototypeConfig.ROD_RANGE.get(),2) && zombie.hasLineOfSight(target);
    }
    @Override public boolean canUse(){return zombie.level().getGameTime()>=nextCast && eligible();}
    @Override public boolean canContinueToUse(){return windup<12 && eligible();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){windup=0;zombie.getNavigation().stop();}
    @Override public void tick(){
        if(!eligible())return;
        ServerPlayer target=(ServerPlayer)zombie.getTarget();
        zombie.getLookControl().setLookAt(target,30,30);
        if(++windup!=10)return;
        ScavengerHook hook=new ScavengerHook(zombie);
        Vec3 direction=target.getBoundingBox().getCenter().subtract(hook.position());
        hook.shoot(direction.x,direction.y+Math.sqrt(direction.x*direction.x+direction.z*direction.z)*.025,direction.z,1.5f,0);
        zombie.level().addFreshEntity(hook);
        zombie.swing(InteractionHand.MAIN_HAND);
        zombie.level().playSound(null,zombie.blockPosition(),SoundEvents.FISHING_BOBBER_THROW,SoundSource.HOSTILE,.8f,.9f);
        nextCast=zombie.level().getGameTime()+PrototypeConfig.ROD_COOLDOWN.get();
    }
    @Override public void stop(){nextCast=Math.max(nextCast,zombie.level().getGameTime()+20);}
}
