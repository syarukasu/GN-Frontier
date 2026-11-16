package local.gnfrontier;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import local.gnfrontier.core.GridTradePlanner;
import local.gnfrontier.core.WeaponRepair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/** Pack-owned repair behavior for the already sold kit. Third-party items and JARs stay original. */
public final class WeaponRepairKit {
    public static final ResourceLocation KIT=new ResourceLocation("hbm_m","gun_kit_2");
    public enum Status{READY,REPAIRED,NO_KIT,FULL,UNSUPPORTED,CHANGED,DISABLED}
    public record Result(Status status,int restored){}
    public static boolean isKit(ItemStack stack){return !stack.isEmpty()&&KIT.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));}
    public static boolean isGun(ItemStack stack){return !stack.isEmpty()&&stack.getCount()==1&&IGun.getIGunOrNull(stack)!=null;}
    public static int kits(Inventory inventory){int count=0;for(int i=0;i<36;i++)if(GridTradePlanner.usable(i)&&isKit(inventory.getItem(i)))count+=inventory.getItem(i).getCount();return count;}
    public static boolean supported(ItemStack stack){
        var gun=IGun.getIGunOrNull(stack);
        return isGun(stack)&&gun!=null&&TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).isPresent()
            &&stack.isDamageableItem()&&stack.getMaxDamage()>0&&stack.getDamageValue()>=0;
    }
    public static WeaponRepair.Plan plan(ItemStack stack,int percent){return WeaponRepair.plan(stack.getMaxDamage(),stack.getDamageValue(),percent);}
    public static boolean usable(ServerPlayer p){return p.isAlive()&&!p.isSpectator()&&WorldState.get(p.serverLevel()).enabled&&PrototypeConfig.WEAPON_REPAIR.get();}
    private static CompoundTag withoutWear(ItemStack stack){var n=stack.save(new CompoundTag());n.getCompound("tag").remove("Damage");return n;}
    public static Result repair(ServerPlayer player,int index,ItemStack expected){
        if(!player.getServer().isSameThread())throw new IllegalStateException("Repair must run on the server thread");
        if(!usable(player))return new Result(Status.DISABLED,0);
        var inv=player.getInventory();
        if(!GridTradePlanner.usable(index))return new Result(Status.UNSUPPORTED,0);
        var gun=inv.getItem(index);
        if(!ItemStack.matches(gun,expected))return new Result(Status.CHANGED,0);
        if(!supported(gun))return new Result(Status.UNSUPPORTED,0);
        var plan=plan(gun,PrototypeConfig.WEAPON_REPAIR_PERCENT.get());
        if(plan.restored()==0)return new Result(Status.FULL,0);
        int kit=-1;for(int i=0;i<36;i++)if(GridTradePlanner.usable(i)&&isKit(inv.getItem(i))){kit=i;break;}
        if(kit<0)return new Result(Status.NO_KIT,0);
        ItemStack beforeGun=gun.copy(),beforeKit=inv.getItem(kit).copy(),afterGun=gun.copy(),afterKit=beforeKit.copy();
        afterGun.setDamageValue(plan.after());
        if(!withoutWear(beforeGun).equals(withoutWear(afterGun)))throw new IllegalStateException("Repair changed unrelated gun data");
        if(!player.isCreative())afterKit.shrink(1);
        try{
            inv.setItem(index,afterGun);inv.setItem(kit,afterKit);inv.setChanged();
        }catch(RuntimeException error){inv.setItem(index,beforeGun);inv.setItem(kit,beforeKit);throw error;}
        player.level().playSound(null,player.blockPosition(),SoundEvents.ANVIL_USE,SoundSource.PLAYERS,.35f,1.35f);
        return new Result(Status.REPAIRED,plan.restored());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void use(PlayerInteractEvent.RightClickItem event){
        if(event.getHand()!=InteractionHand.MAIN_HAND||!isKit(event.getItemStack())||event.getEntity().isSpectator())return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        if(event.getEntity() instanceof ServerPlayer player){
            if(!usable(player)){player.displayClientMessage(Component.translatable("screen.gnfrontier.repair.disabled"),true);return;}
            NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,p)->new WeaponRepairMenu(id,inv),Component.translatable("screen.gnfrontier.repair.title")));
        }
    }
}
