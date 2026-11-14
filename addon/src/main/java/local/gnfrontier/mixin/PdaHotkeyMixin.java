package local.gnfrontier.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets="com.kltyton.campchat.client.input.CampChatKeyMappings",remap=false)
public abstract class PdaHotkeyMixin {
    // Insert is free in the shipped pack; M and P already have other actions.
    @ModifyArg(method="<clinit>",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)V"),index=2)
    private static int gnfrontier$pdaKey(int original){return 260;}
}
