package local.gnfrontier.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** HP/RIP bullet scaling must not reintroduce vanilla armor through Expert's pre-hit hook. */
@Mixin(targets="com.ori.taczplus.event.GameEvents",remap=false)
public abstract class ExpertBulletArmorMixin {
    @Redirect(method="onPreHurtByGun",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;m_21230_()I"))
    private static int gn$ballisticArmorOnly(LivingEntity entity){return 0;}
}
