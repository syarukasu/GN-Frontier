package local.gnfrontier.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** GunDB 2.2.2 draws this one label as a literal; preserve its numerical styled siblings. */
@Mixin(value=Font.class,remap=false)
public abstract class GunTooltipJapaneseMixin {
    @ModifyVariable(method="m_272077_(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Component gnfrontier$jamLabel(Component text){
        if(!(text.getContents() instanceof LiteralContents literal)||!literal.text().equals("Jam Chance: ")
            ||!Minecraft.getInstance().getLanguageManager().getSelected().equals("ja_jp"))return text;
        var translated=Component.translatable("tooltip.gnfrontier.jam_chance").withStyle(text.getStyle());
        text.getSiblings().forEach(s->translated.append(s.copy()));return translated;
    }
}
