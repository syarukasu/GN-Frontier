package local.gnfrontier;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.*;

/** Equipment slots are live views, not duplicate copies. Only the backpack is serialized here. */
@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class NpcInventory implements IItemHandlerModifiable, ICapabilitySerializable<CompoundTag> {
    public static final Capability<NpcInventory> CAPABILITY=CapabilityManager.get(new CapabilityToken<>(){});
    public static final EquipmentSlot[] EQUIPMENT={EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD};
    private final Mob mob;
    private final ItemStackHandler backpack=new ItemStackHandler(21);
    private final LazyOptional<NpcInventory> capability=LazyOptional.of(()->this);
    public NpcInventory(Mob mob){this.mob=mob;}
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<Entity> e){
        if(!(e.getObject() instanceof Mob mob)||!NpcEquipment.candidate(mob))return;
        // PMC already supplies the native equipment/backpack capability; never replace it.
        if(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()).getPath().contains("pmc"))return;
        var inventory=new NpcInventory(mob);e.addCapability(Frontier.id("npc_inventory"),inventory);e.addListener(inventory.capability::invalidate);
    }
    @Override public int getSlots(){return 27;}
    private void slot(int slot){if(slot<0||slot>=getSlots())throw new IndexOutOfBoundsException(slot);}
    @Override public ItemStack getStackInSlot(int slot){slot(slot);return slot<6?mob.getItemBySlot(EQUIPMENT[slot]):backpack.getStackInSlot(slot-6);}
    @Override public void setStackInSlot(int slot,ItemStack stack){slot(slot);if(slot<6)mob.setItemSlot(EQUIPMENT[slot],stack);else backpack.setStackInSlot(slot-6,stack);}
    @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
        slot(slot);if(slot>=6)return backpack.insertItem(slot-6,stack,simulate);
        if(stack.isEmpty()||!getStackInSlot(slot).isEmpty()||!isItemValid(slot,stack))return stack;
        int count=Math.min(getSlotLimit(slot),stack.getCount());if(!simulate)setStackInSlot(slot,stack.copyWithCount(count));
        return stack.getCount()==count?ItemStack.EMPTY:stack.copyWithCount(stack.getCount()-count);
    }
    @Override public ItemStack extractItem(int slot,int amount,boolean simulate){
        slot(slot);if(slot>=6)return backpack.extractItem(slot-6,amount,simulate);
        if(amount<=0)return ItemStack.EMPTY;ItemStack held=getStackInSlot(slot);int count=Math.min(amount,held.getCount());
        ItemStack result=held.copyWithCount(count);if(!simulate)setStackInSlot(slot,held.copyWithCount(held.getCount()-count));return result;
    }
    @Override public int getSlotLimit(int slot){slot(slot);return slot<6?1:64;}
    @Override public boolean isItemValid(int slot,ItemStack stack){slot(slot);return slot<2||slot>=6||Mob.getEquipmentSlotForItem(stack)==EQUIPMENT[slot];}
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){return cap==CAPABILITY?capability.cast():LazyOptional.empty();}
    @Override public CompoundTag serializeNBT(){return backpack.serializeNBT();}
    @Override public void deserializeNBT(CompoundTag tag){backpack.deserializeNBT(tag);}
    @Mod.EventBusSubscriber(modid=Frontier.ID,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration{
        @SubscribeEvent public static void register(RegisterCapabilitiesEvent e){e.register(NpcInventory.class);}
    }
}
