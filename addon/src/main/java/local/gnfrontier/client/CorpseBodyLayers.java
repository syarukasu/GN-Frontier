package local.gnfrontier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import local.gnfrontier.FallenSoldier;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.LivingEntity;

/** Native military skins bake a helmet/vest into the head and torso. Replace those surfaces
 * only when their real equipment slot is empty. Native animation, limbs and camouflage remain.
 * Resources are resolved from the installed mods; no third-party texture is copied or edited. */
final class CorpseBodyLayers {
    private static final ModelPart BARE_HEAD=head();
    private static final ModelPart SHIRT=shirt();
    private static ModelPart head(){
        var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-8,-4,8,8,8),PartPose.ZERO);
        return LayerDefinition.create(mesh,64,64).bakeRoot().getChild("head");
    }
    private static ModelPart shirt(){
        var mesh=new MeshDefinition();var cubes=CubeListBuilder.create();
        // Reuse the uniform's sleeve cloth at its native pixel density, without the painted vest.
        for(int x=-4;x<4;x+=4)for(int y=0;y<12;y+=4)cubes.texOffs(40,16).addBox(x,y,-2,4,4,4);
        mesh.getRoot().addOrReplaceChild("body",cubes,PartPose.ZERO);return LayerDefinition.create(mesh,64,64).bakeRoot().getChild("body");
    }
    @SuppressWarnings("unchecked")
    static boolean render(FallenSoldier corpse,LivingEntity body,EntityRenderer<? super LivingEntity> renderer,PoseStack poses,MultiBufferSource buffers,int light){
        if(!(renderer instanceof LivingEntityRenderer<?,?> living)||!(living.getModel() instanceof HierarchicalModel<?> nativeModel))return false;
        String name=nativeModel.getClass().getName();
        if(!name.endsWith(".RUunitModel")&&!name.endsWith(".USunitModel"))return false;
        var model=(HierarchicalModel<LivingEntity>)nativeModel;var root=model.root();if(!root.hasChild("unit"))return false;
        ModelPart unit=root.getChild("unit"),head=unit.getChild("head"),torso=unit.getChild("body");
        boolean helmet=corpse.visibleEquipment(5).isEmpty(),vest=corpse.visibleEquipment(4).isEmpty();
        if(!helmet&&!vest)return false;
        boolean oldHead=head.skipDraw,oldBody=torso.skipDraw;
        poses.pushPose();
        try{
            var nativeTransforms=(local.gnfrontier.mixin.LivingRendererAccess)living;
            nativeTransforms.gnfrontier$rotations(body,poses,20,0,0);
            poses.scale(-1,-1,1);nativeTransforms.gnfrontier$scale(body,poses,0);poses.translate(0,-1.501,0);
            model.prepareMobModel(body,0,0,0);model.setupAnim(body,0,0,20,0,0);
            head.skipDraw=oldHead||helmet;torso.skipDraw=oldBody||vest;
            var original=buffers.getBuffer(RenderType.entityCutoutNoCull(renderer.getTextureLocation(body)));
            model.renderToBuffer(poses,original,light,OverlayTexture.NO_OVERLAY,1,1,1,1);
            // Removing armor must not hide a gun or other equipment that is still on the body.
            for(var layer:nativeTransforms.gnfrontier$layers())layer.render(poses,buffers,light,body,0,0,0,20,0,0);
            root.translateAndRotate(poses);unit.translateAndRotate(poses);
            if(vest){poses.pushPose();torso.translateAndRotate(poses);SHIRT.render(poses,original,light,OverlayTexture.NO_OVERLAY);poses.popPose();}
            if(helmet){poses.pushPose();head.translateAndRotate(poses);BARE_HEAD.render(poses,buffers.getBuffer(RenderType.entityCutoutNoCull(DefaultPlayerSkin.getDefaultSkin(corpse.getUUID()))),light,OverlayTexture.NO_OVERLAY);poses.popPose();}
        }finally{head.skipDraw=oldHead;torso.skipDraw=oldBody;poses.popPose();}
        return true;
    }
}
