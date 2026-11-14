package local.gnfrontier.client;
import local.gnfrontier.Frontier;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** One diagnostic line after the actual title render, not an offline or FPS claim. */
@Mod.EventBusSubscriber(modid=Frontier.ID,value=Dist.CLIENT)
public final class ClientReadiness {
    private static boolean reported;
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post e){
        if(!reported && e.getScreen() instanceof TitleScreen && Minecraft.getInstance().getOverlay()==null){
            reported=true;LogUtils.getLogger().info("GN_CLIENT_TITLE_RENDERED version={} worldOpened=false",net.minecraftforge.fml.ModList.get().getModContainerById(Frontier.ID).orElseThrow().getModInfo().getVersion());
        }
    }
}
