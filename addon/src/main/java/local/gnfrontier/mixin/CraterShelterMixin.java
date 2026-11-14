package local.gnfrontier.mixin;

import local.gnfrontier.Gameplay;
import local.gnfrontier.RespawnRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Also protect beds if the pack owner disables asynchronous crater processing. */
@Mixin(targets="com.vinlanx.explosionoverhaul.CraterDeformer",remap=false)
public abstract class CraterShelterMixin {
    @Redirect(method={"getCraterBlocks","applyLargeExplosionLogic","applySmallExplosionLogic"},at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;m_60800_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private static float gn$shelterResistance(BlockState state,BlockGetter view,BlockPos pos){
        if(view instanceof ServerLevel level&&RespawnRules.enabled(level)
                &&(Gameplay.protectedAt(level,pos)||RespawnRules.explosionProtectedBed(level,pos)))return Float.MAX_VALUE;
        return state.getDestroySpeed(view,pos);
    }
}
