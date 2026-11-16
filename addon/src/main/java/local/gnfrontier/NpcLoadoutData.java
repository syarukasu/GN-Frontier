package local.gnfrontier;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** One data source for issued gear and physical supplies, independent of death loot. */
@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class NpcLoadoutData extends SimpleJsonResourceReloadListener {
    public record Weapon(String id,int weight,int loaded,int reserve,String mode,String scope){}
    public record Army(String helmet,String vest,List<Weapon> weapons){}
    public record Supply(String id,int count){}
    public record Catalog(Army east,Army west,List<Supply> supplies,List<Supply> medic,int supportMagazines,String carriedLoot,int emptyTicks){}
    public static volatile Catalog catalog;
    public NpcLoadoutData(){super(new Gson(),"npc_loadouts");}
    @SubscribeEvent public static void reload(AddReloadListenerEvent e){e.addListener(new NpcLoadoutData());}
    private static String item(JsonObject object,String key){
        String id=object.get(key).getAsString();
        if(!ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id)))throw new JsonParseException("Unknown NPC item: "+id);
        return id;
    }
    private static int bounded(JsonObject o,String key,int min,int max){int n=o.get(key).getAsInt();if(n<min||n>max)throw new JsonParseException("NPC "+key+" out of range");return n;}
    private static Army army(JsonObject o){
        List<Weapon> weapons=new ArrayList<>();
        for(var element:o.getAsJsonArray("weapons")){
            var w=element.getAsJsonObject();String id=w.get("id").getAsString();new ResourceLocation(id);
            String mode=w.get("mode").getAsString();com.tacz.guns.api.item.gun.FireMode.valueOf(mode);
            weapons.add(new Weapon(id,bounded(w,"weight",1,100),bounded(w,"loaded",1,200),bounded(w,"reserve",0,512),mode,w.has("scope")?w.get("scope").getAsString():""));
        }
        if(weapons.isEmpty())throw new JsonParseException("Empty NPC weapon pool");
        return new Army(item(o,"helmet"),item(o,"vest"),List.copyOf(weapons));
    }
    private static List<Supply> supplies(JsonArray a){List<Supply> result=new ArrayList<>();for(var e:a){var o=e.getAsJsonObject();result.add(new Supply(item(o,"id"),bounded(o,"count",1,64)));}return List.copyOf(result);}
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler){
        var source=files.get(Frontier.id("catalog"));if(source==null)throw new JsonParseException("Missing GN NPC loadout catalog");
        var o=source.getAsJsonObject();if(o.get("schema").getAsInt()!=1)throw new JsonParseException("Unsupported NPC loadout schema");
        String loot=o.get("carried_loot_table").getAsString();new ResourceLocation(loot);
        catalog=new Catalog(army(o.getAsJsonObject("east")),army(o.getAsJsonObject("west")),supplies(o.getAsJsonArray("supplies")),supplies(o.getAsJsonArray("medic_supplies")),bounded(o,"support_reserve_magazines",0,8),loot,bounded(o,"empty_corpse_ticks",20,24000));
    }
}
