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
    private static int standingPage;
    private static boolean special, selling;
    private static final String[] STANDING_PAGES = {"EAST", "WEST", "BLACK"};
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
    public static void reset(){snapshot=new CompoundTag();standingPage=0;hits.clear();}
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
    // Design coordinates follow Ep.01 07:53-08:05, before the video's editorial zoom.
    private static void shop(GuiGraphics g,int mx,int my){
        text(g,tr("gui.gnfrontier.market.title",tr("gui.gnfrontier.category."+CATEGORIES[category])),left,top,px(1000)-left,INK);
        rightText(g,tr("gui.gnfrontier.balance",snapshot.getLong("balance")),px(1380),top,px(1380)-px(980),MUTED);
        text(g,tr("gui.gnfrontier.market.categories"),side,py(265),sideRight-side,INK);
        for(int i=0;i<CATEGORIES.length;i++){final int n=i;centerButton(g,tr("gui.gnfrontier.category."+CATEGORIES[i]),side,py(295+i*33),sideRight-side,py(327+i*33)-py(295+i*33),true,category==i,mx,my,()->{category=n;selected=listScroll=0;quantity=1;});}
        List<CompoundTag> offers=new ArrayList<>();
        for(Tag tag:snapshot.getList("offers",Tag.TAG_COMPOUND)){CompoundTag o=(CompoundTag)tag;if(category(o).equals(CATEGORIES[category]))offers.add(o);}
        if(CATEGORIES[category].equals("computing"))offers.sort(Comparator.comparingInt(o->List.of("mining_rig","i3","pentium","i5","gpu8800","gpu480").indexOf(o.getString("id"))));
        selected=clamp(selected,0,Math.max(0,offers.size()-1));visibleRows=6;totalRows=offers.size();
        listScroll=clamp(listScroll,0,Math.max(0,totalRows-visibleRows));ItemStack hovered=ItemStack.EMPTY;
        for(int i=listScroll;i<offers.size()&&i<listScroll+visibleRows;i++){
            final int index=i;CompoundTag o=offers.get(i);int yy=py(300+(i-listScroll)*72),end=py(360+(i-listScroll)*72);
            Hit hit=new Hit(px(205),yy,px(1385)-px(205),end-yy,true,()->{selected=index;quantity=1;});hits.add(hit);
            if(i==selected||hit.contains(mx,my))g.fill(hit.x,yy,hit.x+hit.w,end,i==selected?0xff30332c:0xff292d25);
            ItemStack stack=ItemStack.of(o.getCompound("stack"));
            var vehicle=local.gnfrontier.VehicleMarket.CATALOG.get(o.getString("id"));
            String picture=vehicle==null?null:"superbwarfare:textures/vehicle_icon/container/"+new ResourceLocation(vehicle.entity()).getPath()+".png";
            if(icon(g,stack,248,312+(i-listScroll)*72,36,mx,my,picture))hovered=stack;
            text(g,offerName(o),px(342),py(318+(i-listScroll)*72),px(1220)-px(342),INK);
            long price=o.getLong(selling?"sell":"buy");rightText(g,price>0?"$"+price:tr(selling?"gui.gnfrontier.market.no_buyback":"gui.gnfrontier.sell_only"),px(1346),py(318+(i-listScroll)*72),px(1346)-px(1225),0x96ce8a);
        }
        if(offers.isEmpty()){text(g,tr("gui.gnfrontier.market.no_stock"),left,py(320),right-left,MUTED);return;}
        CompoundTag o=offers.get(selected);long price=o.getLong(selling?"sell":"buy");
        text(g,offerName(o),left,py(750),right-left,INK);
        String key="gui.gnfrontier.market.description."+o.getString("id");
        String description=net.minecraft.locale.Language.getInstance().has(key)?tr(key):o.getString("id").equals("mining_rig")?tr("gui.gnfrontier.market.mining_desc"):tr("gui.gnfrontier.market.supplier",merchant(o.getString("merchant")),o.getInt(selling?"sellStock":"buyStock"));
        text(g,description,left,py(790),right-left,INK);
        text(g,tr("gui.gnfrontier.market.quantity"),left,py(825),px(280)-left,MUTED);
        g.fill(px(285),py(820),px(333),py(858),0xff484c42);g.fill(px(427),py(820),px(475),py(858),0xff484c42);
        centerButton(g,"−",px(285),py(820),px(333)-px(285),py(858)-py(820),quantity>1,false,mx,my,()->quantity=Math.max(1,quantity-1));
        centerButton(g,Integer.toString(quantity),px(340),py(820),px(418)-px(340),py(858)-py(820),false,false,mx,my,()->{});
        centerButton(g,"+",px(427),py(820),px(475)-px(427),py(858)-py(820),quantity<64,false,mx,my,()->quantity=Math.min(64,quantity+1));
        text(g,tr("gui.gnfrontier.market.total",price*quantity),px(502),py(825),right-px(502),0x96ce8a);
        text(g,tr("gui.gnfrontier.market.unit",price,o.getInt("count")),left,py(865),right-left,0x96ce8a);
        if(!o.getBoolean("progressUnlocked")&&!selling)text(g,tr("message.gnfrontier.steel_required"),left,py(953),right-left,WARN);
        centerButton(g,tr(selling?"gui.gnfrontier.market.switch_buy":"gui.gnfrontier.market.switch_sell"),side,py(884),sideRight-side,py(918)-py(884),true,false,mx,my,()->{selling=!selling;quantity=1;});
        boolean ready=o.getBoolean("available")&&o.getBoolean("unlocked")&&(selling||o.getBoolean("progressUnlocked"))&&price>0&&o.getInt(selling?"sellStock":"buyStock")>=quantity;
        centerButton(g,tr(selling?"gui.gnfrontier.market.sell":"gui.gnfrontier.market.buy"),px(1529),py(950),px(1702)-px(1529),py(983)-py(950),ready,false,mx,my,()->request(selling?"sell":"buy",o.getString("id"),quantity));
        if(totalRows>visibleRows)text(g,(listScroll+1)+"–"+Math.min(totalRows,listScroll+visibleRows)+" / "+totalRows,side,py(842),sideRight-side,MUTED);
        if(!hovered.isEmpty())g.renderTooltip(Minecraft.getInstance().font,hovered,mx,my);
    }
    private static String offerName(CompoundTag o){
        String key="gui.gnfrontier.market.name."+o.getString("id");
        String label=net.minecraft.locale.Language.getInstance().has(key)?tr(key):o.contains("displayName")?o.getString("displayName"):ItemStack.of(o.getCompound("stack")).getHoverName().getString();
        return label+(o.getInt("count")>1?"×"+o.getInt("count"):"");
    }
    private static String category(CompoundTag o){
        String id=o.getString("id"),item=o.getCompound("stack").getString("id");
        if(Set.of("market_meat","market_fruit","market_milk","market_energy","market_wine","ration","food_supply").contains(id))return "food";
        if(id.startsWith("vehicle_")||id.startsWith("market_")||item.endsWith(":modern_kinetic_gun"))return "weapons";
        if(item.endsWith(":ammo")||id.startsWith("ammo_"))return "ammo";
        if(id.startsWith("permit_"))return "permits";
        if(Set.of("pentium","i3","i5","gpu8800","gpu480","mining_rig").contains(id))return "computing";
        return Set.of("bandage","antibiotics","mask","medkit","filter").contains(id)?"medical":"scrap";
    }
    private static void rightText(GuiGraphics g,String label,int x,int y,int width,int color){
        int actual=Math.round(Minecraft.getInstance().font.width(label)*textScale);text(g,label,x-Math.min(width,actual),y,width,color);
    }
    private static void centerButton(GuiGraphics g,String label,int x,int y,int w,int h,boolean enabled,boolean selected,int mx,int my,Runnable action){
        Hit hit=new Hit(x,y,w,h,enabled,action);hits.add(hit);
        if(hit.contains(mx,my))g.fill(x,y,x+w,y+h,0x503f5037);
        int actual=Math.min(w-4,Math.round(Minecraft.getInstance().font.width(label)*textScale));
        text(g,label,x+(w-actual)/2,y+Math.max(0,(h-Math.round(9*textScale))/2),w-4,selected?0xffffff:enabled?INK:0xb4b9a6);
    }
    private static boolean icon(GuiGraphics g,ItemStack stack,int x,int y,int size,int mx,int my){
        return icon(g,stack,x,y,size,mx,my,null);
    }
    private static boolean icon(GuiGraphics g,ItemStack stack,int x,int y,int size,int mx,int my,String picture){
        int xx=px(x),yy=py(y),w=Math.max(1,Math.round(size*scale));
        g.pose().pushPose();g.pose().translate(xx,yy,0);g.pose().scale(w/16f,w/16f,1);
        if(picture==null)g.renderItem(stack,0,0);else g.blit(new ResourceLocation(picture),0,0,0,0,16,16,16,16);
        g.pose().popPose();
        return mx>=xx&&mx<xx+w&&my>=yy&&my<yy+w;
    }
    private static void taskChoice(GuiGraphics g,String label,int x,int y,int w,int h,boolean selected,int mx,int my,Runnable action){
        Hit hit=new Hit(x,y,w,h,true,action);hits.add(hit);int r=Math.max(1,Math.round(9*scale)),c=selected?0xffa5a58a:0xff67675e;
        if(hit.contains(mx,my))g.fill(x+r,y+1,x+w-r,y+h-1,0x403f5037);
        g.fill(x+r,y,x+w-r,y+1,c);g.fill(x+r,y+h-1,x+w-r,y+h,c);g.fill(x,y+r,x+1,y+h-r,c);g.fill(x+w-1,y+r,x+w,y+h-r,c);
        for(int a=0;a<r;a++){g.fill(x+r-a-1,y+a,x+r-a,y+a+1,c);g.fill(x+w-r+a,y+a,x+w-r+a+1,y+a+1,c);g.fill(x+r-a-1,y+h-a-1,x+r-a,y+h-a,c);g.fill(x+w-r+a,y+h-a-1,x+w-r+a+1,y+h-a,c);}
        int actual=Math.min(w-6,Math.round(Minecraft.getInstance().font.width(label)*textScale));
        text(g,label,x+(w-actual)/2,y+Math.max(0,(h-Math.round(9*textScale))/2),w-6,INK);
    }
    private static void tasks(GuiGraphics g,int mx,int my){
        text(g,tr("gui.gnfrontier.task.list"),side,py(265),sideRight-side,INK);
        int half=(sideRight-side)/2;
        centerButton(g,tr("gui.gnfrontier.daily_short"),side,py(884),half,py(918)-py(884),true,!special,mx,my,()->{special=false;selected=listScroll=textScroll=0;});
        centerButton(g,tr("gui.gnfrontier.special_short"),side+half,py(884),half,py(918)-py(884),true,special,mx,my,()->{special=true;selected=listScroll=textScroll=0;});
        ListTag tasks=snapshot.getList(special?"specialQuests":"tasks",Tag.TAG_COMPOUND);
        selected=clamp(selected,0,Math.max(0,tasks.size()-1));totalRows=tasks.size();visibleRows=16;
        listScroll=clamp(listScroll,0,Math.max(0,totalRows-visibleRows));
        for(int i=listScroll;i<tasks.size()&&i<listScroll+visibleRows;i++){
            final int index=i;CompoundTag task=tasks.getCompound(i);String label=special?tr(task.getString("title")):task.getString("title");
            taskChoice(g,label,side+Math.round(4*scale),py(300+(i-listScroll)*34),sideRight-side-Math.round(8*scale),Math.max(1,Math.round(28*scale)),i==selected,mx,my,()->{selected=index;textScroll=0;});
        }
        if(tasks.isEmpty()){text(g,tr("gui.gnfrontier.daily.empty"),left,top,right-left,MUTED);return;}
        CompoundTag q=tasks.getCompound(selected);boolean accepted=q.getBoolean("accepted"),done=q.getBoolean(special?"completed":"claimed");
        text(g,special?tr(q.getString("title")):q.getString("title"),left,top,right-left,INK);
        String description=special?tr(q.getString("description")):q.getString("description");
        if(special)for(Tag prior:q.getList("prerequisites",Tag.TAG_STRING))description+="\n"+tr("gui.gnfrontier.quest.prerequisite",tr(prior.getAsString()));
        var font=Minecraft.getInstance().font;var lines=font.split(Component.literal(description),Math.max(1,(int)((right-left)/textScale)));
        int step=Math.max(1,Math.round(29*scale));textLines=lines.size();textVisible=3;textScroll=clamp(textScroll,0,Math.max(0,textLines-textVisible));
        g.enableScissor(left,py(355),right,py(441));
        for(int i=0;i<textVisible&&i+textScroll<lines.size();i++){g.pose().pushPose();g.pose().translate(left,py(355)+i*step,0);g.pose().scale(textScale,textScale,1);g.drawString(font,lines.get(i+textScroll),0,0,INK,false);g.pose().popPose();}
        g.disableScissor();if(textLines>textVisible)text(g,"↕",right-Math.round(24*scale),py(420),Math.round(24*scale),MUTED);
        text(g,tr("gui.gnfrontier.task.reward",q.getLong("reward")),left,py(455),right-left,0x96ce8a);
        String state=done?"completed":special&&!q.getBoolean("unlocked")?"locked":accepted?"accepted":"available";
        text(g,tr("gui.gnfrontier.task.state",tr("gui.gnfrontier.task.state."+state)),left,py(555),right-left,INK);
        long seconds=Math.max(0,(snapshot.getLong("taskTicksRemaining")-updateTick)/20);
        text(g,special?tr("gui.gnfrontier.task.permanent"):snapshot.getBoolean("daylightCycle")?tr("gui.gnfrontier.task.limit",String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60)):tr("gui.gnfrontier.time_paused"),left,py(605),right-left,WARN);
        ItemStack hovered=ItemStack.EMPTY;ListTag items=q.getList("items",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(4,items.size());i++){
            CompoundTag item=items.getCompound(i);ItemStack stack=questItem(item);int y=665+i*46;
            if(icon(g,stack,200,y,32,mx,my))hovered=stack;
            String name=item.getString("label");if(name.isBlank())name=stack.getHoverName().getString();
            int have=done?item.getInt("count"):item.getInt("have");
            text(g,tr("gui.gnfrontier.quest.item",name,have,item.getInt("count")),px(248),py(y+5),right-px(248),have>=item.getInt("count")?0x96ce8a:INK);
        }
        if(!special&&!q.getString("kind").equals("DELIVERY"))text(g,tr("gui.gnfrontier.daily.progress",done?q.getInt("count"):q.getInt("have"),q.getInt("count")),left,py(670),right-left,MUTED);
        if(special&&!q.getBoolean("unlocked"))text(g,tr("gui.gnfrontier.quest.standing",q.getInt("standing"),q.getInt("reputation")),left,py(850),right-left,WARN);
        text(g,tr("gui.gnfrontier.task.hint."+(done?"completed":accepted?"accepted":"available")),left,py(890),right-left,INK);
        String label=done?"gui.gnfrontier.claimed":!accepted?"gui.gnfrontier.accept":special||q.getString("kind").equals("DELIVERY")?"gui.gnfrontier.quest.deliver":"gui.gnfrontier.claim";
        int index=selected;boolean enabled=!done&&(!special||q.getBoolean("unlocked"))&&(!accepted||q.getBoolean("ready"));
        centerButton(g,tr(label),px(1529),py(950),px(1702)-px(1529),py(983)-py(950),enabled,false,mx,my,()->request(special?(accepted?"quest_claim":"quest_accept"):(accepted?"claim":"accept"),q.getString(special?"token":"claimToken"),special?0:index));
        if(!hovered.isEmpty())g.renderTooltip(Minecraft.getInstance().font,hovered,mx,my);
    }
    private static void stats(GuiGraphics g,int mx,int my){
        for (int i = 0; i <= STANDING_PAGES.length; i++) {
            int page = i;
            String name = i == STANDING_PAGES.length ? tr("gui.gnfrontier.tab.guide") : merchant(STANDING_PAGES[i]);
            button(g,name,side,top+i*rowHeight,sideRight-side,rowHeight-2,true,standingPage==i,mx,my,()->{standingPage=page;textScroll=0;});
        }
        List<Component> lines=new ArrayList<>();
        if(standingPage == STANDING_PAGES.length){
            for(int i=0;i<8;i++){final int n=i;button(g,Integer.toString(i+1),side+(i%4)*(sideRight-side)/4,top+(4+i/4)*rowHeight,(sideRight-side)/4-1,rowHeight-2,true,i==guidePage,mx,my,()->{guidePage=n;textScroll=0;});}
            lines.add(Component.translatable("gui.gnfrontier.guide."+guidePage));
        } else if (standingPage < 2) {
            int[] rep = snapshot.getIntArray("reputation"), alert = snapshot.getIntArray("alert");
            int[] tiers = snapshot.getIntArray("reinforcementTiers");
            if (rep.length == 2 && alert.length == 2 && tiers.length == 2) {
                String sideName = standingPage == 0 ? "east" : "west";
                String relation = snapshot.getString(sideName + "Relation").toLowerCase(Locale.ROOT);
                lines.add(Component.translatable("gui.gnfrontier." + sideName + "_record", rep[standingPage], alert[standingPage]));
                lines.add(Component.translatable("gui.gnfrontier.faction.relation", Component.translatable("gui.gnfrontier.faction." + relation)));
                lines.add(Component.translatable("gui.gnfrontier.faction.force", Component.translatable("gui.gnfrontier.faction.force." + tiers[standingPage])));
            }
        } else {
            int[] trader = snapshot.getIntArray("traderReputation");
            if (trader.length == 4) {
                lines.add(Component.translatable("gui.gnfrontier.trader_record", merchant("BLACK"), trader[3]));
                long offers = snapshot.getList("offers", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
                        .filter(o -> o.getString("merchant").equals("BLACK") && o.getBoolean("unlocked") && o.getBoolean("available") && o.getBoolean("progressUnlocked")).count();
                long contracts = snapshot.getList("specialQuests", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
                        .filter(q -> q.getBoolean("unlocked") && !q.getBoolean("completed")).count();
                lines.add(Component.translatable("gui.gnfrontier.faction.eligible_offers", offers));
                lines.add(Component.translatable("gui.gnfrontier.faction.eligible_contracts", contracts));
            }
            CompoundTag c = snapshot.getCompound("campaign");
            lines.add(Component.translatable("gui.gnfrontier.balance",snapshot.getLong("balance")));
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
