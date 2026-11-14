package local.gnfrontier.mixin;
import local.gnfrontier.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="com.raiiiden.doomsday_decoration.config.BreakRules",remap=false)
public abstract class DoomsdayDropsMixin {
    @Inject(method="dropsNothing",at=@At("HEAD"),cancellable=true)
    private static void gn$salvage(BlockState state,CallbackInfoReturnable<Boolean> cir) {
        var server=ServerLifecycleHooks.getCurrentServer();
        if(server!=null && server.overworld()!=null && WorldState.get(server.overworld()).enabled
            && (state.is(Frontier.SALVAGE_VEHICLES)||state.is(Frontier.SALVAGE_APPLIANCES)||state.is(Frontier.SALVAGE_METAL)))cir.setReturnValue(false);
    }
}
