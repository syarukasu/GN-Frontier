package local.gnfrontier.mixin;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=CreateWorldScreen.class,remap=false)
public interface CreateWorldAccess {
    @Invoker("m_100972_") void gn$create();
}
