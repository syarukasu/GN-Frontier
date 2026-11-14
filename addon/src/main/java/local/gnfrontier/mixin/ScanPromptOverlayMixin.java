package local.gnfrontier.mixin;

import net.minecraft.client.Minecraft;
import local.gnfrontier.client.ScanText;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scan setup remains available in-world, but must not cover inventories or the PDA. */
@Mixin(targets={"com.vinlanx.explosionoverhaul.ScanPromptHUD","com.vinlanx.explosionoverhaul.ScanLoadPromptHUD","com.vinlanx.explosionoverhaul.ScanInfoHUD","com.vinlanx.explosionoverhaul.ScanLoadInfoHUD","com.vinlanx.explosionoverhaul.ScanProgressHUD"},remap=false)
public abstract class ScanPromptOverlayMixin {
    @ModifyArg(method="onRenderGuiOverlay",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;m_280488_(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"),index=1)
    private static String gn$localizedPrompt(String value){return ScanText.localize(value);}
    @ModifyArg(method="onRenderGuiOverlay",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Font;m_92895_(Ljava/lang/String;)I"),index=0)
    private static String gn$localizedWidth(String value){return ScanText.localize(value);}
    @Inject(method="onRenderGuiOverlay",at=@At("HEAD"),cancellable=true)
    private static void gnfrontier$hideOverScreens(RenderGuiOverlayEvent.Post event,CallbackInfo ci){
        if(Minecraft.getInstance().screen!=null)ci.cancel();
    }
}
