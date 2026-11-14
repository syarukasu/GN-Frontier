package local.gnfrontier.mixin;

import local.gnfrontier.Gameplay;
import local.gnfrontier.RespawnRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="com.cyberbutcher.shrapnelexplosionsmod.logic.BlocksInteraction",remap=false)
public abstract class FragmentShelterMixin {
    @Inject(method="handleHit",at=@At("HEAD"),cancellable=true)
    private static void gn$stopFragments(ThrowableProjectile fragment,BlockHitResult hit,BlockState state,CallbackInfoReturnable<Boolean> ci){
        if(fragment.level() instanceof ServerLevel level&&RespawnRules.enabled(level)
                &&(Gameplay.protectedAt(level,hit.getBlockPos())||RespawnRules.explosionProtectedBed(level,hit.getBlockPos()))){
            fragment.discard();ci.setReturnValue(true);
        }
    }
}
