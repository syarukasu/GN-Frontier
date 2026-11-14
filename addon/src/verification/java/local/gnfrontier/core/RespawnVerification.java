package local.gnfrontier.core;

import java.util.UUID;
import local.gnfrontier.WorldState;
import net.minecraft.nbt.CompoundTag;

/** Policy and persisted-state acceptance checks. Does not claim live AI/pathfinding acceptance. */
public final class RespawnVerification {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }

    public static void main(String[] args) {
        Economy.State account = new Economy.State();
        check(Economy.canMoveSpawn(account, 0, "minecraft:overworld", 123), "First registration is immediate");
        account.respawnChangedAt = 100;
        account.spawnDimension = "minecraft:overworld";
        account.spawnPosition = 123;
        check(Economy.canMoveSpawn(account, 101, "minecraft:overworld", 123), "Same bed reuse allowed during cooldown");
        check(!Economy.canMoveSpawn(account, 72099, "minecraft:overworld", 124), "Change denied just before three days");
        check(Economy.canMoveSpawn(account, 72100, "minecraft:overworld", 124), "Change allowed at exactly three days");
        check(!Economy.canMoveSpawn(account, 1000, "ad_astra:mars", 123), "Same coordinates in another dimension are a move");
        check(!Economy.canMoveSpawn(account, 99, "minecraft:overworld", 124), "Clock rewind cannot bypass cooldown");
        RespawnPolicy.loseBed(account);
        check(account.spawnDimension.isEmpty() && account.spawnPosition == 0, "Bed loss revokes its protection coordinates");
        check(account.respawnChangedAt == 100, "Bed loss preserves cooldown origin");
        check(!Economy.canMoveSpawn(account, 101, "minecraft:overworld", 123), "Rebuild at identical coordinates cannot bypass cooldown");
        check(RespawnPolicy.cooldownRemaining(account, 99) == 72000, "Rewound time gives no early unlock");
        check(RespawnPolicy.cooldownRemaining(account, 72099) == 1, "Last cooldown tick");
        check(RespawnPolicy.cooldownRemaining(account, 72100) == 0, "Exact cooldown expiry");

        RespawnPolicy.State protection = new RespawnPolicy.State();
        check(!protection.hasGrace(0), "Old saves do not acquire invented grace");
        protection.initialDimension = "minecraft:overworld";
        protection.initialPosition = 12345;
        protection.beginGrace(1000, 200);
        check(!protection.hasGrace(999), "Clock rewind cannot activate grace");
        check(protection.hasGrace(1000) && protection.hasGrace(1199), "Grace covers its exact 200-tick interval");
        check(!protection.hasGrace(1200), "Grace expires, rather than persisting as invulnerable flag");
        RespawnPolicy.State restored = RespawnPolicy.State.load(protection.save());
        check(restored.hasGrace(1100) && !restored.hasGrace(1200), "Save and reconnect do not extend the interval");
        check(restored.initialPosition == 12345 && restored.initialDimension.equals("minecraft:overworld"), "Initial location survives save");
        restored.beginGrace(2000, 0);
        check(!restored.hasGrace(2000), "Zero disables timed protection");
        check(protection.graceStarted == 1000, "Reloaded state is independent");
        check(RespawnPolicy.inside(3, 0, 4, 0, 0, 0, 5, 5), "Radius-five boundary included");
        check(!RespawnPolicy.inside(3.01, 0, 4, 0, 0, 0, 5, 5), "Beyond radius excluded");
        check(RespawnPolicy.inside(0, -5, 0, 0, 0, 0, 5, 5), "Lower vertical boundary");
        check(!RespawnPolicy.inside(0, 5.01, 0, 0, 0, 0, 5, 5), "Above vertical boundary excluded");

        WorldState world = new WorldState();
        world.enabled = true;
        UUID owner = UUID.fromString("3cecb41e-2a12-4610-8fd6-b83e01ec03d1");
        UUID second = UUID.fromString("306815c9-2116-4390-9075-d29d12ce3eb8");
        world.players.put(owner, account);
        world.players.put(second, new Economy.State());
        world.respawns.put(owner, protection);
        WorldState decoded = WorldState.load(world.save(new CompoundTag()));
        check(decoded.players.get(owner).respawnChangedAt == 100, "Cooldown persists after destroyed bed and restart");
        check(decoded.respawns.get(owner).hasGrace(1100), "Protection is stored with the correct player");
        check(!decoded.respawns.containsKey(second), "Another player cannot inherit grace");
        check(decoded.players.get(second).respawnChangedAt == -1, "Another player retains first-registration eligibility");
        // Replaying the old format without the optional respawn field is still accepted.
        WorldState old = new WorldState(); old.players.put(owner, new Economy.State());
        check(WorldState.load(old.save(new CompoundTag())).respawns.isEmpty(), "Backward-compatible old account migration");
        System.out.println("GN_RESPAWN_POLICY_PASS checks=" + checks + " liveAI=false worldStarted=false");
    }
}
