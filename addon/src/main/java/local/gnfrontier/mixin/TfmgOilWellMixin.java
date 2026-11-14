package local.gnfrontier.mixin;
import local.gnfrontier.WorldgenBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(targets="com.drmangotea.tfmg.worldgen.deposits.OilWellFeature",remap=false)
public abstract class TfmgOilWellMixin {
    @Redirect(method="m_142674_",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/WorldGenLevel;m_7731_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),remap=false,require=4)
    private boolean gn$boundedWell(WorldGenLevel level,BlockPos pos,BlockState state,int flags){return WorldgenBounds.setBlock(level,pos,state,flags);}
}
