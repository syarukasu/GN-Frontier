package local.gnfrontier.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import local.gnfrontier.Frontier;
import local.gnfrontier.ScavengerHook;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Uses Minecraft's bobber texture; no external image asset or desktop input. */
public final class ScavengerHookRenderer extends EntityRenderer<ScavengerHook> {
    private static final ResourceLocation TEXTURE=new ResourceLocation("minecraft","textures/entity/fishing_hook.png");
    public ScavengerHookRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(ScavengerHook hook){return TEXTURE;}
    @Override public void render(ScavengerHook hook,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        poses.pushPose();poses.scale(.4f,.4f,.4f);poses.mulPose(entityRenderDispatcher.cameraOrientation());poses.mulPose(Axis.YP.rotationDegrees(180));
        var pose=poses.last();VertexConsumer quads=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        corner(quads,pose,-.5f,-.5f,0,1,light);corner(quads,pose,.5f,-.5f,1,1,light);
        corner(quads,pose,.5f,.5f,1,0,light);corner(quads,pose,-.5f,.5f,0,0,light);poses.popPose();
        Entity owner=hook.visibleOwner();
        if(owner!=null){
            Vec3 point=new Vec3(Mth.lerp(partial,owner.xo,owner.getX()),Mth.lerp(partial,owner.yo,owner.getY())+owner.getEyeHeight()-.5,Mth.lerp(partial,owner.zo,owner.getZ()));
            Vec3 origin=new Vec3(Mth.lerp(partial,hook.xo,hook.getX()),Mth.lerp(partial,hook.yo,hook.getY()),Mth.lerp(partial,hook.zo,hook.getZ()));
            Vec3 delta=point.subtract(origin);VertexConsumer line=buffers.getBuffer(RenderType.lineStrip());
            for(int i=0;i<=16;i++){
                float t=i/16f;Vec3 p=new Vec3(delta.x*t,delta.y*t-.35*Math.sin(Math.PI*t),delta.z*t);
                line.vertex(poses.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(35,32,29,255).normal(poses.last().normal(),0,1,0).endVertex();
            }
        }
        super.render(hook,yaw,partial,poses,buffers,light);
    }
    private static void corner(VertexConsumer out,PoseStack.Pose pose,float x,float y,float u,float v,int light){
        out.vertex(pose.pose(),x,y,0).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(),0,1,0).endVertex();
    }
    @Mod.EventBusSubscriber(modid=Frontier.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(Frontier.SCAVENGER_HOOK.get(),ScavengerHookRenderer::new);}
    }
}
