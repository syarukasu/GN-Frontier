package local.gnfrontier.mixin;
import local.gnfrontier.HotbarRules;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="nuparu.tinyinv.world.inventory.SlotUtils",remap=false)
public abstract class TinyInvSlotRulesMixin {
    @Inject(method="shouldRemoveSlot",at=@At("HEAD"),cancellable=true)
    private static void gnfrontier$blocked(Player player,int slot,CallbackInfoReturnable<Boolean> cir){if(HotbarRules.blocked(slot)&&!player.isSpectator())cir.setReturnValue(true);}
    @Inject(method="replaceSlotStack",at=@At("HEAD"),cancellable=true)
    private static void gnfrontier$keepContents(Player player,int slot,CallbackInfoReturnable<Boolean> cir){
        if(HotbarRules.blocked(slot)){HotbarRules.evacuate(player);cir.setReturnValue(false);}
    }
}
