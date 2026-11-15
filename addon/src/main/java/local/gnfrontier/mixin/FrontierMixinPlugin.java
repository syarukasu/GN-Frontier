package local.gnfrontier.mixin;

import java.util.List;
import java.util.Set;
import local.gnfrontier.compat.ExfilServerGuard;
import local.gnfrontier.compat.AnomalyDialogServerGuard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class FrontierMixinPlugin implements IMixinConfigPlugin {
    private static final String EXFIL = "local.gnfrontier.mixin.ExfilDedicatedServerMixin";
    private static final String DIALOG = "local.gnfrontier.mixin.AnomalyDialogServerMixin";

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return (!EXFIL.equals(mixinClassName) && !DIALOG.equals(mixinClassName))
                || FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }

    @Override public void preApply(String targetClassName, ClassNode targetClass,
                                   String mixinClassName, IMixinInfo mixinInfo) {
        if (EXFIL.equals(mixinClassName)) {
            if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
                throw new IllegalStateException("ExfilZones side correction must not run on the client");
            }
            ExfilServerGuard.removeClientRegistration(targetClass);
            com.mojang.logging.LogUtils.getLogger().info(
                    "GN Frontier: skipped ExfilZones client event registration on dedicated server");
        }
        if (DIALOG.equals(mixinClassName)) {
            if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
                throw new IllegalStateException("Dialog correction must not run on the client");
            }
            AnomalyDialogServerGuard.removeClientRenderer(targetClass);
            com.mojang.logging.LogUtils.getLogger().info(
                    "GN Frontier: isolated client dialog renderer on dedicated server: {}", targetClassName);
        }
    }

    @Override public void postApply(String targetClassName, ClassNode targetClass,
                                    String mixinClassName, IMixinInfo mixinInfo) {}
}
