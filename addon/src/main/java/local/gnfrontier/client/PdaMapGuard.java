package local.gnfrontier.client;

import local.gnfrontier.Frontier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A different map hotkey must not bypass PDA interference; Escape still closes a map already open. */
@Mod.EventBusSubscriber(modid=Frontier.ID,value=Dist.CLIENT)
public final class PdaMapGuard {
    public static boolean isMap(Screen screen){
        for(Class<?> type=screen==null?null:screen.getClass();type!=null;type=type.getSuperclass())if(type.getName().equals("xaero.map.gui.GuiMap"))return true;
        return false;
    }
    private static boolean blocked(Screen screen){return isMap(screen)&&PdaScreen.signalLost();}
    @SubscribeEvent public static void opening(ScreenEvent.Opening e){
        if(blocked(e.getNewScreen())){e.setCanceled(true);var player=Minecraft.getInstance().player;if(player!=null)player.displayClientMessage(Component.translatable("gui.gnfrontier.signal_lost"),true);}
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Pre e){
        if(!blocked(e.getScreen()))return;
        e.setCanceled(true);var screen=e.getScreen();var g=e.getGuiGraphics();g.fill(0,0,screen.width,screen.height,0xff20231e);
        g.drawCenteredString(Minecraft.getInstance().font,Component.translatable("gui.gnfrontier.signal_lost"),screen.width/2,screen.height/2,0xe3bf7e);
    }
    @SubscribeEvent public static void click(ScreenEvent.MouseButtonPressed.Pre e){if(blocked(e.getScreen()))e.setCanceled(true);}
    @SubscribeEvent public static void drag(ScreenEvent.MouseDragged.Pre e){if(blocked(e.getScreen()))e.setCanceled(true);}
    @SubscribeEvent public static void scroll(ScreenEvent.MouseScrolled.Pre e){if(blocked(e.getScreen()))e.setCanceled(true);}
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre e){if(e.getKeyCode()!=256&&blocked(e.getScreen()))e.setCanceled(true);}
}
