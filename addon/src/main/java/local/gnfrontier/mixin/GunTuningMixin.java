package local.gnfrontier.mixin;
import local.gnfrontier.GunTuning;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.GunIndexPOJO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=CommonGunIndex.class,remap=false)
public abstract class GunTuningMixin {
    @Inject(method="getInstance",at=@At("RETURN"))
    private static void gn$tune(GunIndexPOJO pojo,CallbackInfoReturnable<CommonGunIndex> cir){GunTuning.apply(pojo,cir.getReturnValue());}
}
