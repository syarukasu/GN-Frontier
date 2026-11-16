package local.gnfrontier.mixin;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="nuparu.tinyinv.world.entity.player.PlayerSlots",remap=false)
public abstract class TinyInvHotbarMixin {
    @Inject(method="getHotbarSlots",at=@At("HEAD"),cancellable=true)
    private static void gnfrontier$five(Player player,CallbackInfoReturnable<Integer> cir){if(!player.isSpectator())cir.setReturnValue(5);}
}
