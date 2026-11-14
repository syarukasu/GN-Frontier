package local.gnfrontier.mixin;

import local.gnfrontier.RespawnRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Cover Forge-aware custom explosions as well as the detonation-list filter. */
@Mixin(BedBlock.class)
public abstract class BedExplosionMixin extends Block {
    protected BedExplosionMixin(Properties properties){super(properties);}
    @Override public float getExplosionResistance(BlockState state,BlockGetter level,BlockPos pos,Explosion explosion){
        if(level instanceof ServerLevel server&&RespawnRules.explosionProtectedBed(server,pos))return Float.MAX_VALUE;
        return super.getExplosionResistance(state,level,pos,explosion);
    }
    @Override public void onBlockExploded(BlockState state,Level level,BlockPos pos,Explosion explosion){
        if(level instanceof ServerLevel server&&RespawnRules.explosionProtectedBed(server,pos))return;
        super.onBlockExploded(state,level,pos,explosion);
    }
}
