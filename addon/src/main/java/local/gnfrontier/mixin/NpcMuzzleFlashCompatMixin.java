package local.gnfrontier.mixin;

import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TaCZ 1.2 particle flashes may have no legacy texture. SEM's quad renderer requires one. */
@Mixin(targets="net.nekoyuni.SimpleEnemyMod.entity.client.NpcMuzzleFlashRenderer",remap=false)
public abstract class NpcMuzzleFlashCompatMixin {
    @Inject(method="doRender",at=@At("HEAD"),cancellable=true,remap=false)
    private static void gn$guardLegacyFlash(MuzzleFlash flash,@Coerce Object data,long elapsed,CallbackInfo ci) {
        if(flash==null||flash.getTexture()==null)ci.cancel();
    }
}
