package local.gnfrontier.mixin;

import local.gnfrontier.TaczSidearmCompat;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets={"com.neoalive.tacz_sewv.entity.ai.support.UnitHolster",
    "com.neoalive.tacz_sewv.entity.unit.PmcCommanderEntity"},remap=false)
public abstract class SupportSidearmCompatMixin {
    @ModifyArg(method="buildSidearm",at=@At(value="INVOKE",target="Lcom/tacz/guns/api/item/builder/GunItemBuilder;setId(Lnet/minecraft/resources/ResourceLocation;)Lcom/tacz/guns/api/item/builder/GunItemBuilder;"),index=0)
    private static ResourceLocation gnfrontier$existingPistol(ResourceLocation id){return TaczSidearmCompat.resolve(id);}
}
