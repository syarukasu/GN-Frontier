package local.gnfrontier.mixin;

import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Keep original membership and team flags while avoiding duplicate registration and updates. */
@Mixin(targets={
    "net.mcreator.skarriermobs.procedures.MobTeamWroughtProcedure",
    "net.mcreator.skarriermobs.procedures.MobTeamHellamanderProcedure",
    "net.mcreator.skarriermobs.procedures.MobTeamGraveEntityProcedure",
    "net.mcreator.skarriermobs.procedures.MobTeamFloreProcedure",
    "net.mcreator.skarriermobs.procedures.MobTeamEndorahProcedure",
    "net.mcreator.skarriermobs.procedures.MobTeamCarnagerProcedure"
},remap=false)
public abstract class SkarrierTeamSetupMixin {
    @Redirect(method="execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/scores/Scoreboard;m_83492_(Ljava/lang/String;)Lnet/minecraft/world/scores/PlayerTeam;"))
    private static PlayerTeam gnfrontier$existingTeam(Scoreboard board,String name){var team=board.getPlayerTeam(name);return team!=null?team:board.addPlayerTeam(name);}
    @Redirect(method="execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/scores/PlayerTeam;m_83355_(Z)V"))
    private static void gnfrontier$changedFlag(PlayerTeam team,boolean value){if(team.isAllowFriendlyFire()!=value)team.setAllowFriendlyFire(value);}
}
