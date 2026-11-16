package local.gnfrontier.client;

import local.gnfrontier.FrontierNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** GN transactions inside the installed CampChat PDA; its own Xaero map and frame stay native. */
public final class PdaScreen {
    private static final String NATIVE="com.kltyton.campchat.client.gui.CampChatScreen";
    private static CompoundTag snapshot=new CompoundTag();
    private static java.lang.reflect.Field tabField;
    private static String currentTab="MAP";
    private static int updateTick, category, selected, listScroll, textScroll, quantity=1, guidePage;
    private static boolean special, guide, selling;
    private static int bx,by,left,top,right,bottom,side,sideRight,rowHeight,visibleRows,totalRows,textLines,textVisible;
    private static float scale,textScale;
    private static final int INK=0xe5e8d1, MUTED=0xb1c19b, WARN=0xe3bf7e;
    private static final List<Hit> hits=new ArrayList<>();
    private record Hit(int x,int y,int w,int h,boolean enabled,Runnable action) {
        boolean contains(double mx,double my){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}
    }
    private static final String[] CATEGORIES={"food","medical","ammo","weapons","scrap","computing","permits"};
    public static boolean isNative(Screen screen){return screen!=null&&screen.getClass().getName().equals(NATIVE);}
    public static boolean signalLost(){return snapshot.getBoolean("enabled")&&snapshot.getBoolean("signalLost");}
    public static void signal(boolean enabled,boolean lost){snapshot.putBoolean("enabled",enabled);snapshot.putBoolean("signalLost",lost);hits.clear();}
    public static void reset(){snapshot=new CompoundTag();hits.clear();}
    public static String tab(Screen screen){
        if(!isNative(screen))return "MAP";
        try {if(tabField==null){tabField=screen.getClass().getDeclaredField("activeTab");tabField.setAccessible(true);}return ((Enum<?>)tabField.get(screen)).name();}
        catch(ReflectiveOperationException e){throw new IllegalStateException("CampChat PDA API changed",e);}
    }
    public static void opened(){updateTick=0;hits.clear();request("refresh","",0);}
    public static void accept(CompoundTag data){
        snapshot=data;Minecraft mc=Minecraft.getInstance();
        if(data.getBoolean("open")&&!isNative(mc.screen))try {mc.setScreen((Screen)Class.forName(NATIVE).getConstructor().newInstance());}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Installed PDA unavailable",e);}
    }
    public static void tick(){if(++updateTick>=40){updateTick=0;request("refresh","",0);}}
    private static void request(String action,String id,int count){FrontierNetwork.CHANNEL.sendToServer(new FrontierNetwork.Action(action,id,count));}
    public static boolean clicked(double x,double y,int button){
        if(button!=0)return false;
        // A refresh may change stock, but all amounts, ownership and claim tokens are checked by the server.
        for(Hit hit:List.copyOf(hits))if(hit.contains(x,y)){if(hit.enabled)hit.action.run();return true;}
        return true;
    }
    public static void scroll(double x,double y,double delta){
        int amount=-(int)Math.signum(delta)*2;
        if(currentTab.equals("CONTACTS")||x>=side)listScroll=clamp(listScroll+amount,0,Math.max(0,totalRows-visibleRows));
        else textScroll=clamp(textScroll+amount,0,Math.max(0,textLines-textVisible));
    }
    public static void renderPage(GuiGraphics g,Screen screen,int x,int y,float s,int mx,int my){
        String active=tab(screen);
        if(!active.equals(currentTab)){currentTab=active;selected=listScroll=textScroll=0;hits.clear();}
        bx=x;by=y;scale=s;textScale=s*2.6f;
        left=px(200);top=py(255);right=px(1398);bottom=py(925);side=px(1465);sideRight=px(1764);
        rowHeight=Math.max(15,Math.round(22*textScale));hits.clear();
        if(signalLost()){
            // Same continuous map area as the source PDA, not the shop/chat's split panels.
            g.fill(px(190),py(243),px(1776),py(990),0xff141810);
            var label=tr("gui.gnfrontier.signal_lost");var font=Minecraft.getInstance().font;
            float size=scale*7f;
            g.pose().pushPose();g.pose().translate(px(983),py(617),0);g.pose().scale(size,size,1);
            g.drawString(font,label,-font.width(label)/2,-font.lineHeight/2,0xe5e2b2,false);g.pose().popPose();return;
        }
        // The video fills the content areas over the frame's unused chat controls.
        g.fill(px(198),py(250),px(1400),py(930),0xff20231e);
        g.fill(px(1465),py(255),px(1764),py(930),0xff20231e);
        if(!snapshot.getBoolean("enabled")){text(g,tr("gui.gnfrontier.disabled"),left,top,right-left,WARN);return;}
        switch(active){case "CONTACTS"->shop(g,mx,my);case "TASKS"->tasks(g,mx,my);case "STATS"->stats(g,mx,my);default->{}}
        String message=snapshot.getString("message");
        if(message.equals("SIGNAL LOST"))message=tr("gui.gnfrontier.signal_lost");
        if(message.equals("STEEL REQUIRED"))message=tr("message.gnfrontier.steel_required");
        if(message.startsWith("message.gnfrontier."))message=tr(message);
        if(!message.isBlank())text(g,message,left,py(953),right-left,WARN);
    }
    private static void shop(GuiGraphics g,int mx,int my){
        int sw=sideRight-side,header=Math.max(13,Math.round(24*textScale));
        int gap=Math.max(10,Math.round(16*textScale));
        text(g,tr("gui.gnfrontier.market.title",tr("gui.gnfrontier.category."+CATEGORIES[category])),left,top,(right-left)*2/3,INK);
        rightText(g,tr("gui.gnfrontier.balance",snapshot.getLong("balance")),right,top,(right-left)/3,MUTED);
        text(g,tr("gui.gnfrontier.market.categories"),side,top,sw,INK);
        int categoryHeader=Math.max(10,Math.round(16*textScale)),categoryGap=Math.max(9,Math.round(14*textScale));
        for(int i=0;i<CATEGORIES.length;i++){final int n=i;centerButton(g,tr("gui.gnfrontier.category."+CATEGORIES[i]),side,top+categoryHeader+i*categoryGap,sw,categoryGap,true,category==i,mx,my,()->{category=n;selected=listScroll=0;quantity=1;});}
        List<CompoundTag> offers=new ArrayList<>();
        for(Tag tag:snapshot.getList("offers",Tag.TAG_COMPOUND)){CompoundTag o=(CompoundTag)tag;if(category(o).equals(CATEGORIES[category]))offers.add(o);}
        if(CATEGORIES[category].equals("computing"))offers.sort(Comparator.comparingInt(o->List.of("mining_rig","i3","pentium","i5","gpu8800","gpu480").indexOf(o.getString("id"))));
        selected=clamp(selected,0,Math.max(0,offers.size()-1));
        int start=top+header,detailY=bottom-Math.round(60*textScale);
        int shopRow=Math.max(18,(detailY-start-5)/6);visibleRows=Math.max(1,(detailY-start-5)/shopRow);totalRows=offers.size();
        listScroll=clamp(listScroll,0,Math.max(0,totalRows-visibleRows));
        for(int i=listScroll;i<offers.size()&&i<listScroll+visibleRows;i++){
            final int index=i;CompoundTag o=offers.get(i);int yy=start+(i-listScroll)*shopRow;
            Hit hit=new Hit(left,yy,right-left,shopRow-2,true,()->{selected=index;quantity=1;});hits.add(hit);
            if(i==selected||hit.contains(mx,my))g.fill(left,yy,right,yy+shopRow-2,i==selected?0xff32352e:0xff292d25);
            ItemStack stack=ItemStack.of(o.getCompound("stack"));float icon=Math.min(1.2f,(shopRow-5)/16f);
            int ix=left+Math.round(16*textScale),iy=yy+(shopRow-Math.round(16*icon))/2;
            g.pose().pushPose();g.pose().translate(ix,iy,0);g.pose().scale(icon,icon,1);g.renderItem(stack,0,0);g.pose().popPose();
            int nameX=left+Math.round(47*textScale),priceWidth=Math.round(65*textScale),ty=yy+(shopRow-Math.round(9*textScale))/2;
            text(g,offerName(o),nameX,ty,right-nameX-priceWidth,o.getBoolean("unlocked")?INK:MUTED);
            long price=o.getLong(selling?"sell":"buy");rightText(g,price>0?"$"+price:tr(selling?"gui.gnfrontier.market.no_buyback":"gui.gnfrontier.sell_only"),right-3,ty,priceWidth,0x96ce8a);
        }
        if(offers.isEmpty()){text(g,tr("gui.gnfrontier.market.no_stock"),left,start,right-left,MUTED);return;}
        CompoundTag o=offers.get(selected);long price=o.getLong(selling?"sell":"buy");
        text(g,offerName(o),left,detailY,right-left,INK);
        String description=o.getString("id").equals("mining_rig")?tr("gui.gnfrontier.market.mining_desc"):tr("gui.gnfrontier.market.supplier",merchant(o.getString("merchant")),o.getInt(selling?"sellStock":"buyStock"));
        text(g,description,left,detailY+gap,right-left,MUTED);
        int actionY=detailY+2*gap,h=gap,w=Math.max(16,Math.round(20*textScale)),qx=left+Math.round(34*textScale);
        text(g,tr("gui.gnfrontier.market.quantity"),left,actionY+2,qx-left,INK);
        g.fill(qx,actionY,qx+w,actionY+h,0xff484c42);
        g.fill(qx+3*w,actionY,qx+4*w,actionY+h,0xff484c42);
        button(g,"−",qx,actionY,w,h,quantity>1,false,mx,my,()->quantity=Math.max(1,quantity-1));
        text(g,Integer.toString(quantity),qx+w+Math.round(10*textScale),actionY+2,w,INK);
        button(g,"+",qx+3*w,actionY,w,h,quantity<64,false,mx,my,()->quantity=Math.min(64,quantity+1));
        text(g,tr("gui.gnfrontier.market.total",price*quantity),qx+5*w,actionY+2,right-qx-5*w,0x96ce8a);
        text(g,tr("gui.gnfrontier.market.unit",price,o.getInt("count")),left,actionY+gap,right-left,0x96ce8a);
        if(!o.getBoolean("progressUnlocked")&&!selling)text(g,tr("message.gnfrontier.steel_required"),left,py(953),right-left,WARN);
        centerButton(g,tr(selling?"gui.gnfrontier.market.switch_buy":"gui.gnfrontier.market.switch_sell"),side,bottom-gap,sw,gap,true,false,mx,my,()->{selling=!selling;quantity=1;});
        boolean ready=o.getBoolean("available")&&o.getBoolean("unlocked")&&(selling||o.getBoolean("progressUnlocked"))&&price>0&&o.getInt(selling?"sellStock":"buyStock")>=quantity;
        centerButton(g,tr(selling?"gui.gnfrontier.market.sell":"gui.gnfrontier.market.buy"),px(1529),py(950),px(1702)-px(1529),Math.max(7,py(983)-py(950)),ready,false,mx,my,()->request(selling?"sell":"buy",o.getString("id"),quantity));
        if(totalRows>visibleRows)text(g,(listScroll+1)+"–"+Math.min(totalRows,listScroll+visibleRows)+" / "+totalRows,side,bottom-2*gap,sw,MUTED);
    }
    private static String offerName(CompoundTag o){return o.contains("displayName")?o.getString("displayName"):ItemStack.of(o.getCompound("stack")).getHoverName().getString();}
    private static String category(CompoundTag o){
        String id=o.getString("id"),item=o.getCompound("stack").getString("id");
        if(id.startsWith("vehicle_")||item.endsWith(":modern_kinetic_gun"))return "weapons";
        if(item.endsWith(":ammo")||id.startsWith("ammo_"))return "ammo";
        if(id.startsWith("permit_"))return "permits";
        if(Set.of("pentium","i3","i5","gpu8800","gpu480","mining_rig").contains(id))return "computing";
        if(Set.of("bandage","antibiotics","mask","medkit","filter").contains(id))return "medical";
        return Set.of("ration","food_supply").contains(id)?"food":"scrap";
    }
    private static void rightText(GuiGraphics g,String label,int x,int y,int width,int color){
        int actual=Math.round(Minecraft.getInstance().font.width(label)*textScale);text(g,label,x-Math.min(width,actual),y,width,color);
    }
    private static void centerButton(GuiGraphics g,String label,int x,int y,int w,int h,boolean enabled,boolean selected,int mx,int my,Runnable action){
        Hit hit=new Hit(x,y,w,h,enabled,action);hits.add(hit);
        if(hit.contains(mx,my))g.fill(x,y,x+w,y+h,0x503f5037);
        int actual=Math.min(w-4,Math.round(Minecraft.getInstance().font.width(label)*textScale));
        text(g,label,x+(w-actual)/2,y+Math.max(0,(h-Math.round(9*textScale))/2),w-4,enabled?(selected?0xffffff:INK):0xb4b9a6);
    }
    private static void tasks(GuiGraphics g,int mx,int my){
        int half=(sideRight-side)/2;
        button(g,tr("gui.gnfrontier.daily_short"),side,top,half-1,rowHeight-2,true,!special,mx,my,()->{special=false;selected=listScroll=textScroll=0;});
        button(g,tr("gui.gnfrontier.special_short"),side+half,top,half,rowHeight-2,true,special,mx,my,()->{special=true;selected=listScroll=textScroll=0;});
        ListTag tasks=snapshot.getList(special?"specialQuests":"tasks",Tag.TAG_COMPOUND);
        selected=clamp(selected,0,Math.max(0,tasks.size()-1));totalRows=tasks.size();visibleRows=Math.max(1,(bottom-top-rowHeight)/rowHeight);
        listScroll=clamp(listScroll,0,Math.max(0,totalRows-visibleRows));
        for(int i=listScroll;i<tasks.size()&&i<listScroll+visibleRows;i++){
            final int index=i;CompoundTag task=tasks.getCompound(i);String label=special?tr(task.getString("title")):task.getString("title");
            button(g,label,side,top+rowHeight+(i-listScroll)*rowHeight,sideRight-side,rowHeight-2,true,i==selected,mx,my,()->{selected=index;textScroll=0;});
        }
        if(tasks.isEmpty())return;
        CompoundTag q=tasks.getCompound(selected);List<Component> paragraphs=new ArrayList<>();Map<Integer,ItemStack> icons=new HashMap<>();
        if(special){
            paragraphs.add(Component.translatable(q.getString("title")));
            paragraphs.add(Component.translatable("gui.gnfrontier.quest.standing",q.getInt("standing"),q.getInt("reputation")));
            for(Tag value:q.getList("items",Tag.TAG_COMPOUND)){
                CompoundTag item=(CompoundTag)value;icons.put(paragraphs.size(),questItem(item));
                int have=q.getBoolean("completed")?item.getInt("count"):item.getInt("have");
                paragraphs.add(Component.translatable("gui.gnfrontier.quest.item",itemName(item.getString("item")),have,item.getInt("count")).withStyle(s->s.withColor(have>=item.getInt("count")?0x96ce8a:WARN)));
            }
            paragraphs.add(Component.translatable("gui.gnfrontier.quest.reward",q.getLong("reward"),q.getInt("reputationReward")));
            paragraphs.add(Component.translatable("gui.gnfrontier.quest."+(q.getBoolean("completed")?"completed":!q.getBoolean("unlocked")?"locked":q.getBoolean("accepted")?"accepted":"available")));
            paragraphs.add(Component.translatable(q.getString("description")));
            for(Tag prior:q.getList("prerequisites",Tag.TAG_STRING))paragraphs.add(Component.translatable("gui.gnfrontier.quest.prerequisite",Component.translatable(prior.getAsString())));
            paragraphs.add(Component.translatable("gui.gnfrontier.quest.permanent"));
        }else{
            paragraphs.add(Component.literal(q.getString("title")));
            paragraphs.add(Component.translatable("gui.gnfrontier.daily.from",merchant(q.getString("merchant")),Component.translatable("gui.gnfrontier.daily.kind."+q.getString("kind").toLowerCase(Locale.ROOT))));
            if(q.getString("kind").equals("DELIVERY"))for(Tag value:q.getList("items",Tag.TAG_COMPOUND)){
                CompoundTag item=(CompoundTag)value;String label=item.getString("label");Component name=label.isBlank()?questItem(item).getHoverName():Component.literal(label);
                icons.put(paragraphs.size(),questItem(item));int have=q.getBoolean("claimed")?item.getInt("count"):item.getInt("have");
                paragraphs.add(Component.translatable("gui.gnfrontier.quest.item",name,have,item.getInt("count")).withStyle(s->s.withColor(have>=item.getInt("count")?0x96ce8a:WARN)));
            }else paragraphs.add(Component.translatable("gui.gnfrontier.daily.progress",q.getBoolean("claimed")?q.getInt("count"):q.getInt("have"),q.getInt("count")));
            paragraphs.add(Component.translatable("gui.gnfrontier.daily.reward",q.getLong("reward"),merchant(q.getString("merchant")),5));
            paragraphs.add(Component.translatable("gui.gnfrontier.daily."+(q.getBoolean("claimed")?"completed":q.getBoolean("accepted")?"accepted":"available")));
            for(String line:q.getString("description").split("\n"))if(!line.isBlank())paragraphs.add(Component.literal(line));
            if(!q.getString("kind").equals("DELIVERY"))paragraphs.add(Component.translatable("gui.gnfrontier.daily.combat_note"));
        }
        ItemStack hovered=paragraphs(g,paragraphs,bottom-2*rowHeight,icons,mx,my);
        boolean accepted=q.getBoolean("accepted"),done=q.getBoolean(special?"completed":"claimed");
        String label=done?"gui.gnfrontier.claimed":!accepted?"gui.gnfrontier.accept":special||q.getString("kind").equals("DELIVERY")?"gui.gnfrontier.quest.deliver":"gui.gnfrontier.claim";
        int index=selected;boolean enabled=!done&&(!special||q.getBoolean("unlocked"))&&(!accepted||q.getBoolean("ready"));
        button(g,tr(label),px(1529),py(950),px(1702)-px(1529),Math.max(6,py(983)-py(950)),enabled,false,mx,my,()->request(special?(accepted?"quest_claim":"quest_accept"):(accepted?"claim":"accept"),q.getString(special?"token":"claimToken"),special?0:index));
        if(!special)text(g,snapshot.getBoolean("daylightCycle")?tr("gui.gnfrontier.task_refresh",(snapshot.getLong("taskTicksRemaining")+1199)/1200):tr("gui.gnfrontier.time_paused"),left,bottom-rowHeight,right-left,MUTED);
        if(!hovered.isEmpty())g.renderTooltip(Minecraft.getInstance().font,hovered,mx,my);
    }
    private static void stats(GuiGraphics g,int mx,int my){
        button(g,tr("gui.gnfrontier.tab.stats"),side,top,sideRight-side,rowHeight-2,true,!guide,mx,my,()->{guide=false;textScroll=0;});
        button(g,tr("gui.gnfrontier.tab.guide"),side,top+rowHeight,sideRight-side,rowHeight-2,true,guide,mx,my,()->{guide=true;textScroll=0;});
        List<Component> lines=new ArrayList<>();
        if(guide){
            for(int i=0;i<8;i++){final int n=i;button(g,Integer.toString(i+1),side+(i%4)*(sideRight-side)/4,top+(2+i/4)*rowHeight,(sideRight-side)/4-1,rowHeight-2,true,i==guidePage,mx,my,()->{guidePage=n;textScroll=0;});}
            lines.add(Component.translatable("gui.gnfrontier.guide."+guidePage));
        }else{
            int[] rep=snapshot.getIntArray("reputation"),alert=snapshot.getIntArray("alert"),trader=snapshot.getIntArray("traderReputation");CompoundTag c=snapshot.getCompound("campaign");
            lines.add(Component.translatable("gui.gnfrontier.balance",snapshot.getLong("balance")));
            if(rep.length==2&&alert.length==2){lines.add(Component.translatable("gui.gnfrontier.east_record",rep[0],alert[0]));lines.add(Component.translatable("gui.gnfrontier.west_record",rep[1],alert[1]));}
            if(trader.length==4)for(int i=0;i<4;i++)lines.add(Component.translatable("gui.gnfrontier.trader_record",merchant(new String[]{"EAST","WEST","NEUTRAL","BLACK"}[i]),trader[i]));
            lines.add(Component.translatable("gui.gnfrontier.connected"));lines.add(Component.translatable("gui.gnfrontier.clearance",snapshot.getInt("militaryRank")));
            lines.add(Component.translatable("gui.gnfrontier.satellite",c.getInt("satellitesLaunched"),tr(snapshot.getBoolean("satelliteOnline")?"gui.gnfrontier.online":"gui.gnfrontier.satellite_waiting")));
            lines.add(Component.translatable("gui.gnfrontier.deterrence",tr(snapshot.getBoolean("deterrenceOnline")?"gui.gnfrontier.online":"gui.gnfrontier.deterrence_waiting")));
            lines.add(Component.translatable("gui.gnfrontier.noah",tr(c.getBoolean("noahComplete")?"gui.gnfrontier.noah_complete":"gui.gnfrontier.noah_waiting")));
        }
        paragraphs(g,lines,bottom);
    }
    private static void paragraphs(GuiGraphics g,List<Component> text,int end){
        paragraphs(g,text,end,Map.of(),-1,-1);
    }
    private record DetailLine(FormattedCharSequence text,ItemStack icon,int inset,int offset) {}
    private static ItemStack questItem(CompoundTag item){
        var type=ForgeRegistries.ITEMS.getValue(new ResourceLocation(item.getString("item")));if(type==null)return ItemStack.EMPTY;
        ItemStack stack=new ItemStack(type);String gun=item.getString("gunId");if(!gun.isEmpty())stack.getOrCreateTag().putString("GunId",gun);return stack;
    }
    private static ItemStack paragraphs(GuiGraphics g,List<Component> text,int end,Map<Integer,ItemStack> icons,int mx,int my){
        var font=Minecraft.getInstance().font;List<DetailLine> lines=new ArrayList<>();
        int step=Math.max(8,Math.round(11*textScale)),iconSize=Math.min(2*step-3,Math.max(10,Math.round(16*textScale)));
        for(int p=0;p<text.size();p++){
            ItemStack stack=icons.getOrDefault(p,ItemStack.EMPTY);int inset=stack.isEmpty()?0:iconSize+Math.max(3,Math.round(6*textScale));
            int offset=stack.isEmpty()?0:Math.max(0,(iconSize-Math.round(9*textScale))/2);boolean first=true;
            for(var line:font.split(text.get(p),Math.max(20,(int)((right-left-8-inset)/textScale)))){
                lines.add(new DetailLine(line,first?stack:ItemStack.EMPTY,inset,offset));first=false;
            }
            lines.add(new DetailLine(FormattedCharSequence.EMPTY,ItemStack.EMPTY,0,0));
        }
        textLines=lines.size();textVisible=Math.max(1,(end-top)/step);textScroll=clamp(textScroll,0,Math.max(0,textLines-textVisible));ItemStack hovered=ItemStack.EMPTY;
        g.enableScissor(left,top,right,end);
        for(int i=0;i<textVisible&&i+textScroll<lines.size();i++){
            DetailLine line=lines.get(i+textScroll);int y=top+i*step;
            if(!line.icon.isEmpty()){
                g.pose().pushPose();g.pose().translate(left,y,0);g.pose().scale(iconSize/16f,iconSize/16f,1);g.renderItem(line.icon,0,0);g.pose().popPose();
                if(mx>=left&&mx<left+iconSize&&my>=y&&my<Math.min(end,y+iconSize))hovered=line.icon;
            }
            g.pose().pushPose();g.pose().translate(left+line.inset,y+line.offset,0);g.pose().scale(textScale,textScale,1);g.drawString(font,line.text,0,0,INK,false);g.pose().popPose();
        }
        g.disableScissor();
        if(textLines>textVisible)text(g,textScroll+textVisible<textLines?"▼":"▲",right-9,end-10,10,MUTED);
        return hovered;
    }
    private static void button(GuiGraphics g,String label,int x,int y,int w,int h,boolean enabled,boolean selected,int mx,int my,Runnable action){
        Hit hit=new Hit(x,y,w,h,enabled,action);hits.add(hit);
        if(selected||hit.contains(mx,my))g.fill(x,y,x+w,y+h,selected?0x805c704c:0x503f5037);
        g.fill(x,y+h-1,x+w,y+h,0x55546a43);
        text(g,label,x+3,y+Math.max(0,(h-Math.round(9*textScale))/2),w-6,enabled?INK:0x78826e);
    }
    private static void text(GuiGraphics g,String value,int x,int y,int width,int color){
        var font=Minecraft.getInstance().font;String clipped=font.plainSubstrByWidth(value,Math.max(0,(int)(width/textScale)));
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(textScale,textScale,1);g.drawString(font,clipped,0,0,color,false);g.pose().popPose();
    }
    private static int px(int value){return bx+Math.round(value*scale);}
    private static int py(int value){return by+Math.round(value*scale);}
    private static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
    private static String tr(String key,Object... args){return Component.translatable(key,args).getString();}
    private static String merchant(String id){return tr("gui.gnfrontier.merchant."+switch(id){case "EAST"->"east";case "WEST"->"west";case "BLACK"->"black";default->"neutral";});}
    private static Component itemName(String id){var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));return item==null?Component.literal(id):new ItemStack(item).getHoverName();}
}
