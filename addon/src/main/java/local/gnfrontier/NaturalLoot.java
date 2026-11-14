package local.gnfrontier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;

/** One-time world generation hook for furniture whose templates omit LootTable NBT. */
public final class NaturalLoot {
    @SubscribeEvent public void generated(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        // Initial spawn chunks are prepared before ServerStartedEvent. gameTime==0 is the new-world bootstrap.
        if (level.getServer().overworld() != null) {
            if (!WorldState.get(level).enabled && level.getServer().overworld().getGameTime() != 0) return;
        } else if (level.getGameTime() != 0) return;
        // Visit existing block-entity positions only, not all 65k+ blocks, and never load another chunk.
        for (var pos : new ArrayList<>(chunk.getBlockEntitiesPos())) {
            if (!chunk.getBlockState(pos).is(Frontier.LOOT_FURNITURE)) continue;
            BlockEntity entity = chunk.getBlockEntity(pos);
            if (!(entity instanceof RandomizableContainerBlockEntity container)) continue;
            CompoundTag saved = entity.saveWithoutMetadata();
            if (PersonalFurnitureLoot.hasPersonalLoot(container)) continue;
            if (saved.contains("LootTable")) {
                PersonalFurnitureLoot.assign(container, new net.minecraft.resources.ResourceLocation(saved.getString("LootTable")), saved.getLong("LootTableSeed"));
                continue;
            }
            if (entity.getPersistentData().getBoolean("gnfrontier_loot_assigned") || !container.isEmpty()) continue;
            var blockId = ForgeRegistries.BLOCKS.getKey(entity.getBlockState().getBlock());
            if (blockId == null) continue;
            String path = blockId.getPath();
            String theme = path.contains("fridge") || path.contains("refrigerator") ? "food" : "drawer";
            PersonalFurnitureLoot.assign(container, Frontier.id("chests/" + theme), level.getSeed() ^ pos.asLong() ^ level.dimension().location().hashCode());
            entity.getPersistentData().putBoolean("gnfrontier_loot_assigned", true);
            entity.setChanged(); chunk.setUnsaved(true);
        }
    }
}
