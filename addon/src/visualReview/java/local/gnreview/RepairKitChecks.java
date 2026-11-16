package local.gnreview;

import java.util.*;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.item.attachment.AttachmentType;
import local.gnfrontier.*;
import local.gnfrontier.core.Economy;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;

/** Native item-use, menu packets and complete NBT checks in the registered reusable world. */
public final class RepairKitChecks {
    private static final String KEY="gn_repair_kit_review";
    private static void owned(ServerPlayer p){if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r39_creation"))throw new IllegalStateException("Owned world required");}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static CompoundTag saved(ItemStack s){return s.save(new CompoundTag());}
    private static CompoundTag noWear(ItemStack s){var n=saved(s);n.getCompound("tag").remove("Damage");return n;}
    private static WeaponRepairMenu menu(ServerPlayer p){if(!(p.containerMenu instanceof WeaponRepairMenu m))throw new IllegalStateException("Real repair menu required");return m;}
    private static boolean quality(ItemStack gun)throws Exception{return (Boolean)Class.forName("com.github.leopoko.tacz_attributes_addon.data.GunAttributeData").getMethod("hasAddonData",ItemStack.class).invoke(null,gun);}
    private static ItemStack gun(String id,int ammo,boolean scope)throws Exception{
        var b=GunItemBuilder.create().setId(new ResourceLocation(id)).setCount(1).setAmmoCount(ammo).setAmmoInBarrel(true).setFireMode(FireMode.SEMI);
        if(scope)b.putAttachment(AttachmentType.SCOPE,new ResourceLocation("tacz","scope_acog_ta31"));
        var stack=b.build();
        if(scope)check(IGun.getIGunOrNull(stack).getAttachmentId(stack,AttachmentType.SCOPE).equals(new ResourceLocation("tacz","scope_acog_ta31")),"Actual native optic must be attached");
        Class.forName("com.github.leopoko.tacz_attributes_addon.random.RarityConstraintHelper").getMethod("generateStandard",ItemStack.class,net.minecraft.util.RandomSource.class).invoke(null,stack,net.minecraft.util.RandomSource.create(47120421));
        Class.forName("com.github.leopoko.tacz_attributes_addon.data.GunAttributeData").getMethod("setSealed",ItemStack.class,boolean.class).invoke(null,stack,true);
        check(quality(stack),"Native random quality must exist before testing");
        check(stack.isDamageableItem()&&stack.getMaxDamage()>0,"Native GunDB durability is required");stack.setDamageValue(stack.getMaxDamage()*3/4);return stack;
    }
    private static Map<String,Object> item(ItemStack stack)throws Exception{
        var gun=IGun.getIGunOrNull(stack);
        return Map.of("gunId",gun.getGunId(stack).toString(),"maximum",stack.getMaxDamage(),"damage",stack.getDamageValue(),"ammo",gun.getCurrentAmmoCount(stack),"chambered",gun.hasBulletInBarrel(stack),"scope",String.valueOf(gun.getAttachmentId(stack,AttachmentType.SCOPE)),"nativeQuality",quality(stack),"nbt",saved(stack).toString());
    }
    public static Map<String,Object> server(ServerPlayer p,String action,Properties settings)throws Exception{
        owned(p);var inv=p.getInventory();var n=p.getPersistentData().getCompound(KEY);
        if(action.equals("stage")){
            check(!p.getPersistentData().contains(KEY),"Restore previous review first");
            n=new CompoundTag();n.putInt("mode",p.gameMode.getGameModeForPlayer().getId());p.getPersistentData().put(KEY,n);
            p.setGameMode(GameType.SURVIVAL);inv.clearContent();inv.setItem(0,gun("tacz:m4a1",17,true));inv.setItem(9,gun("hamster:makarov",5,false));
            TradeService.trade(p,"market_repair",Economy.Direction.BUY,8);check(WeaponRepairKit.kits(inv)==8,"Eight actual purchased repair kits");
            inv.selected=1;p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(1));
            check(WeaponRepairKit.isKit(p.getMainHandItem()),"Purchased kit is held in an enabled hotbar slot");
            inv.setChanged();p.containerMenu.broadcastChanges();
            return Map.of("purchasedKits",8,"balance",WorldState.get(p.serverLevel()).account(p.getUUID()).balance,"rifle",item(inv.getItem(0)),"pistol",item(inv.getItem(9)));
        }
        check(p.getPersistentData().contains(KEY),"Fixture required");
        if(action.equals("attach")){
            check(!n.contains("rifle"),"Attachments are prepared before the baseline");inv.setItem(0,gun("tacz:m4a1",17,true));inv.setChanged();p.containerMenu.broadcastChanges();return Map.of("rifle",item(inv.getItem(0)),"kits",WeaponRepairKit.kits(inv));
        }
        if(action.equals("baseline")){
            n.put("rifle",saved(inv.getItem(0)));n.put("pistol",saved(inv.getItem(9)));n.put("inventory",inv.save(new ListTag()));
            return Map.of("rifle",item(inv.getItem(0)),"pistol",item(inv.getItem(9)),"kits",WeaponRepairKit.kits(inv));
        }
        if(action.equals("inspect")){
            check(noWear(inv.getItem(0)).equals(noWear(ItemStack.of(n.getCompound("rifle")))),"Rifle ammo/attachments/quality/capabilities must be unchanged");
            check(noWear(inv.getItem(9)).equals(noWear(ItemStack.of(n.getCompound("pistol")))),"Pistol metadata must be unchanged");
            int kits=WeaponRepairKit.kits(inv);int expectedKits=Integer.parseInt(settings.getProperty("pdaPage"));check(kits==expectedKits,"Expected kit count "+expectedKits+", got "+kits);
            if(settings.containsKey("step"))check(menu(p).selectedGun().getDamageValue()==Integer.parseInt(settings.getProperty("step")),"Expected real wear after repair");
            return Map.of("kits",kits,"rifle",item(inv.getItem(0)),"pistol",item(inv.getItem(9)),"menu",menu(p).getClass().getName(),"status",menu(p).status().name(),"metadataPreserved",true);
        }
        if(action.equals("guards")){
            var m=menu(p);List<String> passed=new ArrayList<>();ItemStack rifle=inv.getItem(0).copy();
            int kits=WeaponRepairKit.kits(inv);var whole=inv.save(new ListTag());
            for(ClickType type:ClickType.values())for(int at:new int[]{-999,0,1,9,35})((AbstractContainerMenu)m).clicked(at,0,type,p);
            check(whole.equals(inv.save(new ListTag())),"No inventory extraction through any click type");passed.add("all inventory clicks are read-only");
            check(!((AbstractContainerMenu)m).clickMenuButton(p,-1)&&!((AbstractContainerMenu)m).clickMenuButton(p,500),"Invalid menu buttons rejected");passed.add("invalid button packets rejected");
            var other=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(p.serverLevel());check(!((AbstractContainerMenu)m).clickMenuButton(other,100),"Wrong owner rejected");
            var stale=new WeaponRepairMenu(((AbstractContainerMenu)m).containerId,inv);check(!((AbstractContainerMenu)stale).clickMenuButton(p,100),"Inactive menu rejected");passed.add("owner and active menu checked");
            var sword=new ItemStack(Items.IRON_SWORD);sword.setDamageValue(100);inv.setItem(0,sword);check(WeaponRepairKit.repair(p,0,sword.copy()).status()==WeaponRepairKit.Status.UNSUPPORTED,"Vanilla tools rejected");passed.add("vanilla items rejected");
            var invalid=rifle.copy();invalid.getOrCreateTag().putString("GunId","gnfrontier:missing_review_gun");inv.setItem(0,invalid);check(WeaponRepairKit.repair(p,0,invalid.copy()).status()==WeaponRepairKit.Status.UNSUPPORTED,"Missing gun definition rejected");passed.add("unknown GunId rejected");
            var unbreakable=rifle.copy();unbreakable.getOrCreateTag().putBoolean("Unbreakable",true);inv.setItem(0,unbreakable);check(WeaponRepairKit.repair(p,0,unbreakable.copy()).status()==WeaponRepairKit.Status.UNSUPPORTED,"Non-damageable gun rejected");passed.add("non-damageable guns rejected");
            var swapped=rifle.copy();swapped.setDamageValue(200);inv.setItem(0,swapped);check(WeaponRepairKit.repair(p,0,rifle).status()==WeaponRepairKit.Status.CHANGED,"Stale gun snapshot rejected");passed.add("changed selection rejected");
            for(int at:new int[]{-1,5,8,36,40,100})check(WeaponRepairKit.repair(p,at,swapped.copy()).status()==WeaponRepairKit.Status.UNSUPPORTED,"Disabled/outside slot rejected");passed.add("disabled/offhand/outside slots rejected");
            boolean enabled=PrototypeConfig.WEAPON_REPAIR.get();try{PrototypeConfig.WEAPON_REPAIR.set(false);check(WeaponRepairKit.repair(p,0,swapped.copy()).status()==WeaponRepairKit.Status.DISABLED,"Disabled setting rejected");}finally{PrototypeConfig.WEAPON_REPAIR.set(enabled);}passed.add("disabled repair rejected");
            check(kits==WeaponRepairKit.kits(inv),"Rejected operations consumed no kit");
            p.setGameMode(GameType.CREATIVE);var result=WeaponRepairKit.repair(p,0,swapped.copy());check(result.status()==WeaponRepairKit.Status.REPAIRED&&kits==WeaponRepairKit.kits(inv),"Creative repair preserves kit count");p.setGameMode(GameType.SURVIVAL);passed.add("creative uses no consumable");
            inv.setItem(0,rifle);inv.setChanged();((AbstractContainerMenu)m).clickMenuButton(p,0);((AbstractContainerMenu)m).broadcastChanges();
            check(whole.equals(inv.save(new ListTag())),"Guard fixture restores every item exactly");
            return Map.of("passed",passed,"noKitSpentByRejectedActions",true,"inventoryRestoredExactly",true);
        }
        if(action.equals("no-kits")){
            check(!n.contains("stash"),"Only one kit stash");var stash=new ListTag();
            for(int i=0;i<36;i++)if(WeaponRepairKit.isKit(inv.getItem(i))){var row=new CompoundTag();row.putInt("slot",i);row.put("item",saved(inv.getItem(i)));stash.add(row);inv.setItem(i,ItemStack.EMPTY);}
            n.put("stash",stash);inv.getItem(9).setDamageValue(200);inv.setChanged();((AbstractContainerMenu)menu(p)).clickMenuButton(p,9);((AbstractContainerMenu)menu(p)).broadcastChanges();return Map.of("kits",WeaponRepairKit.kits(inv),"pistolDamage",200);
        }
        if(action.equals("restore")){
            for(Tag t:n.getList("stash",Tag.TAG_COMPOUND)){var row=(CompoundTag)t;inv.setItem(row.getInt("slot"),ItemStack.of(row.getCompound("item")));}
            p.setGameMode(GameType.byId(n.getInt("mode")));p.getPersistentData().remove(KEY);inv.setChanged();p.containerMenu.broadcastChanges();return Map.of("fixtureRemoved",true,"originalGameModeRestored",true);
        }
        throw new IllegalArgumentException(action);
    }
    public static Map<String,Object> client(String action,Properties settings){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals("GN_Review_r39_creation"))throw new IllegalStateException("Owned client required");
        if(action.equals("open")){
            mc.setScreen(null);var result=mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return Map.of("nativeItemUse",result.toString());
        }
        if(!(mc.player.containerMenu instanceof WeaponRepairMenu m)||!(mc.screen instanceof local.gnfrontier.client.WeaponRepairScreen))throw new IllegalStateException("Native repair screen required");
        if(action.equals("repair"))mc.gameMode.handleInventoryButtonClick(((AbstractContainerMenu)m).containerId,WeaponRepairMenu.REPAIR);
        if(action.equals("select"))mc.gameMode.handleInventoryButtonClick(((AbstractContainerMenu)m).containerId,Integer.parseInt(settings.getProperty("pdaPage")));
        var out=new LinkedHashMap<String,Object>();out.put("screen",mc.screen.getClass().getName());out.put("selected",m.selected());out.put("damage",m.damage());out.put("maximum",m.maximum());out.put("kitCount",m.kitCount());out.put("status",m.status().name());out.put("canRepair",m.canRepair());out.put("allSlotsInactive",((AbstractContainerMenu)m).slots.stream().noneMatch(s->s.isActive()));
        if(action.equals("close")){mc.player.closeContainer();mc.setScreen(null);out.put("closed",true);}return out;
    }
}
