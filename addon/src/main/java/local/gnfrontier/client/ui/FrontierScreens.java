package local.gnfrontier.client.ui;

import local.gnfrontier.Frontier;
import local.gnfrontier.mixin.LevelLoadingAccess;
import local.gnfrontier.mixin.ProgressScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=Frontier.ID,value=Dist.CLIENT)
public final class FrontierScreens {
    @SubscribeEvent public static void opening(ScreenEvent.Opening e) {
        if(e.getNewScreen()!=null&&e.getNewScreen().getClass()==TitleScreen.class)e.setNewScreen(new FrontierTitleScreen());
        if(e.getNewScreen() instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen create)
            e.setNewScreen(new FrontierCreateWorldScreen(create));
    }
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post e) {
        Screen screen=e.getScreen();if(Minecraft.getInstance().screen!=screen)return;
        Component stage;float progress=-1;
        if(screen instanceof LevelLoadingScreen) {
            stage=Component.translatable("gui.gnfrontier.loading.world");
            progress=((LevelLoadingAccess)screen).gn$progressListener().getProgress()/100f;
        } else if(screen instanceof ProgressScreen) {
            var access=(ProgressScreenAccess)screen;stage=access.gn$stage();
            if(stage==null||stage.getString().isBlank())stage=access.gn$header();
            if(stage==null)stage=Component.translatable("gui.gnfrontier.loading.world");
            progress=access.gn$progress()/100f;
        } else if(screen instanceof ReceivingLevelScreen)stage=Component.translatable("gui.gnfrontier.loading.joining");
        else if(screen instanceof GenericDirtMessageScreen)stage=screen.getTitle();
        else if(screen.getClass().getName().equals("com.eruannie_9.extragore.particle.render.BloodLoadingScreen"))stage=Component.translatable("gui.gnfrontier.loading.effects");
        else return;
        FrontierTheme.fontsReady=true;
        var graphics=e.getGuiGraphics();
        // Finish the native screen first; its deferred text must stay behind our background.
        graphics.flush();graphics.pose().pushPose();graphics.pose().translate(0,0,400);
        try {FrontierTheme.loading(graphics,screen.width,screen.height,stage,progress,true);graphics.flush();}
        finally {graphics.pose().popPose();}
    }
}
