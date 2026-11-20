package local.gnfrontier.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "com.ori.taczplus.client.pip.ScopePip", remap = false)
public interface ExpertScopeAccess {
    @Invoker("shaderLensReady") static boolean gnfrontier$hasShaderLens() { throw new AssertionError(); }
    @Accessor("nightVision") static boolean gnfrontier$nightVision() { throw new AssertionError(); }
    @Accessor("thermal") static boolean gnfrontier$thermal() { throw new AssertionError(); }
    @Accessor("ai") static boolean gnfrontier$ai() { throw new AssertionError(); }
}
