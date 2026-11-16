package local.gnfrontier;

import local.gnfrontier.core.GridTradePlanner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Read-only inventory view. Selection and each paid repair are validated on the server. */
public final class WeaponRepairMenu extends AbstractContainerMenu {
    public static final int REPAIR=100;
    private final Inventory inventory;
    private final ContainerData state=new SimpleContainerData(14);
    private ItemStack expected=ItemStack.EMPTY;
    private int lastInventoryChange=-1;
    public WeaponRepairMenu(int id,Inventory inv,FriendlyByteBuf ignored){this(id,inv);}
    public WeaponRepairMenu(int id,Inventory inv){
        super(Frontier.WEAPON_REPAIR_MENU.get(),id);inventory=inv;
        for(int i=0;i<36;i++)addSlot(new Slot(inv,i,-10000,-10000){
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean mayPickup(Player p){return false;}
            @Override public boolean isActive(){return false;}
        });
        state.set(0,-1);addDataSlots(state);
        if(!inv.player.level().isClientSide){
            for(int i=0;i<36;i++)if(GridTradePlanner.usable(i)&&WeaponRepairKit.isGun(inv.getItem(i))){select(i);break;}
            refresh();
        }
    }
    public int selected(){return state.get(0);}
    public WeaponRepairKit.Status status(){int code=state.get(1);return code>=0&&code<WeaponRepairKit.Status.values().length?WeaponRepairKit.Status.values()[code]:WeaponRepairKit.Status.UNSUPPORTED;}
    public int percent(){return state.get(2);}
    public int kitCount(){return state.get(3);}
    public int damage(){return value(4);}
    public int maximum(){return value(6);}
    public int nextDamage(){return value(8);}
    public int lastRestored(){return value(12);}
    private int value(int at){return (state.get(at)&65535)|((state.get(at+1)&65535)<<16);}
    private void value(int at,int value){state.set(at,value&65535);state.set(at+1,(value>>>16)&65535);}
    public ItemStack selectedGun(){return selected()>=0&&selected()<36?slots.get(selected()).getItem():ItemStack.EMPTY;}
    public boolean canRepair(){return selected()>=0&&maximum()>0&&damage()>0&&kitCount()>0&&status()!=WeaponRepairKit.Status.DISABLED&&status()!=WeaponRepairKit.Status.CHANGED;}
    private void select(int index){state.set(0,index);expected=inventory.getItem(index).copy();state.set(1,WeaponRepairKit.Status.READY.ordinal());value(12,0);}
    private void refresh(){
        state.set(2,PrototypeConfig.WEAPON_REPAIR_PERCENT.get());state.set(3,WeaponRepairKit.kits(inventory));
        ItemStack gun=selectedGun();boolean supported=WeaponRepairKit.supported(gun);
        value(4,supported?gun.getDamageValue():0);value(6,supported?gun.getMaxDamage():0);value(8,supported?WeaponRepairKit.plan(gun,percent()).after():0);
        if(!PrototypeConfig.WEAPON_REPAIR.get())state.set(1,WeaponRepairKit.Status.DISABLED.ordinal());
        lastInventoryChange=inventory.getTimesChanged();
    }
    @Override public boolean stillValid(Player player){return player==inventory.player&&player.isAlive()&&!player.isSpectator()
        &&(player.level().isClientSide||player instanceof net.minecraft.server.level.ServerPlayer p&&WeaponRepairKit.usable(p));}
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof net.minecraft.server.level.ServerPlayer p)||player!=inventory.player||player.containerMenu!=this||!stillValid(player))return false;
        if(button>=0&&button<36&&GridTradePlanner.usable(button)&&WeaponRepairKit.isGun(inventory.getItem(button)))select(button);
        else if(button==REPAIR){
            var result=WeaponRepairKit.repair(p,selected(),expected);state.set(1,result.status().ordinal());value(12,result.restored());
            if(result.status()==WeaponRepairKit.Status.REPAIRED)expected=selectedGun().copy();
        }else return false;
        refresh();broadcastChanges();return true;
    }
    @Override public void broadcastChanges(){
        if(!inventory.player.level().isClientSide&&(inventory.getTimesChanged()!=lastInventoryChange||inventory.player.tickCount%20==0))refresh();
        super.broadcastChanges();
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return false;}
}
