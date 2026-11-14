package local.gnfrontier;

import local.gnfrontier.core.Economy;
import net.minecraft.server.level.ServerPlayer;
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
            for (int i = 0; i < 2; i++) {
                Object faction = subjectClass.getMethod("faction", String.class).invoke(null, i == 0 ? "ru" : "us");
                String status = account.alert[i] >= 30 || account.reputation[i] <= -20 ? "ENEMY" : account.reputation[i] >= 30 ? "ALLY" : "NEUTRAL";
                set.invoke(data, subject, faction, Enum.valueOf(relationClass, status));
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            failed = true;
            LogUtils.getLogger().error("GN Frontier: Combined Arms diplomacy link unavailable; economy data remains intact", ex);
        }
    }
}
