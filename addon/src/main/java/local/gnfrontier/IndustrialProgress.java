package local.gnfrontier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Receiving steel records progress too: fan/basin outputs are not crafting-grid events. */
public final class IndustrialProgress {
    private static final String MILESTONE="gnfrontier:steel_acquired";
    private static final TagKey<Item> STEEL=TagKey.create(Registries.ITEM,new ResourceLocation("forge","ingots/steel"));
    public static boolean steelUnlocked(ServerPlayer player) {
        WorldState world=WorldState.get(player.serverLevel());
        var progress=world.campaign(player.getUUID());
        if(progress.milestones.contains(MILESTONE))return true;
        for(ItemStack stack:player.getInventory().items) {
            if(!stack.isEmpty() && stack.is(STEEL)) {
                progress.milestones.add(MILESTONE);world.setDirty();return true;
            }
        }
        return false;
    }
    public static boolean canBuy(ServerPlayer player,String offer) {
        return !offer.equals("mask") || steelUnlocked(player);
    }
}
