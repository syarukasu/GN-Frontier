package local.gnfrontier.mixin;

import local.gnfrontier.NpcInventory;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge's built-in six equipment slots otherwise hide an attached backpack from TaCZ reloads. */
@Mixin(LivingEntity.class)
public abstract class NpcInventoryCapabilityMixin {
    @Inject(method="getCapability",at=@At("HEAD"),cancellable=true,remap=false)
    private <T> void gn$equipmentAndBackpack(Capability<T> cap,Direction side,CallbackInfoReturnable<LazyOptional<T>> cir){
        if(cap!=ForgeCapabilities.ITEM_HANDLER)return;
        var own=((LivingEntity)(Object)this).getCapability(NpcInventory.CAPABILITY,side);
        if(own.isPresent())cir.setReturnValue(own.cast());
    }
}
