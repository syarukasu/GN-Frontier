package local.gnfrontier.mixin;

import com.tacz.guns.client.resource.pojo.display.attachment.ScopeLensConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.renderer.scope.ScopeLensRenderer$Lens", remap = false)
public interface TaczScopeLensAccess {
    @Accessor("centerU") float gnfrontier$centerU();
    @Accessor("centerV") float gnfrontier$centerV();
    @Accessor("radiusU") float gnfrontier$radiusU();
    @Accessor("radiusV") float gnfrontier$radiusV();
    @Accessor("config") ScopeLensConfig gnfrontier$config();
}
