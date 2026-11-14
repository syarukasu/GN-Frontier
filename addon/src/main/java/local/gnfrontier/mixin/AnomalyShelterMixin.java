package local.gnfrontier.mixin;

import local.gnfrontier.RespawnRules;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anomaly Zone's lethal-dose fallback calls Player.kill after canceled damage. */
@Mixin(targets = "com.anomalyzone.Contamination", remap = false)
public abstract class AnomalyShelterMixin {
    @Inject(method = "killByDose(Lnet/minecraft/server/level/ServerPlayer;I)V",
            at = @At("HEAD"), cancellable = true)
    private static void gn$honorSpawnProtection(ServerPlayer player, int kind, CallbackInfo ci) {
        // Keep accumulated contamination and resume ordinary danger outside protection.
        // Administrative /kill does not pass through this method and remains effective.
        if (RespawnRules.protectedFromPull(player)) ci.cancel();
    }
}
