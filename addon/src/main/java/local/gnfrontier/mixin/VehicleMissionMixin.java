package local.gnfrontier.mixin;

import local.gnfrontier.CampaignEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Superb vehicles are Entity, not LivingEntity; use the actual destruction callback. */
@Mixin(targets = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity", remap = false)
public abstract class VehicleMissionMixin {
    @Shadow public abstract DamageSource getLastDamageSource();
    @Shadow public abstract long getLastDamageStamp();
    @Shadow public abstract float getHealth();
    @Inject(method = "destroy", at = @At("HEAD"))
    private void gn$destroyed(CallbackInfo ci) {
        if (getHealth() <= 0) CampaignEvents.vehicleDestroyed((Entity)(Object)this, getLastDamageSource(), getLastDamageStamp());
    }
}
