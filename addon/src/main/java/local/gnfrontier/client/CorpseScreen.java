package local.gnfrontier.client;

import local.gnfrontier.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Field inventory with explicit search compartments; no chest texture or hidden item tooltips. */
public final class CorpseScreen extends AbstractContainerScreen<CorpseMenu> {
    private final Button[] searches=new Button[2];
    public CorpseScreen(CorpseMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=350;imageHeight=236;}
    private Component tr(String key){return Component.translatable("screen.gnfrontier.corpse."+key);}
    @Override protected void init(){
        super.init();
        for(int i=0;i<2;i++){final int group=i;searches[i]=addRenderableWidget(Button.builder(tr("search"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,group)).bounds(leftPos+266,topPos+(i==0?76:100),72,16).build());}
    }
    @Override protected void containerTick(){
        for(int i=0;i<2;i++){boolean done=menu.completed(i);searches[i].active=!done;searches[i].setMessage(tr(done?"searched":menu.activeSection()==i?"pause":"search"));}
    }
    private void panel(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,0xF0181C1D);g.renderOutline(x,y,w,h,0xFF505653);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;panel(g,x,y,imageWidth,imageHeight);
        g.fill(x+1,y+1,x+imageWidth-1,y+22,0xFF343D36);
        g.fill(x+177,y+23,x+178,y+imageHeight-8,0xFF505653);
        for(var slot:menu.slots){int sx=x+slot.x,sy=y+slot.y;g.fill(sx-1,sy-1,sx+17,sy+17,0xFF080B0C);g.renderOutline(sx-1,sy-1,18,18,0xFF424B46);}
        if(minecraft.player!=null)InventoryScreen.renderEntityInInventoryFollowsMouse(g,x+89,y+120,39,(float)(x+89-mx),(float)(y+70-my),minecraft.player);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,tr("inventory"),10,8,0xE4DFCF,false);
        var lines=font.split(title,157);g.drawString(font,lines.get(0),184,8,0xE4DFCF,false);
        g.drawString(font,tr("equipment"),184,26,0xBECAA9,false);
        String[] labels={"primary","secondary","head","chest","legs","feet"};
        for(int i=0;i<6;i++)g.drawString(font,tr(labels[i]),186+i*25,60,0xAAB5AA,false);
        g.drawString(font,tr("pockets"),184,71,0xBECAA9,false);
        g.drawString(font,tr("storage"),184,105,0xBECAA9,false);
        g.drawString(font,tr("carry"),10,127,0xBECAA9,false);
        g.drawString(font,tr("hint"),10,224,0x929C91,false);
        for(int slot=6;slot<54;slot++)if(!menu.known(slot)){
            var s=menu.getSlot(slot);g.fill(s.x,s.y,s.x+16,s.y+16,0xFF252B29);
            g.drawCenteredString(font,Component.literal("?"),s.x+8,s.y+4,0x7C8981);
            if(menu.pendingSlot()==slot&&menu.activeSection()>=0){
                g.fill(s.x,s.y+14,s.x+Math.min(16,16*menu.elapsed()/menu.duration()),s.y+16,0xFFC2B47D);
            }
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        if(hoveredSlot!=null&&hoveredSlot.index<54&&!menu.known(hoveredSlot.index))g.renderTooltip(font,tr("unsearched"),mx,my);
    }
}
