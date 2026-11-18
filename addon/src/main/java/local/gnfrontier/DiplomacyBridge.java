package local.gnfrontier;

import local.gnfrontier.core.Economy;
import local.gnfrontier.core.FactionStanding;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;

/** Optional, public-method bridge pinned to the inspected Combined Arms 0.1.7 API. No bytecode patch. */
public final class DiplomacyBridge {
    private static boolean failed;
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void sync(ServerPlayer player, Economy.State account) {
        if (failed || !ModList.get().isLoaded("tacz_sewv")) return;
        try {
            Class<?> dataClass = Class.forName("com.neoalive.tacz_sewv.diplomacy.DiplomacyData");
            Class<?> subjectClass = Class.forName("com.neoalive.tacz_sewv.diplomacy.DiplomacyData$Subject");
            Class<? extends Enum> relationClass = (Class<? extends Enum>) Class.forName("com.neoalive.tacz_sewv.diplomacy.DiplomacyData$Relation");
            Object data = dataClass.getMethod("get", Level.class).invoke(null, player.serverLevel());
            Object subject = subjectClass.getMethod("player", String.class).invoke(null, player.getGameProfile().getName());
            Method set = dataClass.getMethod("set", subjectClass, subjectClass, relationClass);
            Method relation = dataClass.getMethod("relation", subjectClass, subjectClass);
            for (int i = 0; i < 2; i++) {
                Object faction = subjectClass.getMethod("faction", String.class).invoke(null, i == 0 ? "ru" : "us");
                var status = FactionStanding.relation(account, i);
                Object desired = Enum.valueOf(relationClass, status.name());
                if (relation.invoke(data, subject, faction) == desired) continue;
                set.invoke(data, subject, faction, desired);
                if (status == FactionStanding.Relation.ALLY) dropAlliedTargets(player, i);
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            failed = true;
            LogUtils.getLogger().error("GN Frontier: Combined Arms diplomacy link unavailable; economy data remains intact", ex);
        }
    }

    private static void dropAlliedTargets(ServerPlayer player, int faction) {
        for (Mob unit : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64),
                mob -> mob.getTarget() == player && FactionHostility.faction(mob) == faction)) {
            unit.setTarget(null);
        }
    }
}
