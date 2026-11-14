package local.gnfrontier.client;

import local.gnfrontier.Frontier;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Frontier.ID, value = Dist.CLIENT)
public final class CampChatTradeButton {
    private static KeyMapping pdaKey;
    @SubscribeEvent public static void init(ScreenEvent.Init.Post event) {
        if (PdaScreen.isNative(event.getScreen())) PdaScreen.opened();
    }
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (!PdaScreen.isNative(event.getScreen())) return;
        if (pdaKey == null) try {
            pdaKey=(KeyMapping)Class.forName("com.kltyton.campchat.client.input.CampChatKeyMappings").getField("OPEN_CHAT").get(null);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Installed PDA key unavailable",e); }
        if (pdaKey.matches(event.getKeyCode(),event.getScanCode())) {
            event.getScreen().onClose();
            event.setCanceled(true);
        }
    }
}
