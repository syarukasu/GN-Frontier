package local.gnfrontier.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FrontierGuideScreen extends Screen {
    private final Screen parent;
    private int page;
    private int scroll;
    public FrontierGuideScreen(Screen parent){super(Component.translatable("gui.gnfrontier.title.guide"));this.parent=parent;}
    @Override protected void init() {
        int x=Math.max(18,(width-440)/2),w=Math.min(440,width-36),third=(w-12)/3;
        addRenderableWidget(new FrontierButton(x,height-36,third,22,Component.translatable("gui.gnfrontier.guide.previous"),b->{page=(page+3)%4;scroll=0;},false));
        addRenderableWidget(new FrontierButton(x+third+6,height-36,third,22,Component.translatable("gui.gnfrontier.guide.next"),b->{page=(page+1)%4;scroll=0;},false));
        addRenderableWidget(new FrontierButton(x+(third+6)*2,height-36,third,22,Component.translatable("gui.back"),b->onClose(),false));
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean mouseScrolled(double x,double y,double amount){scroll=Math.max(0,scroll-(int)Math.signum(amount)*3);return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==266||key==267){scroll=Math.max(0,scroll+(key==267?5:-5));return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        FrontierTheme.background(g,width,height,true);int x=Math.max(18,(width-440)/2),w=Math.min(440,width-36);
        FrontierTheme.pixelText(g,"FIELD GUIDE",x,36,2,FrontierTheme.ACCENT);
        g.drawString(font,Component.translatable("gui.gnfrontier.guide."+page+".title"),x,68,FrontierTheme.INK,false);
        var lines=font.split(Component.translatable("gui.gnfrontier.guide."+page+".body"),w-12);
        int shown=Math.max(1,(height-145)/12);scroll=Math.min(scroll,Math.max(0,lines.size()-shown));
        for(int i=0;i<shown&&i+scroll<lines.size();i++)g.drawString(font,lines.get(i+scroll),x,94+i*12,FrontierTheme.INK,false);
        if(lines.size()>shown) {
            int barHeight=shown*12,thumb=Math.max(8,barHeight*shown/lines.size()),top=94+(barHeight-thumb)*scroll/(lines.size()-shown);
            g.fill(x+w-2,94,x+w,94+barHeight,0xff294447);g.fill(x+w-2,top,x+w,top+thumb,FrontierTheme.ACCENT);
        }
        g.drawString(font,(page+1)+" / 4",x+w-30,68,FrontierTheme.MUTED,false);super.render(g,mx,my,delta);
    }
}
