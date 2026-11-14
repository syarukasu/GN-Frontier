package local.gnfrontier.mixin;
import local.gnfrontier.LegacyPulleyNbt;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(targets="com.simibubi.create.content.contraptions.pulley.PulleyBlockEntity",remap=false)
public abstract class LegacyPulleyNbtMixin {
    @ModifyVariable(method="read",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false)
    private CompoundTag gn$legacyEncoding(CompoundTag tag){return LegacyPulleyNbt.repair(tag);}
}
