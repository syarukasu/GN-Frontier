package local.gnfrontier;

import java.util.List;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/** A single shared, persistent inventory. No per-player regeneration and no death-time loot roll. */
public final class FallenSoldier extends Entity implements MenuProvider,IEntityAdditionalSpawnData {
    private static final List<EntityDataAccessor<ItemStack>> VISIBLE_EQUIPMENT=java.util.stream.IntStream.range(0,6)
        .mapToObj(i->SynchedEntityData.defineId(FallenSoldier.class,EntityDataSerializers.ITEM_STACK)).toList();
    public final SimpleContainer inventory=new SimpleContainer(54){
        @Override public boolean stillValid(Player player){return !isRemoved()&&player.distanceToSqr(FallenSoldier.this)<=64;}
    };
    private CompoundTag appearance=new CompoundTag();
    private int emptyTicks;
    private boolean finiteAmmo;
    private final java.util.Map<java.util.UUID,CorpseSearch> searches=new java.util.HashMap<>();
    private final java.util.Set<CorpseMenu> viewers=new java.util.HashSet<>();
    public CorpseSearch search(java.util.UUID player){return searches.computeIfAbsent(player,id->new CorpseSearch());}
    void watch(CorpseMenu menu){viewers.add(menu);}
    void unwatch(CorpseMenu menu){viewers.remove(menu);}
    public FallenSoldier(EntityType<? extends FallenSoldier> type,Level level){
        super(type,level);
        inventory.addListener(container->{
            if(level().isClientSide)return;
            for(int slot=0;slot<VISIBLE_EQUIPMENT.size();slot++){
                // Rendering needs one item; the shared inventory retains every actual round/item.
                ItemStack visible=inventory.getItem(slot).isEmpty()?ItemStack.EMPTY:inventory.getItem(slot).copyWithCount(1);
                if(!ItemStack.matches(entityData.get(VISIBLE_EQUIPMENT.get(slot)),visible))entityData.set(VISIBLE_EQUIPMENT.get(slot),visible);
            }
        });
    }
    public CompoundTag appearance(){return appearance;}
    public ItemStack visibleEquipment(int slot){return entityData.get(VISIBLE_EQUIPMENT.get(slot));}
    public void capture(Mob mob,List<ItemStack> items){
        if(items.size()>inventory.getContainerSize())throw new IllegalArgumentException("Corpse inventory overflow");
        moveTo(mob.getX(),mob.getY(),mob.getZ(),mob.getYRot(),0);
        appearance=mob.saveWithoutId(new CompoundTag());appearance.putString("id",net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()).toString());
        for(String key:List.of("ForgeCaps","Inventory","Items","Passengers","Leash","Brain","Attributes","UUID","Pos","Motion","ActiveEffects"))appearance.remove(key);
        appearance.putFloat("Health",20);appearance.putShort("DeathTime",(short)0);appearance.putShort("HurtTime",(short)0);
        if(appearance.contains("ForgeData")){var data=appearance.getCompound("ForgeData");for(String key:List.copyOf(data.getAllKeys()))if(key.startsWith("gnfrontier_"))data.remove(key);}
        for(int i=0;i<items.size();i++)inventory.setItem(i,items.get(i).copy());
        setCustomName(Component.translatable("entity.gnfrontier.fallen_soldier.named",mob.getDisplayName()));
    }
    @Override protected void defineSynchedData(){for(var slot:VISIBLE_EQUIPMENT)entityData.define(slot,ItemStack.EMPTY);}
    @Override public boolean isPickable(){return !isRemoved();}
    @Override public boolean isPushable(){return false;}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){return false;}
    @Override public InteractionResult interact(Player player,InteractionHand hand){
        if(player instanceof ServerPlayer server&&!player.isSpectator())NetworkHooks.openScreen(server,this,buffer->buffer.writeVarInt(getId()));
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public AbstractContainerMenu createMenu(int id,Inventory playerInventory,Player player){return new CorpseMenu(id,playerInventory,this);}
    @Override public Component getDisplayName(){return getCustomName()!=null?getCustomName():Component.translatable("entity.gnfrontier.fallen_soldier");}
    @Override public void tick(){
        super.tick();
        if(!isNoGravity()){setDeltaMovement(getDeltaMovement().add(0,-.04,0));move(MoverType.SELF,getDeltaMovement());setDeltaMovement(getDeltaMovement().scale(.98));if(onGround())setDeltaMovement(0,0,0);}
        if(!level().isClientSide){
            if(!finiteAmmo&&WorldState.get((ServerLevel)level()).enabled&&NpcLoadoutData.catalog!=null){
                if(NpcEquipment.finiteAmmo(new net.minecraftforge.items.wrapper.InvWrapper(inventory),true))inventory.setChanged();
                finiteAmmo=true;
            }
            viewers.removeIf(menu->menu.owner.containerMenu!=menu||!menu.stillValid(menu.owner));
            viewers.forEach(CorpseMenu::searchTick);
            // Unlooted bodies are retained across saves; only empty bodies decay.
            emptyTicks=inventory.isEmpty()?emptyTicks+1:0;
            if(emptyTicks>=(NpcLoadoutData.catalog==null?1200:NpcLoadoutData.catalog.emptyTicks()))discard();
            if(getY()<level().getMinBuildHeight())setPos(getX(),level().getMinBuildHeight()+1,getZ());
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        tag.putBoolean("FiniteAmmo",finiteAmmo);
        tag.put("Appearance",appearance);tag.putInt("EmptyTicks",emptyTicks);ListTag items=new ListTag();
        for(int slot=0;slot<inventory.getContainerSize();slot++)if(!inventory.getItem(slot).isEmpty()){var item=NpcEquipment.saveStack(inventory.getItem(slot));item.putInt("Slot",slot);items.add(item);}tag.put("Items",items);
        ListTag knowledge=new ListTag();searches.forEach((player,state)->{CompoundTag row=state.save();row.putUUID("Player",player);knowledge.add(row);});tag.put("Searches",knowledge);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        finiteAmmo=tag.getBoolean("FiniteAmmo");
        appearance=tag.getCompound("Appearance");emptyTicks=tag.getInt("EmptyTicks");inventory.clearContent();
        var items=tag.getList("Items",Tag.TAG_COMPOUND);for(int i=0;i<items.size();i++){var item=items.getCompound(i);int slot=item.getInt("Slot");if(slot>=0&&slot<inventory.getContainerSize())inventory.setItem(slot,NpcEquipment.loadStack(item));}
        searches.clear();for(var row:tag.getList("Searches",Tag.TAG_COMPOUND))if(row instanceof CompoundTag state&&state.hasUUID("Player"))searches.put(state.getUUID("Player"),CorpseSearch.load(state));
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public void writeSpawnData(FriendlyByteBuf buffer){buffer.writeNbt(appearance);}
    @Override public void readSpawnData(FriendlyByteBuf buffer){var data=buffer.readNbt();appearance=data==null?new CompoundTag():data;}
}
