package local.gnfrontier.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import local.gnfrontier.client.PdaScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clip Xaero's completed image to the PDA, without clipping its scaled offscreen framebuffer twice. */
@Pseudo
@Mixin(targets="xaero.map.gui.GuiMap",remap=false)
public abstract class PdaMapClipMixin {
    @Unique private boolean gnfrontier$restoreClip;
    @Unique private final int[] gnfrontier$clipBox=new int[4];
    @Inject(method="m_88315_",at=@At("HEAD"))
    private void gnfrontier$offscreen(GuiGraphics graphics,int x,int y,float dt,CallbackInfo ci){
        gnfrontier$restoreClip=PdaScreen.isNative(Minecraft.getInstance().screen)&&GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if(gnfrontier$restoreClip){GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,gnfrontier$clipBox);RenderSystem.disableScissor();}
    }
    @Inject(method="m_88315_",at=@At(value="INVOKE",target="Lxaero/map/graphics/ImprovedFramebuffer;bindDefaultFramebuffer(Lnet/minecraft/client/Minecraft;)V",shift=At.Shift.AFTER))
    private void gnfrontier$composite(GuiGraphics graphics,int x,int y,float dt,CallbackInfo ci){gnfrontier$restore();}
    @Inject(method="m_88315_",at=@At("RETURN"))
    private void gnfrontier$return(GuiGraphics graphics,int x,int y,float dt,CallbackInfo ci){gnfrontier$restore();}
    @Unique private void gnfrontier$restore(){
        if(gnfrontier$restoreClip){RenderSystem.enableScissor(gnfrontier$clipBox[0],gnfrontier$clipBox[1],gnfrontier$clipBox[2],gnfrontier$clipBox[3]);gnfrontier$restoreClip=false;}
    }
}
