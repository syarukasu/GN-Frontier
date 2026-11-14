package local.gnfrontier.mixin;
import local.gnfrontier.WorldState;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.anomalyzone.EmissionHandler",remap=false)
public abstract class EmissionScopeMixin {
    @Inject(method="onServerTick",at=@At("HEAD"),cancellable=true)
    private static void gn$scope(TickEvent.ServerTickEvent event,CallbackInfo ci){
        if(event.getServer().overworld()!=null && !WorldState.get(event.getServer().overworld()).enabled)ci.cancel();
    }
}
