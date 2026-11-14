package local.gnfrontier.mixin;

import local.gnfrontier.RespawnRules;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Natural Valhalla bubbles must not pull a protected player out of their shelter. */
@Mixin(targets = "com.anomalyzone.valhalla.ValhallaTeleporter", remap = false)
public abstract class AnomalyPortalShelterMixin {
    @Inject(method = "enterValhalla(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), cancellable = true)
    private static void gn$honorSpawnProtection(ServerPlayer player, CallbackInfo ci) {
        // Leaving Valhalla, administrator teleporting and unprotected entry retain their behavior.
        if (RespawnRules.protectedFromPull(player)) ci.cancel();
    }
}
