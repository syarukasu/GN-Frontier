package local.gnfrontier.mixin;

import local.gnfrontier.Gameplay;
import local.gnfrontier.RespawnRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** The asynchronous crater applies section writes without Forge explosion events. */
@Mixin(targets="com.vinlanx.explosionoverhaul.AsyncCraterManager$Job",remap=false)
public abstract class AsyncCraterShelterMixin {
    @Redirect(method="applyBatch",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;m_60800_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float gn$shelterResistance(BlockState state,BlockGetter view,BlockPos pos){
        if(view instanceof ServerLevel level&&RespawnRules.enabled(level)
                &&(Gameplay.protectedAt(level,pos)||RespawnRules.explosionProtectedBed(level,pos)))return Float.MAX_VALUE;
        return state.getDestroySpeed(view,pos);
    }
}
