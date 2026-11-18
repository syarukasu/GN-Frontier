package local.gnfrontier.mixin;
import com.tacz.guns.api.item.IGun;
import local.gnfrontier.client.KickClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=HumanoidModel.class,remap=false)
public abstract class HumanoidKickMixin {
    @Inject(method="m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void gn$kick(LivingEntity entity,float limb,float speed,float age,float yaw,float pitch,CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        var model = (HumanoidModel<?>)(Object)this;
        float partial = Mth.clamp(age - entity.tickCount, 0, 1);
        if (player.getPose() == Pose.SWIMMING && !player.isInWater()) {
            float blend = player.getSwimAmount(partial);
            float stroke = Mth.sin(limb * .95f) * Mth.clamp(speed * 2.5f, 0, 1);
            model.leftLeg.xRot = Mth.lerp(blend, model.leftLeg.xRot, .10f + .38f * stroke);
            model.rightLeg.xRot = Mth.lerp(blend, model.rightLeg.xRot, .10f - .38f * stroke);
            if (!(player.getMainHandItem().getItem() instanceof IGun) && !player.isUsingItem()) {
                model.leftArm.xRot = Mth.lerp(blend, model.leftArm.xRot, -2.34f - .34f * stroke);
                model.rightArm.xRot = Mth.lerp(blend, model.rightArm.xRot, -2.34f + .34f * stroke);
                model.leftArm.zRot = Mth.lerp(blend, model.leftArm.zRot, -.25f);
                model.rightArm.zRot = Mth.lerp(blend, model.rightArm.zRot, .25f);
            }
            return;
        }
        float amount = KickClient.strength(player.getId(), partial);
        if (amount > 0) {
            var leg = player.getMainArm() == HumanoidArm.RIGHT ? model.leftLeg : model.rightLeg;
            leg.xRot = Mth.lerp(amount, leg.xRot, -1.6f);
        }
    }
}
