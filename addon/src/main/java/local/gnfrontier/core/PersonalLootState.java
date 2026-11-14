package local.gnfrontier.core;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Stable container identity; player inventories are owned and persisted by Lootr. */
public record PersonalLootState(UUID id, ResourceLocation table, long seed) {
    public static final String KEY = "gnfrontier_personal_loot";
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id); tag.putString("Table", table.toString()); tag.putLong("Seed", seed);
        return tag;
    }
    public static PersonalLootState load(CompoundTag persistent) {
        CompoundTag tag = persistent.getCompound(KEY);
        ResourceLocation table = ResourceLocation.tryParse(tag.getString("Table"));
        return tag.hasUUID("Id") && table != null ? new PersonalLootState(tag.getUUID("Id"), table, tag.getLong("Seed")) : null;
    }
}
