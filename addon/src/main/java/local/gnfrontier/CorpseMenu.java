package local.gnfrontier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative search and shared looting. Hidden stacks are never sent to the client. */
public final class CorpseMenu extends AbstractContainerMenu {
    public final FallenSoldier corpse;
    public final Player owner;
    private final Container contents;
    private final CorpseSearch search;
    private int section=-1;
    private final ContainerData state=new SimpleContainerData(8);
    public CorpseMenu(int id,Inventory inventory,FriendlyByteBuf data){
        this(id,inventory,inventory.player.level().getEntity(data.readVarInt()) instanceof FallenSoldier body?body:null);
    }
    public CorpseMenu(int id,Inventory inventory,FallenSoldier body){
        super(Frontier.CORPSE_MENU.get(),id);corpse=body;owner=inventory.player;
        contents=owner.level().isClientSide?new SimpleContainer(54):java.util.Objects.requireNonNull(body).inventory;
        search=owner.level().isClientSide?null:body.search(owner.getUUID());
        for(int slot=0;slot<54;slot++){
            final int index=slot;int x,y;
            if(slot<6){int column=switch(slot){case 0->0;case 1->1;case 5->2;case 4->3;case 3->4;default->5;};x=186+column*25;y=40;}
            else if(slot<10){x=186+(slot-6)*18;y=80;}
            else{x=186+(slot-10)%8*18;y=120+(slot-10)/8*18;}
            addSlot(new Slot(contents,slot,x,y){
                @Override public boolean mayPlace(ItemStack stack){return false;}
                @Override public boolean mayPickup(Player player){return known(index)&&stillValid(player);}
                @Override public ItemStack getItem(){return known(index)?super.getItem():ItemStack.EMPTY;}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,10+col*18,140+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,10+col*18,202));
        state.set(4,-1);state.set(5,-1);addDataSlots(state);
        if(search!=null){body.watch(this);syncSearch();}
    }
    public boolean known(int slot){
        if(slot<6)return true;
        if(search!=null)return search.known(slot);
        return (state.get(slot/16)&(1<<(slot%16)))!=0;
    }
    public int activeSection(){return state.get(4);}
    public int pendingSlot(){return state.get(5);}
    public int elapsed(){return state.get(6);}
    public int duration(){return Math.max(1,state.get(7));}
    public boolean completed(int group){for(int i=CorpseSearch.start(group);i<CorpseSearch.end(group);i++)if(!known(i))return false;return true;}
    private int durationFor(int slot){return contents.getItem(slot).isEmpty()?PrototypeConfig.CORPSE_EMPTY_SEARCH_TICKS.get():PrototypeConfig.CORPSE_SEARCH_TICKS.get();}
    private void syncSearch(){
        for(int i=0;i<4;i++)state.set(i,(int)(search.revealed()>>>(i*16))&65535);
        state.set(4,section);state.set(5,search.pending());state.set(6,search.elapsed());state.set(7,search.pending()<0?1:durationFor(search.pending()));
    }
    @Override public boolean clickMenuButton(Player player,int button){
        if(search==null||player!=owner||!stillValid(player)||button<0||button>1)return false;
        if(section==button)section=-1;
        else{section=button;int next=search.next(section);if(next<0)section=-1;else search.focus(next);}
        syncSearch();broadcastChanges();return true;
    }
    public void searchTick(){
        if(search==null||section<0||!stillValid(owner)||owner.containerMenu!=this)return;
        int next=search.next(section);
        if(next<0){section=-1;syncSearch();return;}
        search.focus(next);
        if(search.advance(durationFor(next))&&search.next(section)<0)section=-1;
        syncSearch();
    }
    @Override public boolean stillValid(Player player){return corpse!=null&&!corpse.isRemoved()&&player.isAlive()&&!player.isSpectator()&&player.distanceToSqr(corpse)<=64;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(!stillValid(player)||player!=owner)return;
        if(slot>=0&&slot<54&&!known(slot))return;
        super.clicked(slot,button,type,player);
    }
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return slot.index>=54||known(slot.index);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player)||player!=owner||index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(player))return ItemStack.EMPTY;
        ItemStack current=slot.getItem(),copy=current.copy();
        if(index<54){if(!known(index)||!moveItemStackTo(current,54,90,true))return ItemStack.EMPTY;}
        else if(index<81){if(!moveItemStackTo(current,81,90,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(current,54,81,false))return ItemStack.EMPTY;
        if(current.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        if(current.getCount()==copy.getCount())return ItemStack.EMPTY;
        slot.onTake(player,current);return copy;
    }
    @Override public void removed(Player player){super.removed(player);section=-1;if(corpse!=null)corpse.unwatch(this);}
}
