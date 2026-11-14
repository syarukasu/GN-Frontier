package local.gnfrontier.mixin;

import local.gnfrontier.EnhancedAiCompat;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="insane96mcp.enhancedai.modules.mobs.fisher.FishingTargetGoal",remap=false)
public abstract class EnhancedFisherGoalMixin {
    @Shadow @Final private Mob fisher;
    @Inject(method="m_8036_",at=@At("HEAD"),cancellable=true)
    private void gn$protectedTarget(CallbackInfoReturnable<Boolean> result){
        if(EnhancedAiCompat.protectedTarget(fisher.getTarget()))result.setReturnValue(false);
    }
}
