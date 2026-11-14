package local.gnfrontier.core;

import net.minecraft.nbt.CompoundTag;

/** Persisted per-player protection; economy and bed cooldown remain separate. */
public final class RespawnPolicy {
    private RespawnPolicy() {}

    public static boolean inside(double x, double y, double z, double cx, double cy, double cz,
                                 int radius, int halfHeight) {
        double dx = x - cx, dz = z - cz;
        return Math.abs(y - cy) <= halfHeight && dx * dx + dz * dz <= (double) radius * radius;
    }

    public static long cooldownRemaining(Economy.State account, long now) {
        if (account.respawnChangedAt < 0) return 0;
        return Math.max(0, Economy.DAY_TICKS * 3 - Math.max(0, now - account.respawnChangedAt));
    }

    public static void loseBed(Economy.State account) {
        // Losing or rebuilding a bed cannot reset the relocation cooldown.
        account.spawnDimension = "";
        account.spawnPosition = 0;
    }

    public static final class State {
        public String initialDimension = "";
        public long initialPosition;
        public boolean useInitial = true;
        public long graceStarted = -1, graceUntil = -1;

        public void beginGrace(long now, int ticks) {
            if (now < 0 || ticks < 0) throw new IllegalArgumentException("Invalid protection interval");
            graceStarted = now;
            graceUntil = Math.addExact(now, ticks);
        }

        public boolean hasGrace(long now) {
            return graceStarted >= 0 && now >= graceStarted && now < graceUntil;
        }

        public CompoundTag save() {
            CompoundTag n = new CompoundTag();
            n.putString("initialDimension", initialDimension);
            n.putLong("initialPosition", initialPosition);
            n.putBoolean("useInitial", useInitial);
            n.putLong("graceStarted", graceStarted);
            n.putLong("graceUntil", graceUntil);
            return n;
        }

        public static State load(CompoundTag n) {
            State s = new State();
            s.initialDimension = n.getString("initialDimension");
            s.initialPosition = n.getLong("initialPosition");
            s.useInitial = !n.contains("useInitial") || n.getBoolean("useInitial");
            s.graceStarted = n.contains("graceStarted") ? n.getLong("graceStarted") : -1;
            s.graceUntil = n.contains("graceUntil") ? n.getLong("graceUntil") : -1;
            if (s.graceStarted < -1 || s.graceUntil < s.graceStarted)
                throw new IllegalStateException("Invalid saved respawn protection");
            return s;
        }
    }
}
