package local.gnfrontier;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.*;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.*;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class NpcEquipment {
    public static final String ISSUED="gnfrontier_equipment_v1",FRESH="gnfrontier_fresh_unit",SNAPSHOT="gnfrontier_death_inventory";
    public static boolean candidate(Mob mob){
        var id=ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());if(id==null)return false;
        return mob instanceof Pillager||id.getNamespace().equals("simpleenemymod")||
            (id.getNamespace().equals("tacz_sewv")&&(id.getPath().startsWith("ru_")||id.getPath().startsWith("us_")||id.getPath().equals("pmc_commander")));
    }
    public static boolean eligible(Mob mob){return FactionHostility.force(mob)||(mob instanceof Pillager&&mob.getPersistentData().getBoolean("gnfrontier_bandit"));}
    public static boolean managed(Entity entity){return entity instanceof Mob mob&&eligible(mob)&&mob.getPersistentData().getBoolean(ISSUED)&&RespawnRules.enabled(mob.level());}
    public static IItemHandler inventory(Mob mob){return mob.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(()->new IllegalStateException("Missing NPC inventory: "+mob.getType()));}
    public static void markFresh(Mob mob){mob.getPersistentData().putBoolean(FRESH,true);}
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent e){
        if(!(e.getEntity() instanceof Mob mob)||mob.level().isClientSide||!eligible(mob)||!RespawnRules.enabled(mob.level()))return;
        if(mob.isAlive())mob.getPersistentData().remove(SNAPSHOT);
        if(!mob.getPersistentData().getBoolean(ISSUED)||!mob.getPersistentData().getBoolean(NpcGunQuality.INITIALIZED))prepare(mob);
    }
    private static ItemStack item(String id,int count){var key=new ResourceLocation(id);if(!ForgeRegistries.ITEMS.containsKey(key))throw new IllegalStateException("Missing issued item "+id);return new ItemStack(ForgeRegistries.ITEMS.getValue(key),count);}
    public static void prepare(Mob mob){
        if(!eligible(mob))return;
        if(mob.getPersistentData().getBoolean(ISSUED)){NpcGunQuality.prepare(mob);return;}
        var data=NpcLoadoutData.catalog;if(data==null)throw new IllegalStateException("NPC catalog not loaded");
        var inv=inventory(mob);var id=ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()).toString();int faction=FactionHostility.faction(mob);
        boolean regular=faction==0||faction==1;
        boolean fresh=mob.getPersistentData().getBoolean(FRESH);
        int reserve=-1;
        if(regular){
            var army=faction==0?data.east():data.west();
            // Existing units retain their actual gun/NBT; fresh regular infantry receive the new issue pool.
            if(fresh&&(id.equals("simpleenemymod:ruunit")||id.equals("simpleenemymod:usunit"))){
                int ticket=mob.getRandom().nextInt(army.weapons().stream().mapToInt(NpcLoadoutData.Weapon::weight).sum());
                var selected=army.weapons().get(0);for(var w:army.weapons()){ticket-=w.weight();if(ticket<0){selected=w;break;}}
                var gunId=new ResourceLocation(selected.id());
                if(TimelessAPI.getCommonGunIndex(gunId).isEmpty())throw new IllegalStateException("Missing issued TaCZ gun "+gunId);
                var builder=GunItemBuilder.create().setId(gunId).setCount(1).setAmmoCount(selected.loaded()).setFireMode(FireMode.valueOf(selected.mode()));
                if(!selected.scope().isEmpty())builder.putAttachment(AttachmentType.SCOPE,new ResourceLocation(selected.scope()));
                mob.setItemSlot(EquipmentSlot.MAINHAND,builder.build());reserve=selected.reserve();
                IGunOperator.fromLivingEntity(mob).draw(mob::getMainHandItem);
            }
            // Real ballistic items, not vanilla armor-point or toughness bonuses.
            if(fresh||mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty())mob.setItemSlot(EquipmentSlot.HEAD,item(army.helmet(),1));
            if(fresh||mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty())mob.setItemSlot(EquipmentSlot.CHEST,item(army.vest(),1));
            for(var supply:id.contains("medic")?data.medic():data.supplies())carry(inv,item(supply.id(),supply.count()));
            for(var slot:List.of(EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND)){
                ItemStack stack=mob.getItemBySlot(slot);var gun=IGun.getIGunOrNull(stack);if(gun==null)continue;
                // Gun Durability initializes Damage on NBT load; initialize the same zero value at issue.
                if(stack.isDamageableItem())stack.setDamageValue(stack.getDamageValue());
                var index=TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElseThrow();
                // Native TaCZ reloads use ITEM_HANDLER when DummyAmmo is absent.
                if(stack.hasTag()){stack.getTag().remove("DummyAmmo");stack.getTag().remove("MaxDummyAmmo");}
                int rounds=slot==EquipmentSlot.MAINHAND&&reserve>=0?reserve:index.getGunData().getAmmoAmount()*data.supportMagazines();
                while(rounds>0){int count=Math.min(64,rounds);carry(inv,AmmoItemBuilder.create().setId(index.getGunData().getAmmoId()).setCount(count).build());rounds-=count;}
            }
        }
        // Preserve the established scrap/medical/money pool, but roll it once while alive.
        // Recruited PMCs keep the inventory supplied by their owner without a free issue.
        boolean ownedPmc=faction==2&&mob.saveWithoutId(new CompoundTag()).hasUUID("OwnerUUID");
        if(!ownedPmc&&mob.level() instanceof ServerLevel level){
            var params=new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY,mob)
                .withParameter(LootContextParams.ORIGIN,mob.position()).withParameter(LootContextParams.DAMAGE_SOURCE,mob.damageSources().generic()).create(LootContextParamSets.ENTITY);
            for(var carried:level.getServer().getLootData().getLootTable(new ResourceLocation(data.carriedLoot())).getRandomItems(params))carry(inv,carried);
        }
        // PMC keeps its native equipment. Bandits keep their actual equipment.
        // Native PMC issue can contain 240 rounds in one offhand stack. Split without losing rounds.
        for(int slot=0;slot<inv.getSlots();slot++){
            ItemStack stack=inv.getStackInSlot(slot);
            if(stack.isDamageableItem())stack.setDamageValue(stack.getDamageValue());
            if(stack.getCount()>64){int excess=stack.getCount()-64;stack.setCount(64);ItemStack remainder=carry(inv,stack.copyWithCount(excess));stack.grow(remainder.getCount());}
        }
        NpcGunQuality.prepare(mob);
        mob.getPersistentData().putBoolean(ISSUED,true);mob.getPersistentData().remove(FRESH);
    }
    private static ItemStack carry(IItemHandler inventory,ItemStack stack){
        for(int slot=6;slot<inventory.getSlots()&&!stack.isEmpty();slot++)stack=inventory.insertItem(slot,stack,false);
        // Existing inventories may already be full. Keep their possessions and omit excess issue.
        return stack;
    }
    public static List<ItemStack> contents(Mob mob){
        List<ItemStack> items=new ArrayList<>();for(var slot:NpcInventory.EQUIPMENT)items.add(mob.getItemBySlot(slot).copy());
        // Combined Arms temporarily replaces the hands while operating a drone or throwing.
        // Recover the stowed real equipment instead of the animation's temporary item.
        var state=mob.getPersistentData();
        for(String[] stash:List.of(new String[]{"sewv:grenade_stash","sewv:grenade_stash_main","sewv:grenade_stash_off"},new String[]{"sewv_drone_hand_stash","sewv_drone_stash_main","sewv_drone_stash_off"}))
            if(state.getBoolean(stash[0]))for(int hand=0;hand<2;hand++)items.set(hand,ItemStack.of(state.getCompound(stash[hand+1])));
        var inv=inventory(mob);for(int slot=6;slot<inv.getSlots();slot++)items.add(inv.getStackInSlot(slot).copy());return items;
    }
    static CompoundTag saveStack(ItemStack stack){
        var tag=stack.save(new CompoundTag());
        // Vanilla Count is a signed byte; preserve oversized stacks supplied by other mods as well.
        if(stack.getCount()>127){tag.putByte("Count",(byte)1);tag.putInt("GNCount",stack.getCount());}return tag;
    }
    static ItemStack loadStack(CompoundTag tag){var stack=ItemStack.of(tag);if(tag.contains("GNCount",Tag.TAG_INT))stack.setCount(tag.getInt("GNCount"));return stack;}
    private static ListTag save(List<ItemStack> items){ListTag list=new ListTag();for(var stack:items)list.add(saveStack(stack));return list;}
    private static List<ItemStack> load(ListTag list){List<ItemStack> items=new ArrayList<>();for(int i=0;i<list.size();i++)items.add(loadStack(list.getCompound(i)));return items;}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void beforeDeath(LivingDeathEvent e){
        if(e.isCanceled()||!(e.getEntity() instanceof Mob mob)||mob.level().isClientSide||!eligible(mob)||!RespawnRules.enabled(mob.level()))return;
        prepare(mob);mob.getPersistentData().put(SNAPSHOT,save(contents(mob)));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void drops(LivingDropsEvent e){
        if(e.isCanceled()||!managed(e.getEntity())||!(e.getEntity() instanceof Mob mob)||!(mob.level() instanceof ServerLevel level))return;
        if(!level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT))return;
        var tag=mob.getPersistentData();List<ItemStack> actual=tag.contains(SNAPSHOT)?load(tag.getList(SNAPSHOT,Tag.TAG_COMPOUND)):contents(mob);
        FallenSoldier corpse=new FallenSoldier(Frontier.FALLEN_SOLDIER.get(),level);corpse.capture(mob,actual);
        e.getDrops().clear();
        if(level.addFreshEntity(corpse))e.setCanceled(true);
        else for(var stack:actual)if(!stack.isEmpty())e.getDrops().add(new ItemEntity(level,mob.getX(),mob.getY(),mob.getZ(),stack.copy()));
        var inv=inventory(mob);for(int slot=6;slot<inv.getSlots();slot++)inv.extractItem(slot,Integer.MAX_VALUE,false);
        for(var slot:NpcInventory.EQUIPMENT)mob.setItemSlot(slot,ItemStack.EMPTY);
        tag.remove(SNAPSHOT);
    }
}
