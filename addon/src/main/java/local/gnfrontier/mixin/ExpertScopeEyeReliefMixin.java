package local.gnfrontier.mixin;

import local.gnfrontier.client.ScopeEyeRelief;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Pseudo
@Mixin(targets = "com.ori.taczplus.client.pip.LensOverlay", remap = false)
public abstract class ExpertScopeEyeReliefMixin {
    // Run after reticle replay, before Expert releases its depth or stencil mask.
    @Inject(method = "pipLayer", slice = @Slice(from = @At(value = "INVOKE",
            target = "Lcom/ori/taczplus/client/pip/ReticleReplay;replay()V")),
            at = @At(value = "INVOKE", ordinal = 0, target = "Lorg/lwjgl/opengl/GL11;glDepthRange(DD)V"),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    private static void gnfrontier$shadeShaderLens(GuiGraphics gui, CallbackInfo ci, float[] region,
                                                   Minecraft minecraft, int width, int height,
                                                   float x, float y, float radius,
                                                   int left, int right, int bottom, int top, int expertMask) {
        if (ExpertScopeAccess.gnfrontier$nightVision() || ExpertScopeAccess.gnfrontier$thermal()
                || ExpertScopeAccess.gnfrontier$ai()) return;

        if (expertMask == 0) {
            ScopeEyeRelief.render(x, height - y, radius, radius, width, height, ScopeEyeRelief.Mask.CIRCLE);
        } else {
            float yFromBottom = height - y;
            float radiusX = Math.max(x - left, right - x);
            float radiusY = Math.max(yFromBottom - bottom, top - yFromBottom);
            ScopeEyeRelief.render(x, yFromBottom, radiusX, radiusY, width, height,
                    expertMask == 1 ? ScopeEyeRelief.Mask.STENCIL : ScopeEyeRelief.Mask.DEPTH);
        }
    }
}
