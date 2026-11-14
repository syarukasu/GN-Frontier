package local.gnfrontier;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;

/** A normal JEI-visible shapeless recipe whose lighter survives with one use of wear. */
public final class TorchLightingRecipe extends ShapelessRecipe {
    private TorchLightingRecipe(ShapelessRecipe source) {
        super(source.getId(), source.getGroup(), source.category(), source.getResultItem(RegistryAccess.EMPTY).copy(), source.getIngredients());
    }

    @Override public RecipeSerializer<?> getSerializer() { return Frontier.TORCH_LIGHTING.get(); }

    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(grid);
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack ingredient = grid.getItem(slot);
            if (!ingredient.is(Items.FLINT_AND_STEEL)) continue;
            ItemStack tool = ingredient.copy();
            tool.setCount(1);
            tool.setDamageValue(tool.getDamageValue() + 1);
            remaining.set(slot, tool.getDamageValue() < tool.getMaxDamage() ? tool : ItemStack.EMPTY);
        }
        return remaining;
    }

    public static final class Serializer implements RecipeSerializer<TorchLightingRecipe> {
        @Override public TorchLightingRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new TorchLightingRecipe(RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json));
        }
        @Override public TorchLightingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            ShapelessRecipe recipe = RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buffer);
            return recipe == null ? null : new TorchLightingRecipe(recipe);
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, TorchLightingRecipe recipe) {
            RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
