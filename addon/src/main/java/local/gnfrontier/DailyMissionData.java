package local.gnfrontier;

import com.google.gson.*;
import java.util.*;
import local.gnfrontier.core.DailyMissions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** KubeJS/data packs own all daily wording, target choices, quantities and prices. */
@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class DailyMissionData extends SimpleJsonResourceReloadListener {
    public DailyMissionData(){super(new Gson(),"daily_missions");}
    @SubscribeEvent public static void reload(AddReloadListenerEvent event){event.addListener(new DailyMissionData());}
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager resources,ProfilerFiller profiler) {
        JsonElement source=files.get(Frontier.id("catalog"));if(source==null)throw new JsonParseException("Missing GN daily mission catalog");
        var next=DailyMissions.parse(source.getAsJsonObject());
        for(var template:next.templates())for(var target:template.targets()) {
            if(!target.item().isEmpty()&&!TradeService.exists(target.item()))throw new JsonParseException("Unknown daily item: "+target.item());
            for(String entity:target.entities())if(!ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(entity)))throw new JsonParseException("Unknown daily entity: "+entity);
        }
        if(next.deliveries()!=null)for(var target:next.deliveries().items().values())
            if(!TradeService.exists(target.item()))throw new JsonParseException("Unknown modular delivery item: "+target.item());
        DailyMissions.install(next);
    }
}
