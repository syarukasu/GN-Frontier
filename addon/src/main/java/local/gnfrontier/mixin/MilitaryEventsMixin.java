package local.gnfrontier.mixin;
import local.gnfrontier.StrategicEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Installed SEM's three military encounter factories, scoped to the targeted player. */
@Mixin(targets={"net.nekoyuni.SimpleEnemyMod.procedural.events.type.CombatEvent",
    "net.nekoyuni.SimpleEnemyMod.procedural.events.type.PatrolEvent",
    "net.nekoyuni.SimpleEnemyMod.procedural.events.type.CaveExtractionEvent"},remap=false)
public abstract class MilitaryEventsMixin {
    @Inject(method="execute",at=@At("HEAD"),cancellable=true)
    private void gn$ceasefire(ServerLevel level,ServerPlayer player,BlockPos pos,CallbackInfoReturnable<Boolean> cir){
        if(StrategicEvents.protectedPlayer(player))cir.setReturnValue(false);
    }
}
