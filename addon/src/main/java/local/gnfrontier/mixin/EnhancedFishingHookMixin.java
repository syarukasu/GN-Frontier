package local.gnfrontier.mixin;

import local.gnfrontier.EnhancedAiCompat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="insane96mcp.enhancedai.modules.mobs.fisher.FishingHook",remap=false)
public abstract class EnhancedFishingHookMixin {
    @Shadow private Entity hookedIn;
    @Inject(method="retrieve",at=@At("HEAD"),cancellable=true)
    private void gn$protectedRetrieve(boolean inventoryHook,CallbackInfo callback){
        // Recheck at retrieval: protection may begin after the projectile was cast.
        if(EnhancedAiCompat.protectedTarget(hookedIn)){
            ((Entity)(Object)this).discard();
            callback.cancel();
        }
    }
}
