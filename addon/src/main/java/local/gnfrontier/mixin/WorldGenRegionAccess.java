package local.gnfrontier.mixin;
import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=WorldGenRegion.class,remap=false)
public interface WorldGenRegionAccess {
    @Accessor("f_143481_") int gn$writeRadius();
}
