package local.gnfrontier;
import local.gnfrontier.mixin.WorldGenRegionAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
/** Random-walk worldgen features may leave the writable region; reject writes before the native error path. */
public final class WorldgenBounds {
    private WorldgenBounds() {}
    public static boolean within(int x,int z,int centerX,int centerZ,int radius) {
        return Math.abs((long)(x>>4)-centerX)<=radius&&Math.abs((long)(z>>4)-centerZ)<=radius;
    }
    public static boolean setBlock(WorldGenLevel level,BlockPos pos,BlockState state,int flags) {
        if(level instanceof WorldGenRegion region) {
            var center=region.getCenter();int radius=((WorldGenRegionAccess)region).gn$writeRadius();
            if(!within(pos.getX(),pos.getZ(),center.x,center.z,radius))return false;
        }
        return level.setBlock(pos,state,flags);
    }
}
