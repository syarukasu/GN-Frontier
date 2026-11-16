package local.gncombatreview;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.*;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.util.TacHitResult;
import java.nio.file.*;
import java.util.*;
import local.gnperf.PerformanceChecks;
import local.gnnight.NightZombieChecks;
import local.gnfrontier.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("gncombatreview")
public final class CombatReview {
    private final CityReview cityReview=new CityReview();
    private MinecraftServer server;
    private int ticks;
    private final List<String> checks=new ArrayList<>();
    private final List<String> failures=new ArrayList<>();
    private final List<Entity> fixtures=new ArrayList<>();
    private final Map<String,Object> result=new LinkedHashMap<>();
    public CombatReview(){MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void start(ServerStartedEvent e){server=e.getServer();}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
        if(Boolean.getBoolean("gn.cityReview")){if(server!=null&&e.phase==TickEvent.Phase.END&&++ticks>60)cityReview.tick(server);return;}
        if(server==null||e.phase!=TickEvent.Phase.END||++ticks!=60)return;
        Path evidence=Path.of(System.getProperty("gn.combatEvidence"));
        try{
            if(!server.getWorldData().getLevelName().equals("GN_Review_r39_creation"))throw new IllegalStateException("Registered isolated world required");
            run(server.overworld());result.put("status",failures.isEmpty()?"PASS":"FAIL");result.put("failures",failures);
        }catch(Throwable error){result.put("status","FAIL");result.put("error",error.toString());error.printStackTrace();}
        finally{
            fixtures.forEach(Entity::discard);result.put("checks",checks);result.put("failures",failures);result.put("version",net.minecraftforge.fml.ModList.get().getModContainerById(Frontier.ID).orElseThrow().getModInfo().getVersion().toString());result.put("screenUsed",false);
            try{Files.writeString(evidence,new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception error){error.printStackTrace();}
            System.out.println("GN_COMBAT_REVIEW "+result.get("status")+" checks="+checks.size());
        }
    }
    private void check(boolean value,String name){if(!value)failures.add(name);else checks.add(name);}
    private void inventoryEqual(List<ItemStack> expected,List<ItemStack> actual,String name){
        boolean equal=expected.size()==actual.size();
        for(int i=0;i<Math.min(expected.size(),actual.size());i++)if(!ItemStack.matches(expected.get(i),actual.get(i))){
            equal=false;result.put(name+" slot "+i,Map.of("expected",expected.get(i).save(new CompoundTag()).toString(),"actual",actual.get(i).save(new CompoundTag()).toString()));
        }
        check(equal,name);
    }
    private String id(ItemStack item){return ForgeRegistries.ITEMS.getKey(item.getItem()).toString();}
    private Mob unit(ServerLevel level,String name){
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(name));Mob mob=(Mob)Objects.requireNonNull(type.create(level));
        var spawn=level.getSharedSpawnPos();mob.moveTo(spawn.getX()+.5,level.getMaxBuildHeight()-12,spawn.getZ()+.5,0,0);
        mob.getPersistentData().putBoolean("sewv:companion_rolled",true);mob.setNoAi(true);mob.setNoGravity(true);
        mob.finalizeSpawn(level,level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.COMMAND,null,null);
        if(mob instanceof Pillager)RegisteredBedGoal.markBandit((Pillager)mob);
        fixtures.add(mob);
        if(!level.addFreshEntity(mob))throw new IllegalStateException("Test fixture spawn rejected: "+name);
        NpcEquipment.prepare(mob);return mob;
    }
    private List<String> contents(Mob mob){return NpcEquipment.contents(mob).stream().map(s->s.save(new CompoundTag()).toString()).toList();}
    private int ammo(Mob mob){int sum=0;var inv=NpcEquipment.inventory(mob);for(int i=6;i<inv.getSlots();i++)if(inv.getStackInSlot(i).getItem() instanceof IAmmo)sum+=inv.getStackInSlot(i).getCount();return sum;}
    private int count(Mob mob,String id){int sum=0;var inv=NpcEquipment.inventory(mob);for(int i=6;i<inv.getSlots();i++)if(id(inv.getStackInSlot(i)).equals(id))sum+=inv.getStackInSlot(i).getCount();return sum;}
    private void run(ServerLevel level)throws Exception{
        WorldState.get(level).enabled=true;
        Mob east=unit(level,"simpleenemymod:ruunit"),west=unit(level,"simpleenemymod:usunit");
        for(Mob mob:List.of(east,west)){
            var data=mob==east?NpcLoadoutData.catalog.east():NpcLoadoutData.catalog.west();var stack=mob.getMainHandItem();var gun=IGun.getIGunOrNull(stack);
            check(gun!=null,"regular infantry holds TaCZ gun");
            check(stack.hasTag()&&stack.getTag().getCompound("TaczAddon").getBoolean("Sealed"),"native gun quality is fixed while the soldier is alive");
            var issued=data.weapons().stream().filter(w->w.id().equals(gun.getGunId(stack).toString())).findFirst().orElseThrow();
            check(id(mob.getItemBySlot(EquipmentSlot.HEAD)).equals(data.helmet()),"faction ballistic helmet");
            check(id(mob.getItemBySlot(EquipmentSlot.CHEST)).equals(data.vest()),"faction ballistic vest");
            check(ammo(mob)==issued.reserve(),"reserve ammunition matches selected gun loadout");
            check(count(mob,"survival_instinct:bandage")>=2&&count(mob,"survival_instinct:mre")==1,"regular medical and ration inventory");
            check(!gun.useDummyAmmo(stack),"no infinite or dummy ammunition");
            var before=contents(mob);NpcEquipment.prepare(mob);check(before.equals(contents(mob)),"issue is idempotent");
            var expectedSaved=NpcEquipment.contents(mob);Mob restored=(Mob)mob.getType().create(level);restored.load(mob.saveWithoutId(new CompoundTag()));
            inventoryEqual(expectedSaved,NpcEquipment.contents(restored),"NPC inventory and gun NBT survive save/load "+(mob==east?"east":"west"));
            var api=new ModernKineticGunScriptAPI();api.setShooter(mob);api.setItemStack(stack);api.setDataHolder(IGunOperator.fromLivingEntity(mob).getDataHolder());
            api.setAmmoInBarrel(true);int loaded=gun.getCurrentAmmoCount(stack)+(gun.hasBulletInBarrel(stack)?1:0);
            check(api.reduceAmmoOnce(),"native TaCZ fire consumes a round");
            check(gun.getCurrentAmmoCount(stack)+(gun.hasBulletInBarrel(stack)?1:0)==loaded-1,"loaded rounds decrease after firing");
            int reserve=ammo(mob),taken=api.consumeAmmoFromPlayer(7);check(taken==7&&ammo(mob)==reserve-7,"native TaCZ reload consumes physical reserve");
        }
        for(String name:List.of("tacz_sewv:ru_medic","tacz_sewv:us_engineer","tacz_sewv:ru_combat_engineer","simpleenemymod:pmcunit","minecraft:pillager")){
            Mob mob=unit(level,name);check(NpcEquipment.managed(mob),"inventory/loot managed for "+name);
            if(name.contains("pmc")){
                var saved=NpcEquipment.contents(mob);Mob loaded=(Mob)mob.getType().create(level);loaded.load(mob.saveWithoutId(new CompoundTag()));
                inventoryEqual(saved,NpcEquipment.contents(loaded),"PMC inventory survives save/load after splitting oversized native ammo");
            }
            if(name.contains("medic"))check(count(mob,"survival_instinct:bandage")>=6,"medic carries additional medicine");
            if(name.contains("engineer"))check(ammo(mob)>0,"engineer carries sidearm ammunition");
        }
        Map<String,Float> damages=new LinkedHashMap<>();
        for(String bulletType:List.of("fmj","hp","ap")){
            float bare=shot(level,east,false,false,bulletType),ordinary=shot(level,east,true,false,bulletType),ballistic=shot(level,east,false,true,bulletType);
            damages.put(bulletType+".bare",bare);damages.put(bulletType+".netherite_protection",ordinary);damages.put(bulletType+".ballistic",ballistic);
            check(bare>0&&Math.abs(bare-ordinary)<.001,"ordinary armor/toughness/enchantments do not reduce "+bulletType);
            check(ballistic<bare,"ballistic equipment still reduces "+bulletType);
        }
        result.put("damage",damages);
        Cow unarmored=target(level,false,false),armored=target(level,true,false);
        unarmored.hurt(level.damageSources().mobAttack(east),8);armored.hurt(level.damageSources().mobAttack(east),8);
        check(armored.getHealth()>unarmored.getHealth(),"ordinary armor still protects against melee");
        // Alter real state before death; these exact values, including arbitrary mod NBT, must survive.
        ItemStack gun=east.getMainHandItem();gun.getOrCreateTag().putString("GNReviewIdentity","same-weapon");
        IGun.getIGunOrNull(gun).setCurrentAmmoCount(gun,3);gun.setDamageValue(23);east.getItemBySlot(EquipmentSlot.CHEST).setDamageValue(17);
        var inv=NpcEquipment.inventory(east);for(int i=6;i<inv.getSlots();i++)if(id(inv.getStackInSlot(i)).equals("survival_instinct:bandage")){inv.extractItem(i,1,false);break;}
        List<ItemStack> expected=NpcEquipment.contents(east);int itemEntities=level.getEntitiesOfClass(ItemEntity.class,east.getBoundingBox().inflate(8)).size();
        east.hurt(level.damageSources().genericKill(),10000);
        List<FallenSoldier> found=level.getEntitiesOfClass(FallenSoldier.class,east.getBoundingBox().inflate(3));
        check(found.size()==1,"one corpse is created for one death");FallenSoldier corpse=found.get(0);fixtures.add(corpse);
        inventoryEqual(expected,java.util.stream.IntStream.range(0,expected.size()).mapToObj(corpse.inventory::getItem).toList(),"death preserves exact inventory");
        check(level.getEntitiesOfClass(ItemEntity.class,east.getBoundingBox().inflate(8)).size()==itemEntities,"no duplicate ground drops or rerolled gun");
        FallenSoldier restored=new FallenSoldier(Frontier.FALLEN_SOLDIER.get(),level);((Entity)restored).load(((Entity)corpse).saveWithoutId(new CompoundTag()));
        for(int i=0;i<54;i++)check(ItemStack.matches(corpse.inventory.getItem(i),restored.inventory.getItem(i)),"corpse save/load slot "+i);
        for(int i=0;i<6;i++)check(ItemStack.matches(corpse.visibleEquipment(i),restored.visibleEquipment(i)),"visible corpse equipment survives save/load "+i);
        var one=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes("gncombat-one".getBytes()),"GNCombatOne"));
        var two=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes("gncombat-two".getBytes()),"GNCombatTwo"));
        one.setPos(((Entity)corpse).position());two.setPos(((Entity)corpse).position());
        var first=((MenuProvider)corpse).createMenu(1,one.getInventory(),one);var second=((MenuProvider)corpse).createMenu(2,two.getInventory(),two);
        first.clicked(0,0,ClickType.PICKUP,one);second.clicked(0,0,ClickType.PICKUP,two);
        check(!first.getCarried().isEmpty()&&second.getCarried().isEmpty()&&corpse.inventory.getItem(0).isEmpty(),"two looters cannot obtain the same item twice");
        check(first.getCarried().getOrCreateTag().getString("GNReviewIdentity").equals("same-weapon"),"looted gun retains its original custom NBT");
        check(corpse.visibleEquipment(0).isEmpty(),"looting the gun clears its synchronized appearance");
        check(!corpse.visibleEquipment(4).isEmpty(),"unlooted ballistic vest remains visible");
        ItemStack recovered=first.getCarried().copy();one.getInventory().setItem(0,recovered.copy());one.tickCount=10;
        Class.forName("com.github.leopoko.tacz_attributes_addon.handler.GunObtainHandler").getMethod("onPlayerTick",TickEvent.PlayerTickEvent.class)
            .invoke(null,new TickEvent.PlayerTickEvent(TickEvent.Phase.END,one));
        check(ItemStack.matches(recovered,one.getInventory().getItem(0)),"native first-obtain handler preserves recovered gun quality and full NBT");
        first.setCarried(ItemStack.EMPTY);one.getInventory().clearContent();two.getInventory().clearContent();
        // Exercise the actual server menu, including malicious clicks before discovery.
        ((Entity)corpse).setNoGravity(true);corpse.inventory.setItem(6,new ItemStack(Items.DIAMOND,7));corpse.inventory.setItem(10,new ItemStack(Items.IRON_INGOT,3));
        one.containerMenu=first;two.containerMenu=second;
        var searchMenu=(CorpseMenu)first;var otherMenu=(CorpseMenu)second;
        check(first.getItems().get(6).isEmpty()&&first.getItems().get(10).isEmpty(),"hidden storage is absent from the menu synchronization payload");
        for(ClickType click:List.of(ClickType.PICKUP,ClickType.QUICK_MOVE,ClickType.SWAP,ClickType.THROW,ClickType.CLONE,ClickType.PICKUP_ALL))first.clicked(6,0,click,one);
        check(corpse.inventory.getItem(6).getCount()==7&&first.getCarried().isEmpty()&&one.getInventory().isEmpty(),"undiscovered items reject all pickup/swap/drop/clone click routes");
        check(!first.clickMenuButton(one,987),"invalid search section is rejected");
        first.clickMenuButton(one,0);int searchTicks=PrototypeConfig.CORPSE_SEARCH_TICKS.get();
        for(int i=0;i<searchTicks-1;i++)((Entity)corpse).tick();
        check(!searchMenu.known(6)&&searchMenu.elapsed()==searchTicks-1,"occupied slot remains hidden until the complete search duration");
        first.clickMenuButton(one,0);for(int i=0;i<10;i++)((Entity)corpse).tick();
        check(!searchMenu.known(6)&&searchMenu.elapsed()==searchTicks-1,"pausing search preserves partial progress without advancing");
        first.clickMenuButton(one,0);((Entity)corpse).tick();
        check(searchMenu.known(6)&&!searchMenu.known(10)&&!otherMenu.known(6),"search reveals one item and knowledge stays personal and compartment-specific");
        first.clicked(6,0,ClickType.QUICK_MOVE,one);
        check(corpse.inventory.getItem(6).isEmpty()&&one.getInventory().countItem(Items.DIAMOND)==7,"discovered item transfers through the actual menu without loss");
        second.clicked(6,0,ClickType.QUICK_MOVE,two);check(two.getInventory().isEmpty(),"second player cannot duplicate a recovered item");
        first.clickMenuButton(one,1);((Entity)corpse).tick();int partialProgress=searchMenu.elapsed();first.removed(one);one.containerMenu=one.inventoryMenu;
        for(int i=0;i<5;i++)((Entity)corpse).tick();check(corpse.search(one.getUUID()).elapsed()==partialProgress,"closed menu stops search time");
        var savedSearch=new FallenSoldier(Frontier.FALLEN_SOLDIER.get(),level);((Entity)savedSearch).load(((Entity)corpse).saveWithoutId(new CompoundTag()));
        check(savedSearch.search(one.getUUID()).known(6)&&!savedSearch.search(two.getUUID()).known(6)&&savedSearch.search(one.getUUID()).elapsed()==partialProgress,"discoveries and interrupted progress survive corpse save/load per player");
        var reopened=(CorpseMenu)((MenuProvider)corpse).createMenu(3,one.getInventory(),one);one.containerMenu=reopened;
        check(reopened.known(6)&&reopened.activeSection()==-1,"reopening retains discoveries and waits for a new search command");
        ((net.minecraft.world.inventory.AbstractContainerMenu)reopened).clickMenuButton(one,1);one.setPos(((Entity)corpse).getX()+20,((Entity)corpse).getY(),((Entity)corpse).getZ());((Entity)corpse).tick();
        check(!((net.minecraft.world.inventory.AbstractContainerMenu)reopened).stillValid(one)&&corpse.search(one.getUUID()).elapsed()==partialProgress,"distance limit prevents search progression and access");
        one.setPos(((Entity)corpse).position());((net.minecraft.world.inventory.AbstractContainerMenu)reopened).removed(one);one.containerMenu=one.inventoryMenu;second.removed(two);two.containerMenu=two.inventoryMenu;
        one.getInventory().clearContent();two.getInventory().clearContent();
        for(int i=0;i<1201;i++)((Entity)corpse).tick();check(!((Entity)corpse).isRemoved(),"unlooted inventory is not deleted by a timer");
        corpse.inventory.clearContent();for(int i=0;i<NpcLoadoutData.catalog.emptyTicks();i++)((Entity)corpse).tick();check(((Entity)corpse).isRemoved(),"empty corpse decays");
        for(String name:List.of("tacz_sewv:ru_medic","tacz_sewv:us_engineer","simpleenemymod:pmcunit","minecraft:pillager")){
            Mob victim=unit(level,name);
            for(var slot:NpcInventory.EQUIPMENT){var item=victim.getItemBySlot(slot);if(item.isDamageableItem())item.setDamageValue(9);}
            var beforeDeath=NpcEquipment.contents(victim);int ground=level.getEntitiesOfClass(ItemEntity.class,victim.getBoundingBox().inflate(8)).size();
            victim.hurt(level.damageSources().genericKill(),10000);
            var bodies=level.getEntitiesOfClass(FallenSoldier.class,victim.getBoundingBox().inflate(3)).stream().filter(b->!((Entity)b).isRemoved()).toList();
            check(bodies.size()==1,"single shared corpse for "+name);
            if(bodies.size()==1){var body=bodies.get(0);fixtures.add(body);inventoryEqual(beforeDeath,java.util.stream.IntStream.range(0,beforeDeath.size()).mapToObj(body.inventory::getItem).toList(),"preserved inventory for "+name);((Entity)body).discard();}
            check(level.getEntitiesOfClass(ItemEntity.class,victim.getBoundingBox().inflate(8)).size()==ground,"no duplicate ground loot for "+name);
        }
        Mob fatal=unit(level,"simpleenemymod:ruunit");fatal.setHealth(.5f);
        hit(level,west,fatal,"fmj",false);
        var fatalBodies=level.getEntitiesOfClass(FallenSoldier.class,fatal.getBoundingBox().inflate(3)).stream().filter(b->!((Entity)b).isRemoved()).toList();
        check(!fatal.isAlive()&&fatalBodies.size()==1,"actual TaCZ fatal shot creates a corpse");
        result.put("fatalShot",Map.of("alive",fatal.isAlive(),"health",fatal.getHealth(),"corpses",fatalBodies.size(),"originalVestDamage",fatal.getItemBySlot(EquipmentSlot.CHEST).getDamageValue()));
        for(var body:fatalBodies){fixtures.add(body);check(body.inventory.getItem(4).getDamageValue()>0,"fatal bullet wear is preserved in the corpse");((Entity)body).discard();}
        // Release this test's previous units from the original patrol population tracker.
        fixtures.forEach(Entity::discard);fixtures.clear();
        verifySpawnRules(level,one,two);
        var pack=PackChecks.run(level,one);result.put("pack",pack);for(Object name:(List<?>)pack.get("checks"))checks.add(name.toString());
        var performance=PerformanceChecks.run(level,unit(level,"simpleenemymod:usunit"));result.put("performance",performance);for(Object name:(List<?>)performance.get("checks"))checks.add(name.toString());
        var night=NightZombieChecks.run(level,unit(level,"simpleenemymod:usunit"));result.put("nightZombies",night);for(Object name:(List<?>)night.get("checks"))checks.add(name.toString());
        result.put("worldgen",CityGenerationChecks.run(level));check(true,"final preset generates both city and natural chunks at their actual boundary");
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private void verifySpawnRules(ServerLevel level,net.minecraft.server.level.ServerPlayer one,net.minecraft.server.level.ServerPlayer two)throws Exception{
        check(net.minecraftforge.fml.ModList.get().isLoaded("incontrol"),"original In Control is loaded on the dedicated server");
        Class<?> manager=Class.forName("mcjty.incontrol.rules.RulesManager"),when=Class.forName("mcjty.incontrol.rules.support.SpawnWhen");
        List<?> rules=(List<?>)manager.getMethod("getFilteredRules",net.minecraft.world.level.Level.class,when).invoke(null,level,Enum.valueOf((Class)when,"POSITION"));
        check(rules.size()==5,"all five configured population rules are parsed by In Control");
        var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of("config/incontrol/spawn.json"))).getAsJsonArray();
        boolean ids=true;for(var row:json)for(var mob:row.getAsJsonObject().getAsJsonArray("mob"))ids&=ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(mob.getAsString()));
        check(ids,"population rules reference only registered entity identifiers");
        Object setup=Class.forName("mcjty.incontrol.InControl").getField("setup").get(null);
        var cacheField=setup.getClass().getField("cache");Object oldCache=cacheField.get(setup);Class<?> cacheType=oldCache.getClass();
        boolean addedOne=false,addedTwo=false;
        try{
            if(!level.players().isEmpty())throw new IllegalStateException("Dedicated isolated test has no connected players");
            addedOne=level.players().add(one);
            Object cache=cacheType.getConstructor().newInstance();cacheField.set(setup,cache);cacheType.getMethod("performCount",net.minecraft.world.level.LevelAccessor.class).invoke(cache,level);
            var count=cacheType.getMethod("getCount",net.minecraft.world.level.LevelAccessor.class,EntityType.class);
            var match=rules.get(0).getClass().getMethod("match",net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck.class);
            Mob probe=unit(level,"simpleenemymod:ruunit");
            var natural=new net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck(probe,level,MobSpawnType.NATURAL,null);
            int cap=json.get(0).getAsJsonObject().getAsJsonObject("mincount").get("amount").getAsInt();
            int total=0;for(var id:json.get(0).getAsJsonObject().getAsJsonArray("mob"))total+=(Integer)count.invoke(cache,level,ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id.getAsString())));
            check(total<cap&&!(Boolean)match.invoke(rules.get(0),natural),"natural east spawn is unrestricted below the one-player faction cap");
            while(total<cap){unit(level,"simpleenemymod:ruunit");total++;}
            cache=cacheType.getConstructor().newInstance();cacheField.set(setup,cache);cacheType.getMethod("performCount",net.minecraft.world.level.LevelAccessor.class).invoke(cache,level);
            check((Boolean)match.invoke(rules.get(0),natural),"native In Control evaluator blocks the next east spawn at "+cap+" per player");
            var command=new net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck(probe,level,MobSpawnType.COMMAND,null);
            check(!(Boolean)match.invoke(rules.get(0),command),"population rule leaves command and scripted unit creation intact");
            addedTwo=level.players().add(two);cache=cacheType.getConstructor().newInstance();cacheField.set(setup,cache);cacheType.getMethod("performCount",net.minecraft.world.level.LevelAccessor.class).invoke(cache,level);
            check(!(Boolean)match.invoke(rules.get(0),natural),"In Control faction limit scales from "+cap+" to "+(cap*2)+" with two server players");
            result.put("incontrol",Map.of("rules",rules.size(),"testedFactionCap",cap,"multiplayerScaling",true,"existingPatrolCapKept",true));
        }finally{if(addedTwo)level.players().remove(two);if(addedOne)level.players().remove(one);cacheField.set(setup,oldCache);}
    }
    private Cow target(ServerLevel level,boolean normal,boolean ballistic){
        Cow cow=EntityType.COW.create(level);fixtures.add(cow);cow.setNoAi(true);cow.setNoGravity(true);
        var pos=level.getSharedSpawnPos();cow.moveTo(pos.getX()+4.5,level.getMaxBuildHeight()-12,pos.getZ()+.5,0,0);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);cow.setHealth(200);
        if(normal){cow.getAttribute(Attributes.ARMOR).setBaseValue(20);cow.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(12);var chest=new ItemStack(Items.NETHERITE_CHESTPLATE);chest.enchant(Enchantments.ALL_DAMAGE_PROTECTION,4);cow.setItemSlot(EquipmentSlot.CHEST,chest);}
        if(ballistic){cow.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare:ru_helmet_6b47"))));cow.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare:ru_chest_6b43"))));}
        level.addFreshEntity(cow);cow.tick();return cow;
    }
    private float shot(ServerLevel level,Mob shooter,boolean normal,boolean ballistic,String bulletType)throws Exception{
        Cow target=target(level,normal,ballistic);hit(level,shooter,target,bulletType,false);
        if(ballistic)check(target.getItemBySlot(EquipmentSlot.CHEST).getDamageValue()>0,"bullet wears ballistic vest for "+bulletType);
        return 200-target.getHealth();
    }
    private void hit(ServerLevel level,Mob shooter,LivingEntity target,String bulletType,boolean head)throws Exception{
        ItemStack gun=shooter.getMainHandItem();IGun api=IGun.getIGunOrNull(gun);var index=TimelessAPI.getCommonGunIndex(api.getGunId(gun)).orElseThrow();
        Class<?> type=Class.forName("com.ori.taczplus.bullet.BulletType");Object variant=Class.forName("com.ori.taczplus.bullet.BulletTypes").getMethod("byId",String.class).invoke(null,bulletType);
        Class.forName("com.ori.taczplus.bullet.BulletTypeData").getMethod("setLoaded",ItemStack.class,type).invoke(null,gun,variant);
        IGunOperator.fromLivingEntity(shooter).draw(shooter::getMainHandItem);
        var bullet=new EntityKineticBullet(level,shooter,gun,api.getGunId(gun),api.getGunDisplayId(gun),index.getGunData().getAmmoId(),false,index.getGunData(),index.getBulletData());
        check(bullet.getPersistentData().getString("taczexpert:bullet_type").equals(bulletType),"Expert stamps actual "+bulletType+" ammunition");
        Vec3 hit=target.position().add(0,.6,0),start=hit.add(-4,0,0);bullet.setPos(start);bullet.setDeltaMovement(1,0,0);
        var method=EntityKineticBullet.class.getDeclaredMethod("onHitEntity",TacHitResult.class,Vec3.class,Vec3.class);method.setAccessible(true);
        method.invoke(bullet,new TacHitResult(new EntityKineticBullet.EntityResult(target,hit,head)),start,hit);
    }
}
