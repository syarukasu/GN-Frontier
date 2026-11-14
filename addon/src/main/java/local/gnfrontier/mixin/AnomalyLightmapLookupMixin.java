package local.gnfrontier.mixin;
import net.minecraft.client.renderer.LightTexture;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.lang.reflect.Field;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Select the three vanilla fields by Forge-mapped names, not by field-count heuristics. */
@Mixin(targets="com.anomalyzone.EmissionLightmap",remap=false)
public abstract class AnomalyLightmapLookupMixin {
    @Redirect(method="ensureLookup",at=@At(value="INVOKE",target="Ljava/lang/Class;getDeclaredFields()[Ljava/lang/reflect/Field;"),remap=false,require=1)
    private static Field[] gn$nativeLightmapFields(Class<?> owner) {
        if(owner!=LightTexture.class)return owner.getDeclaredFields();
        return new Field[]{
            ObfuscationReflectionHelper.findField(LightTexture.class,"f_109870_"),
            ObfuscationReflectionHelper.findField(LightTexture.class,"f_109871_"),
            ObfuscationReflectionHelper.findField(LightTexture.class,"f_109873_")};
    }
}
