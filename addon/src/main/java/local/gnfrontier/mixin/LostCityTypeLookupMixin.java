package local.gnfrontier.mixin;

import local.gnfrontier.BlockEntityTypeLookup;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="mcjty.lostcities.worldgen.LostCityTerrainFeature",remap=false)
public abstract class LostCityTypeLookupMixin {
    @Inject(method="getTypeForBlock",at=@At("HEAD"),cancellable=true)
    private void gnfrontier$registeredType(BlockState state,CallbackInfoReturnable<BlockEntityType<?>> cir){cir.setReturnValue(BlockEntityTypeLookup.resolve(state));}
    @Redirect(method="handleBlockEntity",at=@At(value="INVOKE",target="Lorg/apache/logging/log4j/Logger;warn(Ljava/lang/String;)V"))
    private void gnfrontier$warnOnce(Logger logger,String message){if(BlockEntityTypeLookup.firstWarning(message))logger.warn(message);}
}
