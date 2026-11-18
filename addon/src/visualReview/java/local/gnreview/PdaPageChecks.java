package local.gnreview;

import java.util.*;
import java.lang.reflect.*;
import local.gnfrontier.*;
import local.gnfrontier.client.PdaScreen;
import local.gnfrontier.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.registries.ForgeRegistries;

/** UI calls and real packets only; restricted to the existing registered review world. */
public final class PdaPageChecks {
    private static final String BACKUP="gn_pda_page_review";
    private static void owned(ServerPlayer p){if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r39_creation"))throw new IllegalStateException("Owned world required");}
    private static Object field(Object o,String name)throws Exception{Class<?> c=o instanceof Class<?>?(Class<?>)o:o.getClass();Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o instanceof Class<?>?null:o);}
    private static ItemStack stack(String id,int count){return new ItemStack(TradeService.item(id),count);}
    private static Map<String,Object> item(ItemStack s){return Map.of("id",ForgeRegistries.ITEMS.getKey(s.getItem()).toString(),"count",s.getCount(),"nbt",String.valueOf(s.getTag()),"name",s.getHoverName().getString());}
    public static Map<String,Object> server(ServerPlayer p,String action,Properties settings){
        owned(p);var w=WorldState.get(p.serverLevel());var id=p.getUUID();
        if(action.equals("prepare")){
            if(p.getPersistentData().contains(BACKUP))throw new IllegalStateException("Restore previous fixture first");
            var n=new CompoundTag();n.put("account",WorldState.encode(w.account(id)));n.put("campaign",w.campaign(id).save());n.put("inventory",p.getInventory().save(new ListTag()));
            n.putLong("sun",p.serverLevel().getDayTime());n.putBoolean("daylight",p.serverLevel().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT));
            var stock=new CompoundTag();w.usedStock.forEach(stock::putInt);n.put("usedStock",stock);n.putLong("stockDay",w.stockDay);n.put("dailyCycle",w.dailyCycle.save());
            n.putBoolean("invulnerable",p.isInvulnerable());n.putBoolean("gravity",p.isNoGravity());n.putDouble("x",p.getX());n.putDouble("y",p.getY());n.putDouble("z",p.getZ());
            n.putInt("selected",p.getInventory().selected);n.putFloat("yaw",p.getYRot());n.putFloat("pitch",p.getXRot());
            p.getPersistentData().put(BACKUP,n);p.setInvulnerable(true);p.setNoGravity(true);p.teleportTo(-282.5,73,-6.5);p.setYRot(180);p.setXRot(0);
            p.getInventory().clearContent();p.getInventory().add(stack("anomalyzone:control_board",8));
            p.getInventory().add(stack("create:precision_mechanism",2));p.getInventory().add(stack("survival_instinct:bandage",3));
            p.getInventory().selected=4;p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(4));
            p.serverLevel().setDayTime(6000);p.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true,p.getServer());
            var account=w.account(id);account.balance=10000;Arrays.fill(account.alert,0);Arrays.fill(account.traderReputation,100);
            var c=new Campaign.State();c.day=w.dailyCycle.day();c.dailyInitialized=true;c.nextInterference=p.serverLevel().getGameTime()+1000000;
            var junk=new Campaign.Mission("review_junk","ジャンク屋",Campaign.Kind.DELIVERY,"anomalyzone:control_board","",8,300,Economy.Merchant.BLACK,"旧式の制御基板を「8枚」回してくれ。",List.of(),"review_junk","");
            var goods=List.of(new Campaign.DeliveryItem("create:precision_mechanism","","精密機構","個",2),new Campaign.DeliveryItem("survival_instinct:bandage","","消毒済みの包帯","個",3),new Campaign.DeliveryItem("gnfrontier:cpu_i3_6100","","Intel Core i3-6100","個",1));
            var mixed=new Campaign.Mission("review_mixed","電子機器と医療品の補給",Campaign.Kind.DELIVERY,goods.get(0).item(),"",goods.stream().mapToInt(Campaign.DeliveryItem::count).sum(),1200,Economy.Merchant.BLACK,"工房へ回す精密機構と演算部品、それから負傷者の手当てに使う包帯をまとめて届けてくれ。",List.of(),"review_mixed","",goods);
            var ship=new Campaign.Mission("review_ship","海の藻屑",Campaign.Kind.SHIP,"","",1,1500,Economy.Merchant.BLACK,"「1隻」だけでもいい、海で船舶を見かけたら沈めてくれ。",List.of(),"review_ship","");
            c.dailyMissions=List.of(junk,mixed,ship);
            for(var mission:c.dailyMissions){c.issuedMissions.add(mission.id());c.issuedAppearances.add(mission.appearance());c.issuedContents.add(mission.contentKey());}
            c.validate();w.campaigns.put(id,c);((net.minecraft.world.level.saveddata.SavedData)w).save(new CompoundTag());((net.minecraft.world.level.saveddata.SavedData)w).setDirty();p.getInventory().setChanged();p.containerMenu.broadcastChanges();FrontierNetwork.send(p,"");
            return Map.of("fixture",true,"board",List.of("one-item delivery","three-item delivery","ship"),"balance",account.balance);
        }
        if(action.equals("restore")){
            var n=p.getPersistentData().getCompound(BACKUP);if(!n.contains("account"))throw new IllegalStateException("No fixture backup");
            w.players.put(id,WorldState.decode(n.getCompound("account")));w.campaigns.put(id,Campaign.State.load(n.getCompound("campaign")));
            w.usedStock.clear();var stock=n.getCompound("usedStock");stock.getAllKeys().forEach(key->w.usedStock.put(key,stock.getInt(key)));w.stockDay=n.getLong("stockDay");w.dailyCycle=DailyCycle.load(n.getCompound("dailyCycle"));
            p.getInventory().clearContent();p.getInventory().load(n.getList("inventory",Tag.TAG_COMPOUND));
            p.getInventory().selected=n.getInt("selected");p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(p.getInventory().selected));p.setYRot(n.getFloat("yaw"));p.setXRot(n.getFloat("pitch"));
            p.setInvulnerable(n.getBoolean("invulnerable"));p.setNoGravity(n.getBoolean("gravity"));p.teleportTo(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"));
            p.serverLevel().setDayTime(n.getLong("sun"));p.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(n.getBoolean("daylight"),p.getServer());
            if(!n.getCompound("account").equals(WorldState.encode(w.account(id)))||!n.getList("inventory",Tag.TAG_COMPOUND).equals(p.getInventory().save(new ListTag())))throw new IllegalStateException("Review restoration differs");
            p.getPersistentData().remove(BACKUP);((net.minecraft.world.level.saveddata.SavedData)w).setDirty();p.getInventory().setChanged();p.containerMenu.broadcastChanges();return Map.of("restored",true,"inventoryAndAccountExact",true);
        }
        if(action.equals("inspect")){
            var items=p.getInventory().items.stream().filter(s->!s.isEmpty()).map(PdaPageChecks::item).toList();
            return Map.of("balance",w.account(id).balance,"accepted",w.campaign(id).accepted,"claimed",w.campaign(id).claimed,"items",items,"backupPresent",p.getPersistentData().contains(BACKUP));
        }
        if(action.equals("generated")){
            if(!p.getPersistentData().contains(BACKUP))throw new IllegalStateException("Fixture backup required");
            var c=new Campaign.State();Campaign.refreshForDay(c,id,w.dailyCycle.day());c.nextInterference=p.serverLevel().getGameTime()+1000000;
            w.campaigns.put(id,c);((net.minecraft.world.level.saveddata.SavedData)w).setDirty();FrontierNetwork.send(p,"");
            return Map.of("generatedByProductionCatalog",true,"missions",c.dailyMissions.stream().map(m->Map.of("title",m.title(),"kind",m.kind().name(),"requirements",m.requirements().stream().map(r->Map.of("item",r.item(),"count",r.count())).toList())).toList());
        }
        throw new IllegalArgumentException(action);
    }
    public static Map<String,Object> client(String action,Properties settings)throws Exception{
        var mc=Minecraft.getInstance();if(mc.player==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals("GN_Review_r39_creation"))throw new IllegalStateException("Owned client required");
        if(action.equals("reload")){mc.reloadResourcePacks();return Map.of("resourceReloadRequested",true);}
        if(action.equals("scale")){int scale=Integer.parseInt(settings.getProperty("scale"));if(scale<2||scale>4)throw new IllegalArgumentException();mc.options.guiScale().set(scale);mc.resizeDisplay();}
        if(action.equals("open")){
            mc.options.hideGui=false;mc.setScreen((Screen)Class.forName("com.kltyton.campchat.client.gui.CampChatScreen").getConstructor().newInstance());PdaScreen.opened();
        }
        if(!PdaScreen.isNative(mc.screen))throw new IllegalStateException("PDA screen required");
        var screen=mc.screen;int bx=(Integer)field(screen,"bgX"),by=(Integer)field(screen,"bgY");float scale=((Number)field(screen,"layoutScale")).floatValue();
        java.util.function.BiConsumer<Integer,Integer> click=(x,y)->screen.mouseClicked(bx+x*scale,by+y*scale,0);
        switch(action){
            case "shop"->click.accept(470,216);
            case "tasks"->click.accept(630,216);
            case "stats"->click.accept(790,216);
            case "category"->click.accept(1615,311+Integer.parseInt(settings.getProperty("pdaPage"))*33);
            case "row"->click.accept(800,330+Integer.parseInt(settings.getProperty("pdaPage"))*72);
            case "task"->click.accept(1600,314+Integer.parseInt(settings.getProperty("pdaPage"))*34);
            case "plus"->click.accept(451,838);
            case "minus"->click.accept(309,838);
            case "submit"->click.accept(1615,966);
            case "special"->click.accept(1680,901);
            case "daily"->click.accept(1535,901);
            case "scroll"->screen.mouseScrolled(bx+700*scale,by+390*scale,Double.parseDouble(settings.getProperty("step","-1")));
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("action",action);out.put("screen",screen.getClass().getName());out.put("tab",PdaScreen.tab(screen));
        out.put("frame",List.of(bx,by,1920*scale,1200*scale));out.put("size",List.of(screen.width,screen.height));
        if(bx<0||by<0||bx+1920*scale>screen.width+1||by+1200*scale>screen.height+1)throw new AssertionError("PDA frame clipped");
        for(String name:List.of("quantity","selected","category","textScroll","textLines","textVisible","special"))out.put(name,field(PdaScreen.class,name));
        var n=(CompoundTag)field(PdaScreen.class,"snapshot");out.put("balance",n.getLong("balance"));out.put("tasks",n.getList("tasks",Tag.TAG_COMPOUND).toString());
        List<Map<String,Object>> goods=new ArrayList<>();
        for(Tag value:n.getList("offers",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)value;ItemStack stack=ItemStack.of(row.getCompound("stack"));
            if(stack.isEmpty())throw new AssertionError("Missing native market item "+row.getString("id"));
            if(mc.getItemRenderer().getModel(stack,mc.level,mc.player,0)==mc.getModelManager().getMissingModel())throw new AssertionError("Missing item model "+row.getString("id"));
            goods.add(Map.of("offer",row.getString("id"),"stack",item(stack),"buy",row.getLong("buy"),"count",row.getInt("count")));
        }
        out.put("goods",goods);return out;
    }
}
