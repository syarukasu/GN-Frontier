package local.gnfrontier.mixin;

import com.tacz.guns.client.renderer.scope.ScopeVisionMode;
import local.gnfrontier.client.ScopeEyeRelief;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.renderer.scope.ScopeLensRenderer", remap = false)
public abstract class TaczScopeEyeReliefMixin {
    @Unique private static TaczScopeLensAccess gnfrontier$lens;
    @Unique private static int gnfrontier$width;
    @Unique private static int gnfrontier$height;

    @Inject(method = "beginFrame", at = @At("HEAD"))
    private static void gnfrontier$clearLens(CallbackInfo ci) {
        gnfrontier$lens = null;
    }

    @Inject(method = "drawLens", at = @At("HEAD"))
    private static void gnfrontier$trackLens(@Coerce Object lens, int width, int height,
                                             @Coerce Object contour, CallbackInfo ci) {
        gnfrontier$lens = (TaczScopeLensAccess) lens;
        gnfrontier$width = width;
        gnfrontier$height = height;
    }

    // The colour and reticle are complete, and the native lens stencil is still active.
    @Inject(method = "render", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glFramebufferTexture2D(IIIII)V"))
    private static void gnfrontier$shadeLens(CallbackInfo ci) {
        TaczScopeLensAccess lens = gnfrontier$lens;
        gnfrontier$lens = null;
        if (lens == null || lens.gnfrontier$config().getVisionMode() != ScopeVisionMode.NORMAL) return;
        if (ModList.get().isLoaded("taczexpert") && ExpertScopeAccess.gnfrontier$hasShaderLens()) return;

        int width = gnfrontier$width;
        int height = gnfrontier$height;
        ScopeEyeRelief.render(lens.gnfrontier$centerU() * width, lens.gnfrontier$centerV() * height,
                lens.gnfrontier$radiusU() * width, lens.gnfrontier$radiusV() * height,
                width, height, ScopeEyeRelief.Mask.STENCIL);
    }
}
