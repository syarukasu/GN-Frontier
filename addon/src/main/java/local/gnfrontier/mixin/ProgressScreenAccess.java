package local.gnfrontier.mixin;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=ProgressScreen.class,remap=false)
public interface ProgressScreenAccess {
    @Accessor("f_96506_") Component gn$header();
    @Accessor("f_96507_") Component gn$stage();
    @Accessor("f_96508_") int gn$progress();
}
