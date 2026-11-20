package local.gnfrontier.mixin;

import java.nio.file.Path;
import local.gnfrontier.client.ShaderPresetAlias;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public abstract class OculusShaderPresetMixin {
    @Shadow
    public static Path getShaderpacksDirectory() {
        throw new AssertionError();
    }

    @Inject(method = "loadExternalShaderpack", at = @At("HEAD"))
    private static void gnfrontier$preparePreset(String name, CallbackInfoReturnable<Boolean> info) {
        ShaderPresetAlias.prepare(getShaderpacksDirectory(), name);
    }
}
