package local.gnfrontier.mixin;

import local.gnfrontier.NpcEquipment;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="net.nekoyuni.SimpleEnemyMod.event.common.UnitDropsHandler",remap=false)
public abstract class NpcNativeDropsMixin {
    @Inject(method="onUnitDrops",at=@At("HEAD"),cancellable=true)
    private static void gn$actualPossessions(LivingDropsEvent event,CallbackInfo ci){if(NpcEquipment.managed(event.getEntity()))ci.cancel();}
}
