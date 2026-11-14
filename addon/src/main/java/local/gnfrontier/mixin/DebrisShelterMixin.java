package local.gnfrontier.mixin;

import local.gnfrontier.Gameplay;
import local.gnfrontier.RespawnRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Shrapnel's crater adapter bypasses the Forge bus before turning blocks into debris. */
@Mixin(targets="com.cyberbutcher.shrapnelexplosionsmod.logic.SecondaryDebrisHandler",remap=false)
public abstract class DebrisShelterMixin {
    @Redirect(method="processExplosion",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;m_60800_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private static float gn$unbreakableShelter(BlockState state,BlockGetter view,BlockPos pos){
        if(view instanceof ServerLevel level&&RespawnRules.enabled(level)
                &&(Gameplay.protectedAt(level,pos)||RespawnRules.explosionProtectedBed(level,pos)))return -1;
        return state.getDestroySpeed(view,pos);
    }
}
