package local.gnfrontier.mixin;
import local.gnfrontier.FrontierWorldPolicy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=MinecraftServer.class,remap=false)
public abstract class FrontierDifficultyMixin {
    @ModifyVariable(method="m_129827_",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Difficulty gn$hard(Difficulty requested) {
        return FrontierWorldPolicy.locked((MinecraftServer)(Object)this)?Difficulty.HARD:requested;
    }
}
