package local.gnfrontier.client.ui;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Reuses vanilla/Forge actions and account restrictions, with a pack-owned layout. */
public final class FrontierTitleScreen extends TitleScreen {
    private boolean compact;
    private int left,menuWidth,menuY;
    private int arrangedChildren=-1;
    @Override protected void init() {
        super.init();FrontierTheme.fontsReady=true;arrangedChildren=-1;
        Map<String,Button> original=new LinkedHashMap<>();
        for(var child:children())if(child instanceof Button b) {
            String key=b.getMessage().getContents() instanceof TranslatableContents t?t.getKey():b.getMessage().getString();
            original.put(key,b);
        }
        clearWidgets();compact=width<650||height<360;
        left=compact?24:Math.max(32,width/16);menuWidth=compact?Math.min(380,width-48):236;
        menuY=compact?Math.max(93,height/2-18):Math.max(182,height/2-30);
        int h=compact?22:30,gap=6,half=(menuWidth-gap)/2,row=menuY;
        String single=minecraft.isDemo()?"menu.playdemo":"menu.singleplayer";
        if(compact) {
            nativeButton(original,single,left,row,half,h,true,null);
            nativeButton(original,"menu.multiplayer",left+half+gap,row,half,h,false,null);row+=h+gap;
        } else {
            nativeButton(original,single,left,row,menuWidth,h,true,null);row+=h+gap;
            nativeButton(original,"menu.multiplayer",left,row,menuWidth,h,false,null);row+=h+gap;
        }
        nativeButton(original,"fml.menu.mods",left,row,half,h,false,null);
        nativeButton(original,"menu.options",left+half+gap,row,half,h,false,null);row+=h+gap;
        addRenderableWidget(new FrontierButton(left,row,half,h,Component.translatable("gui.gnfrontier.title.guide"),b->minecraft.setScreen(new FrontierGuideScreen(this)),false));
        nativeButton(original,"menu.quit",left+half+gap,row,half,h,false,null);
        int footerY=height-39,smallWidth=Math.min(90,(width-48-18)/4);
        nativeButton(original,"narrator.button.language",24,footerY,smallWidth,20,false,"gui.gnfrontier.title.language");
        nativeButton(original,"narrator.button.accessibility",24+smallWidth+6,footerY,smallWidth,20,false,"gui.gnfrontier.title.accessibility");
        nativeButton(original,"menu.online",24+(smallWidth+6)*2,footerY,smallWidth,20,false,"gui.gnfrontier.title.realms");
        nativeButton(original,COPYRIGHT_TEXT.getString(),24+(smallWidth+6)*3,footerY,smallWidth,20,false,"gui.gnfrontier.title.credits");
        // Preserve extra native actions (including the demo reset action) without executing them.
        int extraY=row+h+gap;
        for(var entry:original.entrySet()) {Button b=entry.getValue();if(extraY+20<footerY) {nativeButtonCopy(b,left,extraY,menuWidth,20,false,b.getMessage());extraY+=23;}}
    }
    private void nativeButton(Map<String,Button> map,String key,int x,int y,int w,int h,boolean primary,String label) {
        Button original=map.remove(key);if(original==null)return;
        nativeButtonCopy(original,x,y,w,h,primary,label==null?original.getMessage():Component.translatable(label));
    }
    private void nativeButtonCopy(Button original,int x,int y,int w,int h,boolean primary,Component label) {
        var copy=new FrontierButton(x,y,w,h,label,b->original.onPress(),primary);
        copy.active=original.active;copy.visible=original.visible;copy.setTooltip(original.getTooltip());addRenderableWidget(copy);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        if(arrangedChildren!=children().size()) {
            int right=width-24;
            for(var child:children())if(child instanceof AbstractWidget widget&&!(widget instanceof FrontierButton)) {
                right-=widget.getWidth();widget.setX(right);widget.setY(28+(22-widget.getHeight())/2);right-=8;
            }
            arrangedChildren=children().size();
        }
        FrontierTheme.background(g,width,height,false);
        if(compact) {
            FrontierTheme.pixelText(g,"GN FRONTIER",left,35,3,FrontierTheme.INK);
            g.drawString(font,Component.translatable("gui.gnfrontier.title.tagline"),left,66,FrontierTheme.MUTED,false);
        } else {
            FrontierTheme.pixelText(g,"GN",left,49,8,FrontierTheme.INK);
            FrontierTheme.pixelText(g,"FRONTIER",left,118,4,FrontierTheme.INK);
            g.drawString(font,Component.translatable("gui.gnfrontier.title.tagline"),left,158,FrontierTheme.MUTED,false);
            int right=width-230;
            FrontierTheme.pixelText(g,"EXPLORE / RECLAIM / ASCEND",right,height-112,1,FrontierTheme.ACCENT);
            FrontierTheme.paragraph(g,Component.translatable("gui.gnfrontier.title.world"),right,height-91,195,FrontierTheme.INK,3);
        }
        for(var renderable:renderables)renderable.render(g,mouseX,mouseY,delta);
        if(width>=600)g.drawString(font,"Minecraft 1.20.1",width-font.width("Minecraft 1.20.1")-24,height-16,FrontierTheme.MUTED,false);
    }
}
