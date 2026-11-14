package local.gnfrontier;

import java.util.UUID;
import local.gnfrontier.core.PersonalLootState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noobanidus.mods.lootr.api.LootrAPI;

/** Personal natural furniture inventories without replacing the furniture block/model. */
public final class PersonalFurnitureLoot {
    public static boolean hasPersonalLoot(BlockEntity entity) {
        return entity != null && PersonalLootState.load(entity.getPersistentData()) != null;
    }

    public static boolean assign(RandomizableContainerBlockEntity container, ResourceLocation table, long seed) {
        if (hasPersonalLoot(container)) return false;
        int size = container.getContainerSize();
        if (size < 9 || size > 54 || size % 9 != 0) return false;
        // Inspect saved stacks without unpacking a pending vanilla loot table.
        if (!container.saveWithoutMetadata().getList("Items", 10).isEmpty()) return false;
        var state = new PersonalLootState(UUID.randomUUID(), table, seed);
        container.getPersistentData().put(PersonalLootState.KEY, state.save());
        // Hoppers must never unpack a second, shared copy from the physical inventory.
        container.setLootTable(null, 0L);
        container.setChanged();
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void open(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        BlockEntity entity = level.getBlockEntity(event.getPos());
        if (!(entity instanceof RandomizableContainerBlockEntity container) || !entity.getBlockState().is(Frontier.LOOT_FURNITURE)) return;
        if (!hasPersonalLoot(container) && WorldState.get(level).enabled && entity.getPersistentData().getBoolean("gnfrontier_loot_assigned")) {
            CompoundTag saved = entity.saveWithoutMetadata();
            ResourceLocation table = ResourceLocation.tryParse(saved.getString("LootTable"));
            if (table != null) assign(container, table, saved.getLong("LootTableSeed"));
        }
        var state = PersonalLootState.load(entity.getPersistentData());
        if (state == null) return;
        // Respect earlier protection handlers and vanilla spectator/container lock rules.
        if (event.getUseBlock() == net.minecraftforge.eventbus.api.Event.Result.DENY) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (player.isSpectator() || LootrAPI.isFakePlayer(player) || !container.canOpen(player)) return;
        var inventory = LootrAPI.getInventory(level, state.id(), event.getPos(), player, container,
            (opener, items, table, seed) -> {
                var params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(entity.getBlockPos()))
                    .withParameter(LootContextParams.THIS_ENTITY, opener)
                    .withLuck(opener.getLuck()).create(LootContextParamSets.CHEST);
                level.getServer().getLootData().getLootTable(table).fill(items, params, LootrAPI.getLootSeed(seed));
            }, state::table, state::seed);
        if (inventory != null) player.openMenu(inventory);
    }

    @SubscribeEvent public void breakBlock(BlockEvent.BreakEvent event) {
        if (hasPersonalLoot(event.getLevel().getBlockEntity(event.getPos()))
            && !(event.getPlayer().isCreative() && event.getPlayer().isShiftKeyDown())) event.setCanceled(true);
    }

    @SubscribeEvent public void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> hasPersonalLoot(event.getLevel().getBlockEntity(pos)));
    }
}
