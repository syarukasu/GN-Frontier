package local.gnfrontier.mixin;

import local.gnfrontier.RespawnRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.class)
public abstract class BedRemovalMixin {
    @Inject(method = "m_6810_", at = @At("HEAD"), remap = false)
    private void gn$bedRemoved(BlockState old, Level level, BlockPos pos, BlockState next, boolean moving, CallbackInfo ci) {
        if (!(level instanceof ServerLevel server) || !(old.getBlock() instanceof BedBlock)) return;
        if (next.is(old.getBlock()) && next.getValue(BedBlock.PART) == old.getValue(BedBlock.PART)
                && next.getValue(BedBlock.FACING) == old.getValue(BedBlock.FACING)) return;
        BlockPos head = old.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(old.getValue(BedBlock.FACING));
        RespawnRules.removedBed(server, head);
    }
}
