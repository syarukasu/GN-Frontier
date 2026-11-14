package local.gnfrontier.mixin;

import local.gnfrontier.WorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Halt processing before fuel/input consumption, including hopper-driven naturally generated furnaces. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceMixin {
    @Inject(method = "m_155013_", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gn$processing(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity, CallbackInfo ci) {
        if (level instanceof ServerLevel server && WorldState.get(server).enabled
            && (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE))) ci.cancel();
    }
}
