package local.gnfrontier.mixin;

import local.gnfrontier.client.PdaScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.kltyton.campchat.client.gui.CampChatScreen",remap=false)
public abstract class NativePdaPagesMixin {
    @Shadow private int bgX;
    @Shadow private int bgY;
    @Shadow private float layoutScale;
    @Shadow private float textScale;
    @Shadow private void drawBackgroundTexture(GuiGraphics g){throw new AssertionError();}
    @Shadow private void drawTopTabs(GuiGraphics g){throw new AssertionError();}

    @Redirect(method="computeLayout",at=@At(value="INVOKE",target="Lnet/minecraft/util/Mth;m_14036_(FFF)F",ordinal=0),require=1)
    private float gnfrontier$videoFrame(float value,float min,float max){
        Screen screen=(Screen)(Object)this;
        // The reference fills the display with the PDA's content; its outer case extends offscreen.
        return Math.min(screen.width/1700f,screen.height/960f);
    }
    @Inject(method="computeLayout",at=@At("TAIL"))
    private void gnfrontier$readableTabs(CallbackInfo ci){textScale=layoutScale*2.6f;}

    @Inject(method="m_88315_",at=@At("HEAD"),cancellable=true)
    private void gnfrontier$render(GuiGraphics g,int mx,int my,float dt,CallbackInfo ci){
        Screen screen=(Screen)(Object)this;
        if(PdaScreen.tab(screen).equals("MAP"))return;
        screen.renderBackground(g);
        drawBackgroundTexture(g);
        PdaScreen.renderPage(g,screen,bgX,bgY,layoutScale,mx,my);
        drawTopTabs(g);
        ci.cancel();
    }
    @Inject(method="m_6375_",at=@At("HEAD"),cancellable=true)
    private void gnfrontier$click(double x,double y,int button,CallbackInfoReturnable<Boolean> ci){
        if(!PdaScreen.tab((Screen)(Object)this).equals("MAP")&&y>=bgY+240*layoutScale)
            ci.setReturnValue(PdaScreen.clicked(x,y,button));
    }
    @Inject(method="m_6050_",at=@At("HEAD"),cancellable=true)
    private void gnfrontier$scroll(double x,double y,double delta,CallbackInfoReturnable<Boolean> ci){
        if(!PdaScreen.tab((Screen)(Object)this).equals("MAP")){PdaScreen.scroll(x,y,delta);ci.setReturnValue(true);}
    }
    @Inject(method="m_7933_",at=@At("HEAD"),cancellable=true)
    private void gnfrontier$key(int key,int scan,int modifiers,CallbackInfoReturnable<Boolean> ci){
        if(key!=256&&!PdaScreen.tab((Screen)(Object)this).equals("MAP"))ci.setReturnValue(false);
    }
    @Inject(method="m_5534_",at=@At("HEAD"),cancellable=true)
    private void gnfrontier$character(char code,int modifiers,CallbackInfoReturnable<Boolean> ci){
        if(!PdaScreen.tab((Screen)(Object)this).equals("MAP"))ci.setReturnValue(false);
    }
    @Inject(method="m_86600_",at=@At("TAIL"))
    private void gnfrontier$tick(CallbackInfo ci){PdaScreen.tick();}
}
