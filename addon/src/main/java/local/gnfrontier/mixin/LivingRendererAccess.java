package local.gnfrontier.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Calls the native renderer's virtual transforms; does not copy third-party renderer code. */
@Mixin(value=LivingEntityRenderer.class,remap=false)
public interface LivingRendererAccess {
    @Invoker("m_7523_") void gnfrontier$rotations(LivingEntity entity,PoseStack poses,float age,float yaw,float partial);
    @Invoker("m_7546_") void gnfrontier$scale(LivingEntity entity,PoseStack poses,float partial);
    @Accessor("f_115291_") java.util.List<RenderLayer<LivingEntity,EntityModel<LivingEntity>>> gnfrontier$layers();
}
