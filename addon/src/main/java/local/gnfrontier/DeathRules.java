package local.gnfrontier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;

public final class DeathRules {
    private static boolean enabled(ServerPlayer p) {
        return WorldState.get(p.serverLevel()).enabled && PrototypeConfig.DELETE_ON_DEATH.get();
    }
    private static void clear(IItemHandler handler) {
        for (int i=0;i<handler.getSlots();i++) {
            if (handler instanceof IItemHandlerModifiable writable) writable.setStackInSlot(i, ItemStack.EMPTY);
            else handler.extractItem(i, Integer.MAX_VALUE, false);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void curioDrops(DropRulesEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && enabled(p)) e.addOverride(stack -> true, ICurio.DropRule.DESTROY);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void death(LivingDeathEvent e) {
        if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p && enabled(p)) clearCarried(p);
    }
    private static void clearCarried(ServerPlayer p) {
        p.containerMenu.setCarried(ItemStack.EMPTY);
        p.inventoryMenu.setCarried(ItemStack.EMPTY);
        // Inventory crafting grid belongs to the player; do not clear an open chest's storage.
        for (int i=1;i<=4;i++) p.inventoryMenu.getSlot(i).set(ItemStack.EMPTY);
        if(p.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu)
            for(int i=1;i<=9;i++)p.containerMenu.getSlot(i).set(ItemStack.EMPTY);
        if(p.containerMenu instanceof net.minecraft.world.inventory.ItemCombinerMenu combiner)
            for(int i=0;i<=combiner.getResultSlot();i++)combiner.getSlot(i).set(ItemStack.EMPTY);
        if(p.containerMenu instanceof net.minecraft.world.inventory.EnchantmentMenu)
            for(int i=0;i<2;i++)p.containerMenu.getSlot(i).set(ItemStack.EMPTY);
        if(p.containerMenu instanceof net.minecraft.world.inventory.GrindstoneMenu || p.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu)
            for(int i=0;i<3;i++)p.containerMenu.getSlot(i).set(ItemStack.EMPTY);
        p.getInventory().clearContent();
        p.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(DeathRules::clear);
        CuriosApi.getCuriosInventory(p).ifPresent(inv -> inv.getCurios().values().forEach(slots -> {
            clear(slots.getStacks()); clear(slots.getCosmeticStacks());
        }));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void clone(PlayerEvent.Clone e) {
        if (e.isWasDeath() && e.getEntity() instanceof ServerPlayer p && enabled(p)) clearCarried(p);
    }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!e.isEndConquered() && e.getEntity() instanceof ServerPlayer p && WorldState.get(p.serverLevel()).enabled) {
            RespawnRules.respawn(p);
        }
    }
}
