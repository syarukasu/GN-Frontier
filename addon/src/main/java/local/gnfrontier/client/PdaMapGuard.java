package local.gnfrontier.client;

import local.gnfrontier.Frontier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The map provider stays available to the PDA's embedded map. */
@Mod.EventBusSubscriber(modid=Frontier.ID,value=Dist.CLIENT)
public final class PdaMapGuard {
    public static boolean isMap(Screen screen){
        for(Class<?> type=screen==null?null:screen.getClass();type!=null;type=type.getSuperclass())if(type.getName().equals("xaero.map.gui.GuiMap"))return true;
        return false;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void opening(ScreenEvent.Opening event) {
        if (isMap(event.getNewScreen())) event.setCanceled(true);
    }
}
