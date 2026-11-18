package local.gnfrontier.mixin;

import local.gnfrontier.LostCityRuinBounds;
import mcjty.lostcities.worldgen.LostCityTerrainFeature;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LostCityTerrainFeature.class, remap = false)
public abstract class LostCityRuinHeightMixin {
    @Unique private static final ThreadLocal<Integer> gnfrontier$ruinTop = new ThreadLocal<>();

    @Inject(method = "generateRuins", at = @At("HEAD"))
    private void gnfrontier$measureRuin(BuildingInfo info, CallbackInfo ci) {
        // Compute once, outside the 256-column loop. No world or BuildingInfo is retained.
        gnfrontier$ruinTop.set(info.ruinHeight < 0 ? -1 : LostCityRuinBounds.tallPartTop(info));
    }

    @Inject(method = "generateRuins", at = @At("RETURN"))
    private void gnfrontier$finishRuin(BuildingInfo info, CallbackInfo ci) {
        gnfrontier$ruinTop.remove();
    }

    // Native ruin math assumes every part is six blocks high. ChaosZ's radio tower
    // has a 70-block part: the native pass removes its base but leaves the top.
    @Redirect(method = "generateRuins", at = @At(value = "INVOKE",
            target = "Lmcjty/lostcities/worldgen/lost/BuildingInfo;getNumFloors()I"))
    private int gnfrontier$ruinFloors(BuildingInfo info) {
        return gnfrontier$ruinTop.get() >= 0 ? 1 : info.getNumFloors();
    }

    @ModifyConstant(method = "generateRuins", constant = @Constant(floatValue = 6.0F))
    private float gnfrontier$ruinSpan(float nativeHeight, BuildingInfo info) {
        int top = gnfrontier$ruinTop.get();
        return top >= 0 ? Math.max(0, top - info.getCityGroundLevel()) : nativeHeight;
    }

    @Redirect(method = "generateRuins", at = @At(value = "INVOKE",
            target = "Lmcjty/lostcities/worldgen/lost/BuildingInfo;getMaxHeight()I"))
    private int gnfrontier$ruinTop(BuildingInfo info) {
        int top = gnfrontier$ruinTop.get();
        return top >= 0 ? top : info.getMaxHeight();
    }
}
