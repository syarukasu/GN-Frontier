package local.gnfrontier.mixin;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=LevelLoadingScreen.class,remap=false)
public interface LevelLoadingAccess {
    @Accessor("f_96138_") StoringChunkProgressListener gn$progressListener();
}
