package local.gnfrontier.mixin;

import local.gnfrontier.FactionHostility;
import local.gnfrontier.RespawnRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mob's default canAttackType excludes ghasts even for otherwise hostile soldiers. */
@Mixin(value=Mob.class,remap=false)
public abstract class FactionGhastTargetMixin {
    @Inject(method="m_6549_",at=@At("HEAD"),cancellable=true)
    private void gn$ghastEnemy(EntityType<?> type,CallbackInfoReturnable<Boolean> result){
        Mob self=(Mob)(Object)this;
        if(type==EntityType.GHAST&&RespawnRules.enabled(self.level())&&FactionHostility.force(self))result.setReturnValue(true);
    }
}
