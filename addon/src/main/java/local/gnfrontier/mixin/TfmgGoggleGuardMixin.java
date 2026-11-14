package local.gnfrontier.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run before TFMG 1.0.2f's unchecked BlockHitResult cast, matching Create's native non-block path. */
// HEAD callbacks are prepended: applying after TFMG puts this guard before its handler.
@Mixin(targets="com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer",priority=500,remap=false)
public abstract class TfmgGoggleGuardMixin {
    @Shadow public static int hoverTicks;
    @Shadow public static BlockPos lastHovered;
    @Inject(method="renderOverlay",at=@At("HEAD"),cancellable=true,remap=false)
    private static void gn$guardHit(ForgeGui gui,GuiGraphics graphics,float tick,int width,int height,CallbackInfo ci) {
        if(!(Minecraft.getInstance().hitResult instanceof BlockHitResult)) {lastHovered=null;hoverTicks=0;ci.cancel();}
    }
}
