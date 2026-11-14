package local.gnreview;

import java.util.*;
import java.lang.reflect.*;
import local.gnfrontier.*;
import local.gnfrontier.client.PdaScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Screen APIs only, on the never-activated review desktop. No OS keyboard or pointer input. */
public final class PdaChecks {
    private static Object field(Object o,String name)throws Exception{Class<?> c=o instanceof Class<?>?(Class<?>)o:o.getClass();Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o instanceof Class<?>?null:o);}
    private static int number(String name)throws Exception{return ((Number)field(PdaScreen.class,name)).intValue();}
    public static Map<String,Object> client(String action,int value)throws Exception{
        Minecraft mc=Minecraft.getInstance();
        if(mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().matches("GN_Review_r(23_factions|24_expert|28_beds|31_deliveries)"))throw new IllegalStateException("Owned world required");
        KeyMapping key=(KeyMapping)Class.forName("com.kltyton.campchat.client.input.CampChatKeyMappings").getField("OPEN_CHAT").get(null);
        if(action.equals("open-key")){mc.setScreen(null);KeyMapping.click(key.getKey());}
        else if(action.equals("close-key")){
            if(!PdaScreen.isNative(mc.screen))throw new IllegalStateException("Native PDA required");
            var event=new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,key.getKey().getValue(),0,0);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
            if(!event.isCanceled()||mc.screen!=null)throw new AssertionError("PDA hotkey did not close screen");
        }
        else if(action.equals("inventory"))mc.setScreen(new InventoryScreen(mc.player));
        else if(action.equals("reload")){Class.forName("com.anomalyzone.settings.HudSettings").getMethod("load").invoke(null);mc.reloadResourcePacks();}
        else if(action.equals("close"))mc.setScreen(null);
        else if(action.equals("native-map")){if(!PdaScreen.isNative(mc.screen))throw new IllegalStateException("PDA required");mc.setScreen((Screen)field(mc.screen,"embeddedMap"));}
        else if(action.equals("tab")){
            if(!PdaScreen.isNative(mc.screen)||value<0||value>3)throw new IllegalStateException("PDA tab required");
            Screen s=mc.screen;float scale=((Number)field(s,"layoutScale")).floatValue();int bx=(Integer)field(s,"bgX"),by=(Integer)field(s,"bgY");
            s.mouseClicked(bx+(235+160*value+60)*scale,by+216*scale,0);
        }else if(action.equals("category")){
            if(!PdaScreen.isNative(mc.screen)||value<0||value>6)throw new IllegalStateException("Shop required");
            float text=((Number)field(PdaScreen.class,"textScale")).floatValue();int header=Math.max(10,Math.round(16*text)),gap=Math.max(9,Math.round(14*text));
            mc.screen.mouseClicked((number("side")+number("sideRight"))/2.,number("top")+header+(value+.5)*gap,0);
        }else if(action.equals("plus")||action.equals("buy")||action.equals("special")||action.equals("daily")||action.equals("accept")){
            if(!PdaScreen.isNative(mc.screen))throw new IllegalStateException("Native PDA required");
            int row=number("rowHeight"),left=number("left"),right=number("right"),bottom=number("bottom"),side=number("side"),sr=number("sideRight");float text=((Number)field(PdaScreen.class,"textScale")).floatValue();
            double x,y;
            if(action.equals("plus")){int w=Math.max(16,Math.round(20*text)),gap=Math.max(10,Math.round(16*text));x=left+Math.round(34*text)+3.5*w;y=bottom-Math.round(60*text)+2.5*gap;}
            else if(action.equals("buy")){float s=((Number)field(mc.screen,"layoutScale")).floatValue();x=(Integer)field(mc.screen,"bgX")+1615*s;y=(Integer)field(mc.screen,"bgY")+966*s;}
            else if(action.equals("special")||action.equals("daily")){x=side+(sr-side)*(action.equals("daily")?.25:.75);y=number("top")+row/2.;}
            else {float s=((Number)field(mc.screen,"layoutScale")).floatValue();x=(Integer)field(mc.screen,"bgX")+1625*s;y=(Integer)field(mc.screen,"bgY")+965*s;}
            mc.screen.mouseClicked(x,y,0);
        }else if(action.equals("task-scroll")){
            if(!PdaScreen.isNative(mc.screen)||!PdaScreen.tab(mc.screen).equals("TASKS"))throw new IllegalStateException("Daily PDA page required");
            mc.screen.mouseScrolled((number("left")+number("right"))/2.,number("top")+20,value);
        }else if(action.equals("map-zoom")){
            if(!PdaScreen.isNative(mc.screen)||!PdaScreen.tab(mc.screen).equals("MAP"))throw new IllegalStateException("Native map required");
            mc.screen.mouseScrolled(mc.screen.width/2.,mc.screen.height/2.,value);
        }else if(!action.equals("state")&&!action.equals("locale")&&!action.equals("locale-sweep"))throw new IllegalArgumentException(action);
        Map<String,Object> out=new LinkedHashMap<>();out.put("action",action);out.put("screen",mc.screen==null?"null":mc.screen.getClass().getName());out.put("pdaItemPresent",TradeService.hasPda(mc.player));
        if(action.equals("locale-sweep")){
            var path=java.nio.file.Path.of(System.getProperty("gnfrontier.reviewAuditKeys","runtime-audit-keys.json"));
            var rows=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonArray();
            List<Map<String,String>> entries=new ArrayList<>();
            for(var entry:rows){var row=entry.getAsJsonObject();String keyName=row.get("key").getAsString();entries.add(Map.of("namespace",row.get("namespace").getAsString(),"key",keyName,"actual",net.minecraft.locale.Language.getInstance().getOrDefault(keyName)));}
            out.put("localeEntries",entries);
        }
        if(action.equals("locale")){
            Map<String,String> expected=Map.of("config.tacz_presence.cat.fire_blur","発砲ぼかし","gui.gnfrontier.category.computing","演算装置","item.gnfrontier.cpu_i3_6100","Intel Core i3-6100","anomalyzone.stats_panel.title","総合能力");
            Map<String,String> actual=new LinkedHashMap<>();
            for(var entry:expected.entrySet()){
                String text=net.minecraft.network.chat.Component.translatable(entry.getKey()).getString();actual.put(entry.getKey(),text);
                if(!entry.getValue().equals(text))throw new AssertionError(entry.getKey()+": "+text);
            }
            String prompt=local.gnfrontier.client.ScanText.localize("Start chunk scanning?");
            if(!prompt.equals("地形情報の走査を開始しますか？"))throw new AssertionError(prompt);
            out.put("translations",actual);out.put("scanPrompt",prompt);out.put("jtransLoaded",net.minecraftforge.fml.ModList.get().isLoaded("jtrans"));
        }
        out.put("key",key.getKey().getName());out.put("keyConflicts",Arrays.stream(mc.options.keyMappings).filter(k->k!=key&&k.getKey().equals(key.getKey())).map(KeyMapping::getName).toList());
        out.put("unusedLetters",java.util.stream.IntStream.rangeClosed(65,90).filter(c->Arrays.stream(mc.options.keyMappings).noneMatch(k->k.getKey().getValue()==c)).mapToObj(c->Character.toString(c)).toList());
        Map<String,Object> candidates=new LinkedHashMap<>();for(int code:new int[]{260,268,269,344,92})candidates.put(Integer.toString(code),Arrays.stream(mc.options.keyMappings).filter(k->k.getKey().getValue()==code).map(KeyMapping::getName).toList());out.put("candidateKeys",candidates);
        out.put("position",mc.player.position().toString());out.put("dimension",mc.level.dimension().location().toString());
        Object session=Class.forName("xaero.map.WorldMapSession").getMethod("getCurrentSession").invoke(null);
        if(session!=null){Object processor=session.getClass().getMethod("getMapProcessor").invoke(session);out.put("mapCaveLayer",processor.getClass().getMethod("getCurrentCaveLayer").invoke(processor));out.put("mapBrightness",processor.getClass().getMethod("getBrightness").invoke(processor));}
        Object setting=Class.forName("com.anomalyzone.settings.HudSettings").getField("INV_STATS_SHOW").get(null);out.put("inventoryStatPanel",setting.getClass().getMethod("getBool").invoke(setting));
        if(PdaScreen.isNative(mc.screen)){
            out.put("tab",PdaScreen.tab(mc.screen));out.put("embeddedMap",field(mc.screen,"embeddedMap")!=null);out.put("snapshot",field(PdaScreen.class,"snapshot").toString());
            out.put("quantity",number("quantity"));out.put("special",field(PdaScreen.class,"special"));out.put("width",mc.screen.width);out.put("height",mc.screen.height);
            out.put("textScroll",number("textScroll"));out.put("textLines",number("textLines"));out.put("textVisible",number("textVisible"));
        }
        return out;
    }
}
