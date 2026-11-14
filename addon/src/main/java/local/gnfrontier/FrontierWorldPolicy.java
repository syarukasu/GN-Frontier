package local.gnfrontier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Applies to newly generated worlds; existing saves without this marker keep their rules. */
@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class FrontierWorldPolicy extends SavedData {
    private static final String KEY="gnfrontier_world_policy";
    private boolean hard;
    private static FrontierWorldPolicy load(CompoundTag n){var p=new FrontierWorldPolicy();p.hard=n.getBoolean("hard");return p;}
    @Override public CompoundTag save(CompoundTag n){n.putBoolean("hard",hard);return n;}
    public static boolean locked(MinecraftServer server) {
        var level=server.overworld(); if(level==null)return false;
        var policy=level.getDataStorage().get(FrontierWorldPolicy::load,KEY);
        return policy!=null&&policy.hard;
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
    public static void fresh(LevelEvent.CreateSpawnPosition event) {
        if(!(event.getLevel() instanceof ServerLevel level)||level.dimension()!=Level.OVERWORLD)return;
        var p=level.getDataStorage().computeIfAbsent(FrontierWorldPolicy::load,FrontierWorldPolicy::new,KEY);
        p.hard=true;p.setDirty(); enforce(level.getServer());
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(locked(event.getServer()))enforce(event.getServer());
    }
    private static void enforce(MinecraftServer server) {
        server.setDifficulty(Difficulty.HARD,true);
        server.setDifficultyLocked(true);
    }
}
