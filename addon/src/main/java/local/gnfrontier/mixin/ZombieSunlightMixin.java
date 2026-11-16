package local.gnfrontier.mixin;

import local.gnfrontier.WorldState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Disable sunlight ignition at its source. Existing flames, fire arrows and lava keep their normal behavior. */
@Mixin(Zombie.class)
public abstract class ZombieSunlightMixin {
    @Inject(method="m_5884_",at=@At("HEAD"),cancellable=true,remap=false)
    private void gnfrontier$sunlightOnly(CallbackInfoReturnable<Boolean> ci){
        if(((Zombie)(Object)this).level() instanceof ServerLevel level&&WorldState.get(level).enabled)ci.setReturnValue(false);
    }
}
