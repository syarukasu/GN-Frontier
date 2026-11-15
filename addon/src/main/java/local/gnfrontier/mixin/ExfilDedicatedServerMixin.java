package local.gnfrontier.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Marker for the constructor correction in FrontierMixinPlugin. */
@Pseudo
@Mixin(targets = "com.example.examplemod.EvacuationMod", remap = false)
public abstract class ExfilDedicatedServerMixin {}
