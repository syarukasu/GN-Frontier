package local.gnfrontier.mixin;
import local.gnfrontier.client.KickClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=HumanoidModel.class,remap=false)
public abstract class HumanoidKickMixin {
    @Inject(method="m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void gn$kick(LivingEntity entity,float limb,float speed,float age,float yaw,float pitch,CallbackInfo ci) {
        if(!(entity instanceof Player))return;float f=KickClient.strength(entity.getId(),age-entity.tickCount);
        if(f>0)((HumanoidModel<?>)(Object)this).rightLeg.xRot=-1.7f*f;
    }
}
