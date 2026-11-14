package local.gnfrontier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;

/** Normal mining also salvages a model; helper collision blocks never yield materials. */
public final class SalvageLoot extends LootModifier {
    private static final ResourceLocation CREATE_WRENCH=new ResourceLocation("create","wrench");
    public static final Codec<SalvageLoot> CODEC=RecordCodecBuilder.create(i->codecStart(i).apply(i,SalvageLoot::new));
    public SalvageLoot(LootItemCondition[] c){super(c);}
    public static boolean isCreateWrench(ItemStack tool) {
        return tool!=null && !tool.isEmpty() && CREATE_WRENCH.equals(ForgeRegistries.ITEMS.getKey(tool.getItem()));
    }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> result,LootContext ctx) {
        if(!WorldState.get(ctx.getLevel()).enabled)return result;
        var state=ctx.getParamOrNull(LootContextParams.BLOCK_STATE);var origin=ctx.getParamOrNull(LootContextParams.ORIGIN);
        if(state==null || origin==null)return result;
        boolean car=state.is(Frontier.SALVAGE_VEHICLES),appliance=state.is(Frontier.SALVAGE_APPLIANCES),metal=state.is(Frontier.SALVAGE_METAL);
        if(!car && !appliance && !metal)return result;
        if(WorldState.get(ctx.getLevel()).placedBlocks.contains(Gameplay.key(ctx.getLevel(),BlockPos.containing(origin))))return result;
        // Read the actual breaking tool, never an offhand or a nearby player's held item.
        boolean wrench=car && isCreateWrench(ctx.getParamOrNull(LootContextParams.TOOL));
        result.clear();result.add(new ItemStack(Frontier.ROUGH_IRON.get(),car?(wrench?3:2):1));
        result.add(new ItemStack(Frontier.METAL_SCRAP.get(),car?(wrench?6:4):2));
        if(car||appliance){
            result.add(new ItemStack(Frontier.PLASTIC_FRAGMENT.get(),wrench?3:1));
            if(car && ctx.getRandom().nextInt(wrench?2:4)==0)result.add(new ItemStack(Frontier.ALUMINUM_CHUNK.get()));
            result.add(new ItemStack(Frontier.ALUMINUM_SCRAP.get(),car?(wrench?6:4):2));
            result.add(new ItemStack(TradeService.item("tfmg:copper_wire"),wrench?3:2));
            if(car)result.add(new ItemStack(TradeService.item("tfmg:rubber_sheet")));
            if(ctx.getRandom().nextInt(wrench?2:4)==0)result.add(new ItemStack(Frontier.WASTE_BATTERY.get()));
            if(appliance)result.add(new ItemStack(TradeService.item("survival_instinct:electronic_scrap")));
        }
        return result;
    }
    @Override public Codec<SalvageLoot> codec(){return CODEC;}
}
