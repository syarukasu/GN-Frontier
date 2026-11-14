package local.gnfrontier.mixin;
import local.gnfrontier.client.ui.FrontierTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraftforge.client.loading.ForgeLoadingOverlay;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ForgeLoadingOverlay.class,remap=false)
public abstract class FrontierForgeLoadingMixin {
    @Shadow @Final private ReloadInstance reload;
    @Inject(method="m_88315_",at=@At("TAIL")) private void gn$brand(GuiGraphics g,int x,int y,float delta,CallbackInfo ci) {
        FrontierTheme.reload(g,(LoadingOverlay)(Object)this,reload);
    }
}
