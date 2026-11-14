package local.gnfrontier;

import com.google.gson.*;
import java.util.*;
import local.gnfrontier.core.MerchantQuests;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Data packs may override data/gnfrontier/merchant_quests/black_market.json. */
@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class MerchantQuestData extends SimpleJsonResourceReloadListener {
    private static volatile List<MerchantQuests.Quest> catalog=List.of();
    public MerchantQuestData(){super(new Gson(),"merchant_quests");}
    public static List<MerchantQuests.Quest> catalog(){return catalog;}
    @SubscribeEvent public static void reload(AddReloadListenerEvent event){event.addListener(new MerchantQuestData());}
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager resources,ProfilerFiller profiler) {
        JsonElement source=files.get(Frontier.id("black_market"));
        if(source==null)throw new JsonParseException("Missing GN black-market quest catalog");
        List<MerchantQuests.Quest> next=MerchantQuests.parse(source.getAsJsonObject());
        for(var quest:next)for(var item:quest.items())if(!TradeService.exists(item.item()))throw new JsonParseException("Missing quest item: "+item.item());
        catalog=next;
    }
}
