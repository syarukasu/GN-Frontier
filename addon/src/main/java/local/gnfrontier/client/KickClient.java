package local.gnfrontier.client;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import local.gnfrontier.*;
import net.minecraft.client.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid=Frontier.ID,value=Dist.CLIENT)
public final class KickClient {
    private static final KeyMapping KEY=new KeyMapping("key.gnfrontier.kick",KeyConflictContext.IN_GAME,com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_V),"key.categories.gnfrontier");
    private static final Map<Integer,Long> starts=new HashMap<>();
    private static long lastRequest=Long.MIN_VALUE/2;
    private static PlayerModel<LivingEntity> legModel;
    @Mod.EventBusSubscriber(modid=Frontier.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){e.register(KEY);}
    }
    public static void animate(int entityId){Minecraft mc=Minecraft.getInstance();if(mc.level!=null)starts.put(entityId,mc.level.getGameTime());}
    public static float strength(int id,float partial) {
        Minecraft mc=Minecraft.getInstance();if(mc.level==null || !starts.containsKey(id))return 0;
        float age=mc.level.getGameTime()-starts.get(id)+partial;
        return age<0 || age>=10?0:(float)Math.sin(Math.PI*age/10);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null)return;
        long now=mc.level.getGameTime();starts.entrySet().removeIf(x->now-x.getValue()>12);
        while(KEY.consumeClick())if(mc.screen==null && (now<lastRequest || now-lastRequest>=14)) {
            lastRequest=now;FrontierNetwork.CHANNEL.sendToServer(new FrontierNetwork.KickRequest());
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){starts.clear();lastRequest=Long.MIN_VALUE/2;AtmosphereOverrides.resetClient();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hand(RenderHandEvent e) {
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;float f=strength(mc.player.getId(),e.getPartialTick());
        if(f<=0 || !mc.options.getCameraType().isFirstPerson())return;
        e.setCanceled(true);if(e.getHand()!=InteractionHand.MAIN_HAND)return;
        if(legModel==null)legModel=new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
        PoseStack pose=e.getPoseStack();pose.pushPose();
        pose.translate(.26,-.72+.22*f,-.55-.42*f);pose.mulPose(Axis.XP.rotationDegrees(85*f));
        pose.scale(1,-1,-1);
        legModel.rightLeg.setPos(0,0,0);legModel.rightLeg.xRot=0;legModel.rightLeg.yRot=0;legModel.rightLeg.zRot=0;
        legModel.rightPants.copyFrom(legModel.rightLeg);
        var vertices=e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(mc.player.getSkinTextureLocation()));
        legModel.rightLeg.render(pose,vertices,e.getPackedLight(),OverlayTexture.NO_OVERLAY);
        legModel.rightPants.render(pose,vertices,e.getPackedLight(),OverlayTexture.NO_OVERLAY);pose.popPose();
    }
}
