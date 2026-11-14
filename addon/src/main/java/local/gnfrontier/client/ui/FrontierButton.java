package local.gnfrontier.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class FrontierButton extends Button {
    private final boolean primary;
    public FrontierButton(int x,int y,int width,int height,Component text,OnPress press,boolean primary) {
        super(x,y,width,height,text,press,DEFAULT_NARRATION);this.primary=primary;
    }
    @Override public void renderWidget(GuiGraphics g,int mouseX,int mouseY,float delta) {
        boolean selected=isHoveredOrFocused();int x=getX(),y=getY();
        g.fill(x,y,x+width,y+height,selected?0xef29494a:primary?0xdf253b3b:0xb5112428);
        g.fill(x,y,x+(primary||selected?3:1),y+height,active?(selected||primary?FrontierTheme.ACCENT:0xff496666):0xff334145);
        g.fill(x,y+height-1,x+width,y+height,selected?0xff7b9387:0xff314c4e);
        g.drawString(Minecraft.getInstance().font,getMessage(),x+11,y+(height-9)/2,active?FrontierTheme.INK:0xff75817f,false);
        if(primary&&width>150)g.drawString(Minecraft.getInstance().font,">",x+width-16,y+(height-9)/2,FrontierTheme.ACCENT,false);
    }
}
