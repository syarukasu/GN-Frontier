package local.gnfrontier;

import java.util.*;
import local.gnfrontier.core.Economy;
import local.gnfrontier.core.RespawnPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;

/** Initial spawn, bed identity, relocation cooldown and damage protection. Server-owned only. */
public final class RespawnRules {
    private final Set<UUID> pendingSpawnChanges = new HashSet<>();

    public static boolean enabled(Level level) {
        return level instanceof ServerLevel server && WorldState.get(server).enabled;
    }

    private static long clock(ServerLevel level) { return level.getServer().overworld().getGameTime(); }
    private static String dimension(ServerLevel level) { return level.dimension().location().toString(); }

    /** No chunk generation or loading while checking protection or AI targets. */
    public static BlockPos bedHead(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return null;
        return state.getValue(BedBlock.PART) == BedPart.HEAD ? pos.immutable()
                : pos.relative(state.getValue(BedBlock.FACING));
    }

    public static boolean intactBed(ServerLevel level, BlockPos head) {
        if (!level.hasChunkAt(head)) return false;
        BlockState state = level.getBlockState(head);
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) return false;
        BlockPos foot = head.relative(state.getValue(BedBlock.FACING).getOpposite());
        if (!level.hasChunkAt(foot)) return false;
        BlockState other = level.getBlockState(foot);
        return other.is(state.getBlock()) && other.getValue(BedBlock.PART) == BedPart.FOOT
                && other.getValue(BedBlock.FACING) == state.getValue(BedBlock.FACING);
    }

    private static boolean matchesSpawn(ServerPlayer p, ServerLevel level, BlockPos head) {
        return p.getRespawnPosition() != null && p.getRespawnDimension().equals(level.dimension())
                && head.equals(bedHead(level, p.getRespawnPosition()));
    }

    public static void initialize(ServerPlayer p, boolean firstJoin) {
        WorldState data = WorldState.get(p.serverLevel());
        if (data.respawns.containsKey(p.getUUID())) return;
        RespawnPolicy.State state = new RespawnPolicy.State();
        ServerLevel initialLevel = firstJoin ? p.serverLevel() : p.server.overworld();
        // Old saves never turn the player's current base into a new initial safe area.
        state.initialDimension = dimension(initialLevel);
        state.initialPosition = (firstJoin ? p.blockPosition() : initialLevel.getSharedSpawnPos()).asLong();
        state.useInitial = p.getRespawnPosition() == null;
        if (firstJoin) state.beginGrace(clock(p.serverLevel()), PrototypeConfig.RESPAWN_GRACE.get());
        data.respawns.put(p.getUUID(), state);
        data.setDirty();
        if (firstJoin) p.sendSystemMessage(Component.translatable("message.gnfrontier.initial_spawn", PrototypeConfig.SHELTER_RADIUS.get()));
    }

    public static void loggedIn(ServerPlayer p, boolean firstJoin) {
        initialize(p, firstJoin);
        Economy.State account = WorldState.get(p.serverLevel()).account(p.getUUID());
        // An offline owner's old player.dat may still name a bed that was destroyed and then rebuilt.
        if (account.respawnChangedAt >= 0 && account.spawnDimension.isEmpty()
                && p.getRespawnPosition() != null && !p.isRespawnForced())
            p.setRespawnPosition(Level.OVERWORLD, null, 0, true, false);
        reconcile(p);
    }

    public static boolean registerBed(ServerPlayer p, ServerLevel level, BlockPos pos) {
        BlockPos head = bedHead(level, pos);
        if (!level.dimensionType().bedWorks() || head == null || !intactBed(level, head)) {
            p.sendSystemMessage(Component.translatable("message.gnfrontier.invalid_bed"));
            return false;
        }
        WorldState data = WorldState.get(level);
        Economy.State account = data.account(p.getUUID());
        if (!Economy.canMoveSpawn(account, clock(level), dimension(level), head.asLong())) {
            cooldownMessage(p, account);
            return false;
        }
        p.setRespawnPosition(level.dimension(), head, p.getYRot(), false, true);
        // Forge or another protection mod may refuse the change. Commit only after the actual setter succeeded.
        if (!matchesSpawn(p, level, head)) {
            p.sendSystemMessage(Component.translatable("message.gnfrontier.bed_denied"));
            return false;
        }
        recordBed(p, level, head);
        p.sendSystemMessage(Component.translatable("message.gnfrontier.bed_registered"));
        return true;
    }

    private static void recordBed(ServerPlayer p, ServerLevel level, BlockPos head) {
        WorldState data = WorldState.get(level);
        initialize(p, false);
        Economy.State account = data.account(p.getUUID());
        if (account.respawnChangedAt < 0 || !account.spawnDimension.equals(dimension(level)) || account.spawnPosition != head.asLong()) {
            account.respawnChangedAt = clock(level);
            account.spawnDimension = dimension(level);
            account.spawnPosition = head.asLong();
            data.setDirty();
        }
        RespawnPolicy.State protection = data.respawns.get(p.getUUID());
        if (protection.useInitial) { protection.useInitial = false; data.setDirty(); }
    }

    private static void cooldownMessage(ServerPlayer p, Economy.State account) {
        long minutes = (RespawnPolicy.cooldownRemaining(account, clock(p.serverLevel())) + 1199) / 1200;
        p.sendSystemMessage(Component.translatable("message.gnfrontier.bed_cooldown", minutes));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void changingSpawn(PlayerSetSpawnEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p) || !enabled(p.level())) return;
        ServerLevel level = p.server.getLevel(e.getSpawnLevel());
        // Forced changes are administrative. A failed event must never consume the cooldown.
        if (!e.isForced() && level != null && e.getNewSpawn() != null) {
            BlockPos head = bedHead(level, e.getNewSpawn());
            if (head != null && !Economy.canMoveSpawn(WorldState.get(level).account(p.getUUID()),
                    clock(level), dimension(level), head.asLong())) {
                e.setCanceled(true);
                cooldownMessage(p, WorldState.get(level).account(p.getUUID()));
                return;
            }
        }
        pendingSpawnChanges.add(p.getUUID());
    }

    /** Called only for a real death respawn, never for login or return from the End. */
    public static void respawn(ServerPlayer p) {
        if (!enabled(p.level())) return;
        initialize(p, false);
        WorldState data = WorldState.get(p.serverLevel());
        RespawnPolicy.State state = data.respawns.get(p.getUUID());
        if (p.getRespawnPosition() == null) {
            // Vanilla also clears a bed when its exit is obstructed. Revoke that stale safe area now,
            // before the deferred spawn-change event is reconciled at the end of this tick.
            RespawnPolicy.loseBed(data.account(p.getUUID()));
            state.useInitial = true;
            returnToInitial(p, state);
        }
        state.beginGrace(clock(p.serverLevel()), PrototypeConfig.RESPAWN_GRACE.get());
        data.setDirty();
        if (PrototypeConfig.RESPAWN_GRACE.get() > 0)
            p.sendSystemMessage(Component.translatable("message.gnfrontier.respawn_grace", (PrototypeConfig.RESPAWN_GRACE.get() + 19) / 20));
    }

    private static void returnToInitial(ServerPlayer p, RespawnPolicy.State state) {
        ResourceLocation id = ResourceLocation.tryParse(state.initialDimension);
        if (id == null) return;
        ServerLevel level = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) return;
        BlockPos center = BlockPos.of(state.initialPosition);
        // Only already loaded terrain. If the old spawn is obstructed, preserve vanilla's safe fallback.
        for (int radius = 0; radius <= 4; radius++) for (int x = -radius; x <= radius; x++)
            for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                for (int dy : new int[]{0, 1, -1, 2, -2, 3, 4, 5, 6}) {
                    BlockPos pos = center.offset(x, dy, z);
                    if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
                    if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                            || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) continue;
                    Vec3 point = Vec3.atBottomCenterOf(pos);
                    if (!level.noCollision(p, p.getBoundingBox().move(point.subtract(p.position())))) continue;
                    p.teleportTo(level, point.x, point.y, point.z, p.getYRot(), p.getXRot());
                    return;
                }
            }
    }

    public static boolean withinArea(ServerLevel level, Vec3 point) {
        if (!enabled(level)) return false;
        WorldState data = WorldState.get(level);
        String dim = dimension(level);
        for (var row : data.players.entrySet()) {
            Economy.State account = row.getValue();
            BlockPos center = null;
            if (account.spawnDimension.equals(dim)) {
                BlockPos head = BlockPos.of(account.spawnPosition);
                if (intactBed(level, head)) center = head;
            } else if (account.spawnDimension.isEmpty()) {
                RespawnPolicy.State state = data.respawns.get(row.getKey());
                if (state != null && state.useInitial && state.initialDimension.equals(dim)) center = BlockPos.of(state.initialPosition);
            }
            if (center != null && RespawnPolicy.inside(point.x, point.y, point.z,
                    center.getX() + .5, center.getY(), center.getZ() + .5,
                    PrototypeConfig.SHELTER_RADIUS.get(), PrototypeConfig.SHELTER_HEIGHT.get())) return true;
        }
        return false;
    }

    public static List<BlockPos> nearbyRegisteredBeds(ServerLevel level, BlockPos origin, int radius) {
        return nearbyRegisteredBeds(level,origin,radius,null);
    }
    public static List<BlockPos> nearbyRegisteredBeds(ServerLevel level, BlockPos origin, int radius,net.minecraft.world.entity.Mob attacker) {
        Set<BlockPos> candidates = new HashSet<>();
        for (Economy.State account : WorldState.get(level).players.values()) {
            if (!account.spawnDimension.equals(dimension(level))) continue;
            BlockPos pos = BlockPos.of(account.spawnPosition);
            if (pos.distSqr(origin) <= (double) radius * radius && intactBed(level, pos)
                    &&(attacker==null||attackableBed(level,pos,attacker))) candidates.add(pos);
        }
        return candidates.stream().sorted(Comparator.comparingDouble(pos -> pos.distSqr(origin))).limit(8).toList();
    }

    public static boolean attackableBed(ServerLevel level,BlockPos head,net.minecraft.world.entity.Mob attacker){
        if(!intactBed(level,head))return false;
        boolean found=false;
        for(var entry:WorldState.get(level).players.entrySet()){
            var account=entry.getValue();
            if(!account.spawnDimension.equals(dimension(level))||account.spawnPosition!=head.asLong())continue;
            found=true;
            // Shared beds must also respect their friendly/neutral owners. Zombies have no diplomacy.
            if(RegisteredBedGoal.human(attacker)&&!FactionRelations.hostileTo(attacker,entry.getKey(),account))return false;
        }
        return found;
    }

    public static boolean registeredBed(ServerLevel level, BlockPos head) {
        return intactBed(level, head) && WorldState.get(level).players.values().stream()
                .anyMatch(s -> s.spawnDimension.equals(dimension(level)) && s.spawnPosition == head.asLong());
    }

    public static boolean explosionProtectedBed(ServerLevel level,BlockPos pos){
        if(!enabled(level))return false;
        BlockPos head=bedHead(level,pos);return head!=null&&registeredBed(level,head);
    }

    public static void removedBed(ServerLevel level, BlockPos head) {
        if (!enabled(level)) return;
        WorldState data = WorldState.get(level);
        for (var row : data.players.entrySet()) {
            Economy.State account = row.getValue();
            if (!account.spawnDimension.equals(dimension(level)) || account.spawnPosition != head.asLong()) continue;
            RespawnPolicy.loseBed(account);
            RespawnPolicy.State state = data.respawns.get(row.getKey());
            if (state != null) state.useInitial = true;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(row.getKey());
            if (p != null) {
                BlockPos current = p.getRespawnPosition();
                if (p.getRespawnDimension().equals(level.dimension()) && current != null
                        && (current.equals(head) || head.equals(bedHead(level, current))))
                    p.setRespawnPosition(Level.OVERWORLD, null, 0, true, false);
                p.sendSystemMessage(Component.translatable("message.gnfrontier.bed_lost"));
            }
            data.setDirty();
        }
    }

    private static boolean protects(ServerPlayer p, DamageSource source) {
        if (!enabled(p.level()) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return protectedFromPull(p);
    }

    public static boolean protectedFromPull(ServerPlayer p) {
        if (!enabled(p.level())) return false;
        RespawnPolicy.State state = WorldState.get(p.serverLevel()).respawns.get(p.getUUID());
        return (state != null && state.hasGrace(clock(p.serverLevel()))) || withinArea(p.serverLevel(), p.position());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void attack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && protects(p, e.getSource())) e.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void hurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && protects(p, e.getSource())) e.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void destroy(LivingDestroyBlockEvent e) {
        if(RegisteredBedGoal.checkingBreak(e.getEntity(),e.getPos()))return;
        if (e.getEntity().level() instanceof ServerLevel level && enabled(level) && Gameplay.protectedAt(level, e.getPos())) e.setCanceled(true);
    }

    private static void reconcile(ServerPlayer p) {
        WorldState data = WorldState.get(p.serverLevel());
        initialize(p, false);
        Economy.State account = data.account(p.getUUID());
        BlockPos spawn = p.getRespawnPosition();
        ServerLevel level = p.server.getLevel(p.getRespawnDimension());
        if (spawn != null && level != null) {
            if (!level.hasChunkAt(spawn)) return;
            BlockPos head = bedHead(level, spawn);
            if (head != null && intactBed(level, head)) { recordBed(p, level, head); return; }
        }
        if (!account.spawnDimension.isEmpty()) { RespawnPolicy.loseBed(account); data.setDirty(); }
        RespawnPolicy.State state = data.respawns.get(p.getUUID());
        if (state.useInitial != (spawn == null)) { state.useInitial = spawn == null; data.setDirty(); }
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !enabled(e.getServer().overworld())) return;
        for (UUID id : new HashSet<>(pendingSpawnChanges)) {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(id);
            if (p != null) reconcile(p);
        }
        pendingSpawnChanges.clear();
        if (clock(e.getServer().overworld()) % 20 != 0) return;
        // Covers old save entries and block replacement paths from other mods; never force-loads a home.
        for (ServerLevel level : e.getServer().getAllLevels()) {
            List<BlockPos> missing = new ArrayList<>();
            for (Economy.State account : WorldState.get(level).players.values()) {
                if (!account.spawnDimension.equals(dimension(level))) continue;
                BlockPos head = BlockPos.of(account.spawnPosition);
                if (level.hasChunkAt(head) && !intactBed(level, head)) {
                    BlockState block = level.getBlockState(head);
                    if (block.getBlock() instanceof BedBlock
                            && !level.hasChunkAt(head.relative(block.getValue(BedBlock.FACING).getOpposite()))) continue;
                    missing.add(head);
                }
            }
            for (BlockPos head : missing) removedBed(level, head);
        }
    }

    @SubscribeEvent public void stopped(ServerStoppedEvent e) { pendingSpawnChanges.clear(); }
}
