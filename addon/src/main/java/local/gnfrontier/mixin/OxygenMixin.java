package local.gnfrontier.mixin;
import local.gnfrontier.AtmosphereOverrides;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="earth.terrarium.adastra.common.systems.OxygenApiImpl",remap=false)
public abstract class OxygenMixin {
    @Inject(method="hasOxygen(Lnet/minecraft/resources/ResourceKey;)Z",at=@At("HEAD"),cancellable=true)
    private void gn$planet(ResourceKey<Level> dimension,CallbackInfoReturnable<Boolean> cir){if(AtmosphereOverrides.complete(dimension))cir.setReturnValue(true);}
    @Inject(method="hasOxygen(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",at=@At("HEAD"),cancellable=true)
    private void gn$position(Level level,net.minecraft.core.BlockPos pos,CallbackInfoReturnable<Boolean> cir){if(AtmosphereOverrides.complete(level.dimension()))cir.setReturnValue(true);}
}
