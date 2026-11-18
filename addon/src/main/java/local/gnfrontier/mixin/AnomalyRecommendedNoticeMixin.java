package local.gnfrontier.mixin;

import net.minecraftforge.client.event.ScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pack supplies the recommended content; keep startup on its own title screen. */
@Pseudo
@Mixin(targets="com.anomalyzone.RecommendedModsNotice",remap=false)
public abstract class AnomalyRecommendedNoticeMixin {
    @Inject(method="onScreenOpening",at=@At("HEAD"),cancellable=true)
    private static void gnfrontier$skipBundledRecommendations(ScreenEvent.Opening event,CallbackInfo ci){ci.cancel();}
}
