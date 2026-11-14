package local.gnreview;

import java.nio.file.Path;
import java.util.*;
import java.lang.reflect.*;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** Integration checks on real transformed classes in the disposable r24 world. */
public final class ExpertChecks {
    static Class<?> c(String name)throws Exception{return Class.forName("com.ori.taczplus."+name);}
    static Object call(Object receiver,String name,Object... args)throws Exception{
        Class<?> type=receiver instanceof Class<?> t?t:receiver.getClass();
        for(Method m:type.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
            try{return m.invoke(receiver instanceof Class<?>?null:receiver,args);}catch(IllegalArgumentException mismatch){}
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    static final List<String> checks=new ArrayList<>();
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);}
    static ResourceLocation id(String s){return new ResourceLocation(s);}
    static Object type(String key)throws Exception{return c("bullet.BulletTypes").getField(key).get(null);}
    static ItemStack magazine(Object spec,int count)throws Exception{
        Object rounds=c("bullet.RoundList").getConstructor().newInstance();call(rounds,"push",type("FMJ"),count);
        Object kind=Enum.valueOf((Class)c("magazine.MagazineKind"),"STANDARD");
        return (ItemStack)call(c("magazine.MagazineItem"),"create",call(spec,"caliber"),call(spec,"primaryFamily"),kind,call(spec,"baseCapacity"),rounds);
    }
    static int rounds(ItemStack s)throws Exception{return (Integer)call(call(c("magazine.MagazineItem"),"rounds",s),"total");}
    static ModernKineticGunScriptAPI api(ServerPlayer p,ItemStack gun){
        var api=new ModernKineticGunScriptAPI();api.setShooter(p);api.setItemStack(gun);api.setDataHolder(new ShooterDataHolder());return api;
    }
    public static Map<String,Object> verify(ServerPlayer p,Path evidence)throws Exception{
        if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r24_expert"))throw new IllegalStateException("Owned world required");
        checks.clear();p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.onUpdateAbilities();
        Class<?> cfg=c("Config");
        for(String yes:List.of("magazinesEnabled","bulletTypes"))check((Boolean)call(cfg,yes),yes);
        for(String no:List.of("itemSizes","weightEnabled","jammingEnabled","banditSpawning","unSpawning","nightRaidsEnabled","contractsEnabled","airdrops","blackMarketEnabled","cartelEnabled"))check(!(Boolean)call(cfg,no),"disabled "+no);
        check(ModList.get().isLoaded("gundb")&&ModList.get().isLoaded("petiteinventory")&&ModList.get().isLoaded("tinyinv")&&ModList.get().isLoaded("lootr"),"Existing wear, footprint, hotbar and personal loot mods retained");
        check(!ModList.get().isLoaded("tacz_x_guns_lights_addon"),"Redundant muzzle/tracer light addon absent");
        var registry=p.level().registryAccess().registryOrThrow(Registries.BIOME);
        for(String name:List.of("abandoned_town","abandoned_warehouse","bandit_camp","black_market","bunker","gas_station","roadblock","un_base","underground_tunnels","watchtower")){
            var tag=TagKey.create(Registries.BIOME,id("taczexpert:has_structure/"+name));
            check(registry.getTag(tag).isPresent()&&registry.getTag(tag).orElseThrow().size()==0,"No added worldgen/communal crates: "+name);
        }
        List<Map<String,Object>> reloads=new ArrayList<>();
        for(String name:List.of("tacz:m4a1","tacz:ak47","tacz:m9a4","tacz:m1911","tacz:glock_17")){
            p.getInventory().clearContent();ItemStack gun=GunItemBuilder.create().setId(id(name)).build();IGun ig=IGun.getIGunOrNull(gun);
            check(TimelessAPI.getCommonGunIndex(id(name)).isPresent(),name+" gun data");
            ig.setCurrentAmmoCount(gun,5);p.getInventory().setItem(0,gun);p.getInventory().selected=0;
            Object spec=call(c("magazine.Magazines"),"spec",gun);check(spec!=null,name+" magazine family");
            int capacity=(Integer)call(spec,"baseCapacity");ItemStack mag=magazine(spec,capacity);
            check(p.getInventory().add(mag),name+" Petite insertion");
            ItemStack loose=new ItemStack(ForgeRegistries.ITEMS.getValue(id("tacz:ammo")),17);loose.getOrCreateTag().putString("AmmoId",String.valueOf(call(spec,"caliber")));p.getInventory().add(loose);
            call(c("bullet.BulletTypeData"),"setGunHasMag",gun,true);
            var api=api(p,gun);check(api.hasAmmoToConsume(),name+" native reload sees real magazine");
            int taken=api.consumeAmmoFromPlayer(api.getNeededAmmoAmount());check(taken==capacity,name+" reload extracted magazine capacity");
            api.putAmmoInMagazine(taken);check(ig.getCurrentAmmoCount(gun)==capacity,name+" native gun received exact rounds");
            int returned=0,looseCount=0,magCount=0;
            for(ItemStack s:p.getInventory().items){if(c("magazine.MagazineItem").isInstance(s.getItem())){returned+=rounds(s);magCount+=s.getCount();}if(s.is(ForgeRegistries.ITEMS.getValue(id("tacz:ammo"))))looseCount+=s.getCount();}
            check(returned==5&&magCount==1&&looseCount==17,name+" old magazine returned; loose ammo unchanged");
            call(c("magazine.Magazines"),"unload",p,gun,ig,spec);
            int sum=0,count=0;for(ItemStack s:p.getInventory().items)if(c("magazine.MagazineItem").isInstance(s.getItem())){sum+=rounds(s);count+=s.getCount();}
            check(sum==capacity+5&&count==2&&ig.getCurrentAmmoCount(gun)==0,name+" unload conserved both magazines and every round");
            p.getInventory().clearContent();p.getInventory().setItem(0,gun);check(!api.hasAmmoToConsume(),name+" empty inventory cannot reload");
            ItemStack other=GunItemBuilder.create().setId(id(name.equals("tacz:ak47")?"tacz:m4a1":"tacz:ak47")).build();Object otherSpec=call(c("magazine.Magazines"),"spec",other);p.getInventory().add(magazine(otherSpec,10));
            check(!api.hasAmmoToConsume(),name+" incompatible magazine rejected");
            reloads.add(Map.of("gun",name,"capacity",capacity,"conserved",capacity+5));
        }
        p.getInventory().clearContent();ItemStack gun=GunItemBuilder.create().setId(id("tacz:m4a1")).build();IGun ig=IGun.getIGunOrNull(gun);p.getInventory().setItem(0,gun);
        Object spec=call(c("magazine.Magazines"),"spec",gun);ItemStack mag=magazine(spec,0);p.getInventory().setItem(9,mag);
        ItemStack ammo=new ItemStack(ForgeRegistries.ITEMS.getValue(id("tacz:ammo")),40);ammo.getOrCreateTag().putString("AmmoId","tacz:556x45");
        var slot=p.inventoryMenu.slots.stream().filter(s->s.container==p.getInventory()&&s.getContainerSlot()==9).findFirst().orElseThrow();
        boolean filled=mag.getItem().overrideOtherStackedOnMe(mag,ammo,slot,net.minecraft.world.inventory.ClickAction.SECONDARY,p,net.minecraft.world.entity.SlotAccess.NULL);
        check(filled&&rounds(mag)==30&&ammo.getCount()==10,"Native right-click filling consumes exactly 30/40 loose rounds");
        check(!(Boolean)call(c("magazine.Magazines"),"appliesTo",p,GunItemBuilder.create().setId(id("tacz:m870")).build()),"Shell-fed shotgun keeps native loose reload");
        ig.setCurrentAmmoCount(gun,30);Object list=c("bullet.RoundList").getConstructor().newInstance();call(list,"push",type("FMJ"),30);call(c("bullet.BulletTypeData"),"setGunRounds",gun,list);call(c("bullet.BulletTypeData"),"setGunHasMag",gun,true);
        float heatBefore=(Float)call(c("ballistics.BarrelHeat"),"get",gun,p.level());
        var fire=new com.tacz.guns.api.event.common.GunFireEvent(p,gun,net.minecraftforge.fml.LogicalSide.SERVER);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(fire);
        float heatAfter=(Float)call(c("ballistics.BarrelHeat"),"get",gun,p.level());
        check(!fire.isCanceled()&&heatAfter>heatBefore,"Real Forge shot event advances Expert heat without a second jam system");
        ItemStack vest=new ItemStack(ForgeRegistries.ITEMS.getValue(id("taczexpert:plate_carrier")));
        Object ceramic=Enum.valueOf((Class)c("armor.PlateType"),"CERAMIC");
        Object front=Enum.valueOf((Class)c("armor.VestItem$Side"),"FRONT"),back=Enum.valueOf((Class)c("armor.VestItem$Side"),"BACK");
        call(c("armor.VestItem"),"setPlate",vest,front,ceramic,0);call(c("armor.VestItem"),"setPlate",vest,back,ceramic,0);
        var target=new net.minecraft.world.entity.monster.Zombie(p.serverLevel());target.setPos(p.getX()+8,p.getY(),p.getZ());target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,vest);
        var arrow=new net.minecraft.world.entity.projectile.Arrow(p.serverLevel(),p);arrow.setPos(target.getX(),target.getY()+1.0,target.getZ()+1.0);arrow.setDeltaMovement(0,0,-1);
        var hit=new com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre(arrow,target,p,id("tacz:m4a1"),id("tacz:m4a1"),20,null,false,1,net.minecraftforge.fml.LogicalSide.SERVER);
        call(c("armor.BallisticArmor"),"onBulletHit",hit);
        check(hit.getBaseAmount()>0&&hit.getBaseAmount()<20,"Expert ballistic armor hook reduces a torso hit");
        check((Integer)call(c("armor.VestItem"),"plateDamage",vest,front)+(Integer)call(c("armor.VestItem"),"plateDamage",vest,back)>0,"Ballistic hit wears the fitted ceramic plate");
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,vest);target.discard();arrow.discard();
        p.inventoryMenu.broadcastChanges();p.inventoryMenu.sendAllDataToRemote();
        return Map.of("checks",checks.size(),"passed",List.copyOf(checks),"reloads",reloads,"survival",true,"fixtureInventory",p.getInventory().items.stream().filter(s->!s.isEmpty()).map(ItemStack::toString).toList());
    }
    public static Map<String,Object> client()throws Exception{
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals("GN_Review_r24_expert"))throw new IllegalStateException("Owned world required");
        List<Map<String,Object>> keys=new ArrayList<>();
        for(var key:mc.options.keyMappings)if(key.getName().startsWith("key.taczexpert.")){
            var conflicts=Arrays.stream(mc.options.keyMappings).filter(k->k!=key&&!key.isUnbound()&&k.getKey().equals(key.getKey())).map(net.minecraft.client.KeyMapping::getName).toList();

            keys.add(Map.of("name",key.getName(),"key",key.getKey().getName(),"label",net.minecraft.network.chat.Component.translatable(key.getName()).getString(),"conflicts",conflicts));
        }
        Object hotbar=Class.forName("nuparu.tinyinv.config.ServerConfig").getField("hotbarSlots").get(null);
        return Map.of("keys",keys,"survival",!mc.player.getAbilities().instabuild,"menuSlots",mc.player.inventoryMenu.slots.size(),"configuredHotbarSlots",call(hotbar,"get"),"held",mc.player.getMainHandItem().toString(),"magazineName",net.minecraft.network.chat.Component.translatable("item.taczexpert.magazine").getString());
    }
    public static Map<String,Object> refresh()throws Exception{
        var mc=net.minecraft.client.Minecraft.getInstance();client();mc.options.load();net.minecraft.client.KeyMapping.resetMapping();Class.forName("com.anomalyzone.settings.HudSettings").getMethod("load").invoke(null);mc.setScreen(null);return client();
    }
    public static Map<String,Object> explosives(ServerPlayer p,Path evidence)throws Exception{
        if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r24_expert"))throw new IllegalStateException("Owned world required");
        check((Integer)call(c("Config"),"fragDensity")==0&&((Double)call(c("Config"),"claymoreFragments"))==0.0,"Expert duplicate fragmentation disabled");
        var grenade=(net.minecraft.world.entity.projectile.Projectile)ForgeRegistries.ENTITY_TYPES.getValue(id("taczexpert:grenade")).create(p.serverLevel());
        grenade.setOwner(p);grenade.setPos(p.getX(),p.getY()+48,p.getZ());
        List<String> spawned=new ArrayList<>();var active=new java.util.concurrent.atomic.AtomicBoolean(true);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,net.minecraftforge.event.entity.EntityJoinLevelEvent.class,e->{if(active.get())spawned.add(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getEntity().getType())));});
        try{call(c("grenade.GrenadeEffects"),"frag",p.serverLevel(),grenade);}finally{active.set(false);grenade.discard();}
        long shards=spawned.stream().filter(s->s.startsWith("shrapnelexplosionsmod:")).count();check(shards==50,"Expert grenade produces exactly one configured batch of 50 Shrapnel projectiles: "+spawned);
        return Map.of("expertFragDensity",0,"expertClaymoreFragments",0,"shrapnelBatch",shards,"spawned",spawned);
    }
}
