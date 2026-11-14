package local.gnfrontier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;
import net.minecraftforge.registries.*;

public final class BedLoot extends LootModifier {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Frontier.ID);
    public static final RegistryObject<Codec<BedLoot>> CODEC = SERIALIZERS.register("bed_salvage", () -> RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, BedLoot::new)));
    public static final RegistryObject<Codec<ChestLoot>> CHESTS = SERIALIZERS.register("natural_chests", () -> ChestLoot.CODEC);
    public static final RegistryObject<Codec<SalvageLoot>> SALVAGE = SERIALIZERS.register("model_salvage", () -> SalvageLoot.CODEC);
    public BedLoot(LootItemCondition[] conditions) { super(conditions); }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generated, LootContext context) {
        if (!WorldState.get(context.getLevel()).enabled) return generated;
        // Looted iron is early scrap-tier material. Crafting results and player inventory drops are not loot-table rolls.
        for (int i = 0; i < generated.size(); i++) {
            ItemStack original = generated.get(i);
            if (original.is(net.minecraft.world.item.Items.IRON_INGOT)) {
                ItemStack rough = new ItemStack(Frontier.ROUGH_IRON.get(), original.getCount());
                if (original.hasTag()) rough.setTag(original.getTag().copy());
                generated.set(i, rough);
            }
        }
        BlockState block = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (block == null || !(block.getBlock() instanceof BedBlock)) return generated;
        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) return generated;
        BlockPos pos = BlockPos.containing(origin); WorldState world = WorldState.get(context.getLevel());
        if (!world.placedBlocks.contains(Gameplay.key(context.getLevel(), pos))) generated.clear();
        // Do not mutate tracking here: loot queries (JEI, probes) are allowed to be read-only.
        return generated;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC.get(); }
}
