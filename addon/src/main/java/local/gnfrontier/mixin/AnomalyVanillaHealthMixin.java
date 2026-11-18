package local.gnfrontier.mixin;

import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.anomalyzone.HealthOverlay", remap = false)
public abstract class AnomalyVanillaHealthMixin {
    @Shadow private static boolean showHp() { throw new AssertionError(); }
    @Shadow private static boolean showSt() { throw new AssertionError(); }

    @Inject(method = "onRenderOverlay", at = @At("HEAD"), cancellable = true)
    private static void gnfrontier$keepHeartsWhenPanelIsHidden(RenderGuiOverlayEvent.Pre event, CallbackInfo ci) {
        // The upstream handler suppresses vanilla hearts even when both of its rows are hidden.
        if (!showHp() && !showSt()) ci.cancel();
    }
}
