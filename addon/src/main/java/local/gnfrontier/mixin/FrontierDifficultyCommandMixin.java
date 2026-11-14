package local.gnfrontier.mixin;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import local.gnfrontier.FrontierWorldPolicy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.DifficultyCommand;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=DifficultyCommand.class,remap=false)
public abstract class FrontierDifficultyCommandMixin {
    @Inject(method="m_136944_",at=@At("HEAD"))
    private static void gn$locked(CommandSourceStack source,Difficulty difficulty,CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        if(difficulty!=Difficulty.HARD&&FrontierWorldPolicy.locked(source.getServer()))
            throw new SimpleCommandExceptionType(Component.translatable("gui.gnfrontier.create.difficulty_locked")).create();
    }
}
