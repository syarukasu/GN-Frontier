package local.gnfrontier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;
import java.util.List;

/** Routes only named, natural chest tables; never refills a container or rewrites its inventory. */
public final class ChestLoot extends LootModifier {
    public static final Codec<ChestLoot> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance).and(instance.group(
            ResourceLocation.CODEC.listOf().fieldOf("sources").forGetter(m -> m.sources),
            ResourceLocation.CODEC.fieldOf("target").forGetter(m -> m.target))).apply(instance, ChestLoot::new));
    private final List<ResourceLocation> sources;
    private final ResourceLocation target;
    public ChestLoot(LootItemCondition[] conditions, List<ResourceLocation> sources, ResourceLocation target) {
        super(conditions); this.sources = List.copyOf(sources); this.target = target;
    }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generated, LootContext context) {
        if (!WorldState.get(context.getLevel()).enabled || !sources.contains(context.getQueriedLootTableId())) return generated;
        LootTable table = context.getLevel().getServer().getLootData().getLootTable(target);
        if (table == LootTable.EMPTY) return generated; // Fail closed when a datapack removes the target.
        ObjectArrayList<ItemStack> result = new ObjectArrayList<>();
        // Raw generation deliberately avoids recursively invoking global modifiers with the original context ID.
        table.getRandomItemsRaw(context, LootTable.createStackSplitter(context.getLevel(), result::add));
        return result;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
