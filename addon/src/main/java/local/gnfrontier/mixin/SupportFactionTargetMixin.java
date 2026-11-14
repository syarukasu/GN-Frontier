package local.gnfrontier.mixin;

import local.gnfrontier.FactionHostility;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Support role changes reset all targets. Reattach monster targeting after each native rebuild. */
@Mixin(targets="com.neoalive.tacz_sewv.entity.ai.goal.SupportUnitGoals",remap=false)
public abstract class SupportFactionTargetMixin {
    @Inject(method={"engineer","combatEngineer"},at=@At("TAIL"),remap=false)
    private static void gn$monsterTargets(@Coerce Object unit,GoalSelector goals,GoalSelector targets,CallbackInfo ci){
        FactionHostility.supportTargets((Mob)unit,targets);
    }
}
