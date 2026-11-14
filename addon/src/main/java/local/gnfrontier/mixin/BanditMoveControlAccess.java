package local.gnfrontier.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=MoveControl.class,remap=false)
public interface BanditMoveControlAccess {
    @Accessor("f_24974_") Mob gn$owner();
}
