package local.gnfrontier.mixin;

import local.gnfrontier.NpcEquipment;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="net.nekoyuni.SimpleEnemyMod.entity.unit.AbstractUnit",remap=false)
public abstract class NpcFreshEquipmentMixin {
    @Inject(method="m_6518_",at=@At("RETURN"))
    private void gn$fresh(CallbackInfoReturnable<?> cir){NpcEquipment.markFresh((Mob)(Object)this);}
}
