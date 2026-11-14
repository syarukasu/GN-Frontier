package local.gnfrontier.client;

import java.util.*;
import local.gnfrontier.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=Frontier.ID,value=Dist.CLIENT)
public final class SurvivalVisuals {
    private static final Map<Integer,FrontierNetwork.Condition> states=new HashMap<>();
    public static void receive(FrontierNetwork.Condition c){if(!c.collapsed()&&c.debt()==0)states.remove(c.entityId());else states.put(c.entityId(),c);}
    public static boolean down(int id){return states.containsKey(id)&&states.get(id).collapsed();}
    public static boolean localDown(){var p=Minecraft.getInstance().player;return p!=null&&down(p.getId());}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){states.clear();}
    @SubscribeEvent public static void input(MovementInputUpdateEvent e){if(down(e.getEntity().getId())){var input=e.getInput();input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;input.shiftKeyDown=false;}}
    @SubscribeEvent public static void interact(InputEvent.InteractionKeyMappingTriggered e){if(localDown()){e.setCanceled(true);e.setSwingHand(false);}}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){if(localDown()){e.setRoll(85);e.setPitch(Math.min(85,e.getPitch()+20));}}
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post e){if(!localDown())return;var mc=Minecraft.getInstance();var g=e.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        g.fill(0,0,w,h,0xe8000000);g.drawCenteredString(mc.font,net.minecraft.network.chat.Component.translatable("gui.gnfrontier.unconscious"),w/2,h/2-12,0xc8c8c8);
        var c=states.get(mc.player.getId());g.drawCenteredString(mc.font,net.minecraft.network.chat.Component.translatable("gui.gnfrontier.breathing_recovery", c.recovery()),w/2,h/2+8,0x889988);}
}
