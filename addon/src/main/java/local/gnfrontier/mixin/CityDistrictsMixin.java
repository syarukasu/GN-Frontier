package local.gnfrontier.mixin;
import local.gnfrontier.CityDistricts;
import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.lost.City;
import mcjty.lostcities.worldgen.lost.cityassets.CityStyle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=City.class,remap=false)
public abstract class CityDistrictsMixin {
    // Resolve before LC2H's disk cache: existing cache entries predate district assignment.
    @Inject(method="getCityStyle",at=@At("HEAD"),cancellable=true)
    private static void gnfrontier$district(ChunkCoord coord,IDimensionInfo info,LostCityProfile profile,CallbackInfoReturnable<CityStyle> cir){var style=CityDistricts.resolve(coord,info,profile);if(style!=null)cir.setReturnValue(style);}
}
