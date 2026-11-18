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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
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
        if (age < 0 || age >= WindowKick.RECOVERY_TICKS) return 0;
        if (age < WindowKick.IMPACT_TICK) return ease(age / WindowKick.IMPACT_TICK);
        return age < 5 ? 1 : ease((WindowKick.RECOVERY_TICKS - age) / (WindowKick.RECOVERY_TICKS - 5));
    }
    private static float ease(float t) { return t * t * (3 - 2 * t); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null)return;
        long now=mc.level.getGameTime();starts.entrySet().removeIf(x->now-x.getValue()>12);
        while(KEY.consumeClick())if(mc.screen==null&&!mc.player.isPassenger()&&!mc.player.isVisuallySwimming()&&!net.minecraft.client.gui.screens.Screen.hasControlDown()&&!net.minecraft.client.gui.screens.Screen.hasAltDown() && (now<lastRequest || now-lastRequest>=14)) {
            lastRequest=now;FrontierNetwork.CHANNEL.sendToServer(new FrontierNetwork.KickRequest());
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){starts.clear();lastRequest=Long.MIN_VALUE/2;AtmosphereOverrides.resetClient();PdaScreen.reset();}
    @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true) public static void hand(RenderHandEvent e) {
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;float f=strength(mc.player.getId(),e.getPartialTick());
        if(f<=0 || e.getHand()!=InteractionHand.MAIN_HAND || !mc.options.getCameraType().isFirstPerson())return;
        if(legModel==null)legModel=new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
        boolean left = mc.player.getMainArm() == HumanoidArm.RIGHT;
        var leg = left ? legModel.leftLeg : legModel.rightLeg;
        var pants = left ? legModel.leftPants : legModel.rightPants;
        PoseStack pose=e.getPoseStack();pose.pushPose();
        pose.translate((left ? -1 : 1) * Mth.lerp(f, .25f, .07f), -.79 + .42 * f, -.65 - .16 * f);
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(f, 22, 126)));
        pose.mulPose(Axis.ZP.rotationDegrees((left ? -1 : 1) * 7 * f));
        pose.scale(1,-1,1);
        leg.setPos(0,0,0);leg.xRot=0;leg.yRot=0;leg.zRot=0;
        pants.copyFrom(leg);
        var vertices=e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(mc.player.getSkinTextureLocation()));
        leg.render(pose,vertices,e.getPackedLight(),OverlayTexture.NO_OVERLAY);
        if(mc.player.isModelPartShown(left ? PlayerModelPart.LEFT_PANTS_LEG : PlayerModelPart.RIGHT_PANTS_LEG))
            pants.render(pose,vertices,e.getPackedLight(),OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
