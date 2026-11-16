package local.gnfrontier.client;

import java.util.*;
import local.gnfrontier.*;
import local.gnfrontier.core.GridTradePlanner;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Field maintenance: select a carried gun without moving or extracting inventory items. */
@Mod.EventBusSubscriber(modid=Frontier.ID,value=Dist.CLIENT)
public final class WeaponRepairScreen extends AbstractContainerScreen<WeaponRepairMenu> {
    private final List<Button> gunButtons=new ArrayList<>();
    private Button repair,previous,next;
    private int page;
    public WeaponRepairScreen(WeaponRepairMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=340;imageHeight=224;}
    private static Component tr(String key,Object...args){return Component.translatable("screen.gnfrontier.repair."+key,args);}
    private List<Integer> guns(){List<Integer> result=new ArrayList<>();for(int i=0;i<36;i++)if(GridTradePlanner.usable(i)&&WeaponRepairKit.isGun(menu.getSlot(i).getItem()))result.add(i);return result;}
    private void send(int button){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,button);}
    @Override protected void init(){
        super.init();gunButtons.clear();
        for(int row=0;row<6;row++){final int n=row;gunButtons.add(addRenderableWidget(Button.builder(Component.empty(),b->{var guns=guns();int at=page*6+n;if(at<guns.size())send(guns.get(at));}).bounds(leftPos+170,topPos+44+row*22,158,20).build()));}
        previous=addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;update();}).bounds(leftPos+170,topPos+182,20,20).build());
        next=addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;update();}).bounds(leftPos+214,topPos+182,20,20).build());
        repair=addRenderableWidget(Button.builder(tr("button"),b->send(WeaponRepairMenu.REPAIR)).bounds(leftPos+238,topPos+182,90,20).build());update();
    }
    private void update(){
        var guns=guns();int pages=Math.max(1,(guns.size()+5)/6);page=Math.max(0,Math.min(page,pages-1));
        for(int row=0;row<6;row++){
            var b=gunButtons.get(row);int at=page*6+row;b.visible=at<guns.size();
            if(b.visible){ItemStack gun=menu.getSlot(guns.get(at)).getItem();b.setMessage(Component.literal(font.plainSubstrByWidth(gun.getHoverName().getString(),144)));b.active=menu.selected()!=guns.get(at)||menu.status()==WeaponRepairKit.Status.CHANGED;}
        }
        previous.active=page>0;next.active=page+1<pages;repair.active=menu.canRepair();
    }
    @Override protected void containerTick(){update();}
    @Override public void resize(net.minecraft.client.Minecraft mc,int width,int height){gunButtons.clear();super.resize(mc,width,height);}
    private void panel(GuiGraphics g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+h,color);g.renderOutline(x,y,w,h,0xff525e50);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;panel(g,x,y,imageWidth,imageHeight,0xf51a201c);g.fill(x+1,y+1,x+imageWidth-1,y+27,0xff364231);
        panel(g,x+10,y+34,146,143,0xff111712);panel(g,x+166,y+28,166,149,0xff111712);
        var kit=ForgeRegistries.ITEMS.getValue(WeaponRepairKit.KIT);if(kit!=null)g.renderItem(new ItemStack(kit),x+11,y+6);
        var gun=menu.selectedGun();if(!gun.isEmpty()){
            g.pose().pushPose();g.pose().translate(x+59,y+42,0);g.pose().scale(3,3,3);g.renderItem(gun,0,0);g.pose().popPose();
        }
        int max=menu.maximum(),condition=max<=0?0:Math.max(0,max-menu.damage());
        g.fill(x+18,y+141,x+148,y+149,0xff35392f);
        if(max>0)g.fill(x+18,y+141,x+18+(int)(130L*condition/max),y+149,0xff9baa68);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,34,10,0xe1dfc8,false);g.drawString(font,tr("choose"),171,31,0xbac5a1,false);
        var gun=menu.selectedGun();var name=font.split(gun.isEmpty()?tr("no_guns"):gun.getHoverName(),130);
        for(int i=0;i<Math.min(2,name.size());i++)g.drawString(font,name.get(i),18,99+i*10,0xe0dfc8,false);
        int max=menu.maximum();
        g.drawString(font,max>0?tr("condition",Math.max(0,max-menu.damage()),max):tr("unsupported"),18,126,0xbec8aa,false);
        if(max>0)g.drawString(font,tr("after",Math.max(0,max-menu.nextDamage()),max),18,156,0xbec8aa,false);
        g.drawString(font,tr("stock",menu.kitCount()),12,183,0xdfdbc0,false);g.drawString(font,tr("amount",menu.percent()),12,197,0x9fae91,false);
        g.drawCenteredString(font,Component.literal(Integer.toString(page+1)),202,188,0xdedbc2);
        var status=menu.status();Component message=status==WeaponRepairKit.Status.REPAIRED?tr("repaired",menu.lastRestored()):tr(status==WeaponRepairKit.Status.READY?"hint":status.name().toLowerCase(Locale.ROOT));
        g.drawString(font,message,12,212,status==WeaponRepairKit.Status.REPAIRED?0xbad38a:0xc4baa0,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);
        if(mx>=leftPos+18&&mx<leftPos+148&&my>=topPos+36&&my<topPos+119&&!menu.selectedGun().isEmpty())g.renderTooltip(font,menu.selectedGun(),mx,my);
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){
        if(WeaponRepairKit.isKit(event.getItemStack())){event.getToolTip().add(tr("usage").copy().withStyle(net.minecraft.ChatFormatting.GRAY));event.getToolTip().add(tr("amount",PrototypeConfig.WEAPON_REPAIR_PERCENT.get()).copy().withStyle(net.minecraft.ChatFormatting.DARK_GREEN));}
    }
}
