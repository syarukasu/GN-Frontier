package local.gnreview;

import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import local.gnfrontier.*;
import local.gnfrontier.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Runs only in the disposable r21 world. Not a production mod. */
public final class DailyChecks {
    private static final List<String> passed=new ArrayList<>();
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason+"; prior="+passed.subList(Math.max(0,passed.size()-6),passed.size()));passed.add(reason);}
    private static void owned(ServerPlayer p){if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r21_daily"))throw new IllegalStateException("Owned r21 review world required");}
    private static void commands(ServerPlayer p,String... text){for(String command:text)p.getServer().getCommands().performPrefixedCommand(p.getServer().createCommandSourceStack(),command);}
    private static String inventory(ServerPlayer p){ListTag n=new ListTag();p.getInventory().save(n);return n.toString();}
    private static WorldState setup(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);p.setHealth(p.getMaxHealth());p.getAbilities().invulnerable=true;p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Frontier.PDA.get()));
        WorldState w=WorldState.get(p.serverLevel());w.enabled=true;Economy.State account=new Economy.State();account.balance=1000;w.players.put(p.getUUID(),account);
        Campaign.State c=new Campaign.State();Campaign.refresh(c,p.getUUID(),p.getServer().overworld().getGameTime());w.campaigns.put(p.getUUID(),c);((net.minecraft.world.level.saveddata.SavedData)w).setDirty();return w;
    }
    private static void supply(ServerPlayer p,Campaign.Mission m) {
        p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Frontier.PDA.get()));
        int left=m.count()+2;Item type=TradeService.item(m.item());int slot=9;
        while(left>0){int amount=Math.min(left,type.getMaxStackSize());ItemStack stack=new ItemStack(type,amount);if(!m.gunId().isEmpty())stack.getOrCreateTag().putString("GunId",m.gunId());p.getInventory().setItem(slot,stack);left-=amount;slot+=3;}
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
    }
    private static void rejectUnchanged(ServerPlayer p,Runnable operation,String label) {
        var w=WorldState.get(p.serverLevel());String before=inventory(p);var state=w.campaign(p.getUUID()).save();var account=WorldState.encode(w.account(p.getUUID()));
        try{operation.run();throw new AssertionError("Unexpected success: "+label);}catch(IllegalArgumentException expected){}
        check(before.equals(inventory(p))&&state.equals(w.campaign(p.getUUID()).save())&&account.equals(WorldState.encode(w.account(p.getUUID()))),label);
    }
    public static Map<String,Object> prepare(ServerPlayer p,Path evidence)throws Exception {
        owned(p);passed.clear();commands(p,"gamerule doMobSpawning false","gamerule doDaylightCycle false","gamerule doWeatherCycle false","gamemode creative @p","time set 6000","weather clear","fill 0 239 0 16 239 16 minecraft:smooth_stone","fill 0 240 0 16 248 16 minecraft:air","tp @p 8.5 240 8.5");
        WorldState w=setup(p);p.setGameMode(GameType.CREATIVE);var c=w.campaign(p.getUUID());check(c.dailyInitialized&&c.dailyMissions.size()==3,"Actual data-pack loader generated three daily contracts");
        check(c.dailyMissions.get(0).kind()==Campaign.Kind.DELIVERY,"First generated daily is a delivery");
        check(c.dailyMissions.get(1).kind()==Campaign.Kind.HUNT||c.dailyMissions.get(1).kind()==Campaign.Kind.SOLDIER,"Second generated daily is a ground hunt");
        var resource=p.getServer().getResourceManager().getResource(new ResourceLocation("gnfrontier:daily_missions/catalog.json")).orElseThrow();
        try(var input=resource.open()){check(Arrays.equals(input.readAllBytes(),Files.readAllBytes(evidence.getParent().getParent().resolve("overlay/kubejs/data/gnfrontier/daily_missions/catalog.json"))),"Effective daily resource equals authored KubeJS catalog");}
        var m=c.dailyMissions.get(0);supply(p,m);FrontierNetwork.send(p,"");
        return Map.of("passed",List.copyOf(passed),"variants",DailyMissions.catalog().variants(),"initialBalance",1000,"player",p.getUUID().toString(),"board",c.dailyMissions,"firstToken",Campaign.token(c,0));
    }
    public static Map<String,Object> verifyUi(ServerPlayer p,Path evidence) {
        owned(p);WorldState w=WorldState.get(p.serverLevel());var c=w.campaign(p.getUUID());var m=c.dailyMissions.get(0);
        check((c.claimed&1)!=0,"Actual PDA packets accepted and completed generated delivery");
        check(w.account(p.getUUID()).balance==1000+m.reward(),"PDA delivery paid the generated reward exactly once");
        check(Economy.standing(w.account(p.getUUID()),m.merchant())==5,"PDA reward credited the issuing merchant's standing");
        check(TradeService.missionCount(p,m)==2,"PDA consumed exactly the generated item count");
        rejectUnchanged(p,()->TradeService.claim(p,0,Campaign.token(c,0)),"Repeated PDA claim changes no inventory, money or progress");
        return Map.of("passed",List.copyOf(passed),"delivered",m.count(),"reward",m.reward(),"remaining",2);
    }
    public static Map<String,Object> inspect(ServerPlayer p,Path evidence) {
        owned(p);var w=WorldState.get(p.serverLevel());
        return Map.of("inventory",inventory(p),"pda",TradeService.hasPda(p),"alive",p.isAlive(),"down",Hypoxia.down(p),"campaign",w.campaign(p.getUUID()).save().toString(),"account",WorldState.encode(w.account(p.getUUID())).toString(),"gameTime",p.getServer().overworld().getGameTime(),"tickCount",p.getServer().getTickCount());
    }
    private static Campaign.Mission first(Campaign.Kind kind) {
        var catalog=DailyMissions.catalog();for(int i=0;i<catalog.variants();i++){var m=DailyMissions.variant(catalog,i);if(m.kind()==kind)return m;}throw new AssertionError(kind);
    }
    private static Campaign.State board(ServerPlayer p,Campaign.Mission mission) {
        Campaign.State c=new Campaign.State();c.day=p.getServer().overworld().getGameTime()/24000;c.dailyInitialized=true;c.dailyMissions=List.of(mission);
        c.issuedMissions.add(mission.id());c.issuedAppearances.add(mission.appearance());WorldState.get(p.serverLevel()).campaigns.put(p.getUUID(),c);return c;
    }
    private static Mob victim(ServerPlayer p,String entityId) {
        Entity entity=Objects.requireNonNull(ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityId))).create(p.serverLevel());
        if(!(entity instanceof Mob mob))throw new AssertionError("Mob target required: "+entityId);
        mob.moveTo(5,240,5,0,0);mob.setNoAi(true);mob.setPersistenceRequired();p.serverLevel().addFreshEntity(mob);return mob;
    }
    private static void kill(ServerPlayer p,Mob target) {
        check(target.hurt(p.damageSources().playerAttack(p),100000f)&&target.isDeadOrDying(),"Actual native damage killed "+ForgeRegistries.ENTITY_TYPES.getKey(target.getType()));
    }
    public static Map<String,Object> transactions(ServerPlayer player,Path evidence)throws Exception {
        owned(player);passed.clear();LogUtils.getLogger().info("GN_R21_DAILY_FIXTURE_BEGIN");
        var w=WorldState.get(player.serverLevel());var original=w.campaign(player.getUUID()).save();long originalMoney=w.account(player.getUUID()).balance;
        ServerPlayer peer=FakePlayerFactory.get(player.serverLevel(),new GameProfile(UUID.fromString("81c04e30-b25a-4ac3-a801-123456789abc"),"GN_DailyPeer"));
        peer.moveTo(8.5,240,8.5,0,0);setup(peer);long total=0;
        peer.getMainHandItem().getOrCreateTag();check(TradeService.hasPda(peer),"PDA possession survives empty NBT from other mods");
        peer.getMainHandItem().getOrCreateTag().putString("reviewMarker","incidental NBT");check(TradeService.hasPda(peer),"PDA possession survives unrelated custom NBT");
        peer.getInventory().setItem(0,new ItemStack(Items.PAPER));check(!TradeService.hasPda(peer),"Unrelated item cannot impersonate a PDA");peer.getInventory().setItem(0,new ItemStack(Frontier.PDA.get()));
        for(Campaign.Kind kind:Campaign.Kind.values()) {
            var mission=first(kind);var c=board(peer,mission);String token=Campaign.token(c,0);
            if(kind==Campaign.Kind.DELIVERY)supply(peer,mission);
            rejectUnchanged(peer,()->TradeService.claim(peer,0,token),"Unaccepted "+kind+" claim refused atomically");
            if(kind==Campaign.Kind.HUNT){Mob dead=victim(peer,mission.entities().get(0));kill(peer,dead);check(c.progress[0]==0,"Real kill before acceptance does not count");dead.discard();}
            TradeService.accept(peer,0,token);
            if(kind==Campaign.Kind.DELIVERY) {
                ItemStack all=peer.getInventory().getItem(9).copy();peer.getInventory().setItem(9,ItemStack.EMPTY);
                rejectUnchanged(peer,()->TradeService.claim(peer,0,token),"Insufficient generated delivery preserves all state");peer.getInventory().setItem(9,all);
            } else if(kind==Campaign.Kind.HUNT||kind==Campaign.Kind.SOLDIER) {
                Mob cow=victim(peer,"minecraft:cow");kill(peer,cow);check(c.progress[0]==0,"Unlisted passive target is not a bounty");cow.discard();
                if(kind==Campaign.Kind.SOLDIER){Mob neutral=victim(peer,mission.entities().get(0));kill(peer,neutral);check(c.progress[0]==0,"Neutral non-engaged soldier does not count");neutral.discard();}
                for(int i=0;i<mission.count();i++) {
                    Mob mob=victim(peer,mission.entities().get(0));if(kind==Campaign.Kind.SOLDIER)mob.setTarget(peer);
                    kill(peer,mob);check(c.progress[0]==i+1,"Native death event advances "+kind+" once");
                    new CampaignEvents().killed(new LivingDeathEvent(mob,peer.damageSources().playerAttack(peer)));check(c.progress[0]==i+1,"Repeated death notification for same UUID is ignored");mob.discard();
                }
            } else {
                String name=kind==Campaign.Kind.SHIP?"superbwarfare:speedboat":"superbwarfare:ah_6";
                check(ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(name)),"Registered vehicle ID exists: "+name);
                EntityType<?> type=Objects.requireNonNull(ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(name)),name);
                for(int i=0;i<mission.count();i++) {
                    Entity vehicle=type.create(peer.serverLevel());check(vehicle!=null,"Real vehicle class exists: "+name);
                    CampaignEvents.vehicleDestroyed(vehicle,peer.damageSources().playerAttack(peer),peer.level().getGameTime());check(c.progress[0]==i,"Civilian/unattributed vehicle is excluded");
                    vehicle.getPersistentData().putBoolean("gnfrontier_raid",true);
                    CampaignEvents.vehicleDestroyed(vehicle,peer.damageSources().playerAttack(peer),peer.level().getGameTime());check(c.progress[0]==i+1,"Native vehicle attribution handler credits "+kind);
                    CampaignEvents.vehicleDestroyed(vehicle,peer.damageSources().playerAttack(peer),peer.level().getGameTime());check(c.progress[0]==i+1,"Same vehicle UUID cannot count again");vehicle.discard();
                }
            }
            check(peer.isAlive()&&!peer.isSpectator()&&!Hypoxia.down(peer)&&TradeService.hasPda(peer),"PDA precondition before "+kind+": health="+peer.getHealth()+", removed="+peer.isRemoved()+", spectator="+peer.isSpectator()+", down="+Hypoxia.down(peer)+", pda="+TradeService.hasPda(peer)+", inventory="+inventory(peer));
            long before=w.account(peer.getUUID()).balance;w.account(peer.getUUID()).balance=Economy.MAX_MONEY;
            rejectUnchanged(peer,()->TradeService.claim(peer,0,token),"Balance cap preserves "+kind+" completion state");w.account(peer.getUUID()).balance=before;
            TradeService.claim(peer,0,token);total+=mission.reward();check(w.account(peer.getUUID()).balance==1000+total,"Exact "+kind+" reward committed");
            rejectUnchanged(peer,()->TradeService.claim(peer,0,token),"Repeated "+kind+" claim is rejected");
            if(kind==Campaign.Kind.DELIVERY)check(TradeService.missionCount(peer,mission)==2,"Generated delivery remainder is exact");
        }
        check(w.campaign(player.getUUID()).save().equals(original)&&w.account(player.getUUID()).balance==originalMoney,"Second player did not change the connected player's board or money");
        Class<?> builderClass=Class.forName("com.atsuishio.superbwarfare.tools.CustomExplosion$Builder");
        Entity source=EntityType.SNOWBALL.create(peer.serverLevel());source.moveTo(2.5,241,2.5,0,0);
        Object explosion=builderClass.getConstructor(Entity.class).newInstance(source);
        var neutral=builderClass.getDeclaredField("damageMultiplier");neutral.setAccessible(true);check(neutral.getFloat(explosion)==0,"Removed SBW monster bonus has a neutral compatible field");
        builderClass.getMethod("damage",float.class).invoke(explosion,4f);builderClass.getMethod("radius",float.class).invoke(explosion,2f);
        builderClass.getMethod("explode").invoke(explosion);
        check(source.getPersistentData().getBoolean("sbw_exploded"),"Actual SBW explosion executes Shrapnel's existing explosion hook");
        builderClass.getMethod("explode").invoke(explosion);check(source.getPersistentData().getBoolean("sbw_exploded"),"Already-exploded source retains Shrapnel's duplicate guard");source.discard();
        Entity projectile=EntityType.SNOWBALL.create(peer.serverLevel());projectile.moveTo(2.5,241,2.5,0,0);
        Class<?> projectileTool=Class.forName("com.atsuishio.superbwarfare.tools.ProjectileTool");
        projectileTool.getMethod("causeCustomExplode",net.minecraft.world.entity.projectile.Projectile.class,net.minecraft.world.damagesource.DamageSource.class,Entity.class,float.class,float.class)
            .invoke(null,projectile,peer.damageSources().playerAttack(peer),peer,4f,2f);
        check(projectile.getPersistentData().getBoolean("sbw_exploded"),"Current projectile explosion overload executes with all six Shrapnel mixins loaded");projectile.discard();
        Campaign.State history=w.campaign(peer.getUUID());Set<String> ids=new HashSet<>(history.issuedMissions);
        for(int day=1;day<=60;day++) {
            Campaign.refresh(history,peer.getUUID(),(history.day+1)*24000);
            for(var m:history.dailyMissions)check(ids.add(m.id()),"No duplicate combination across sixty in-game day changes");
        }
        ((net.minecraft.world.level.saveddata.SavedData)w).setDirty();player.getServer().overworld().getDataStorage().save();Path file=player.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/gnfrontier.dat");
        WorldState disk=WorldState.load(NbtIo.readCompressed(file.toFile()).getCompound("data"));
        check(disk.campaign(peer.getUUID()).issuedMissions.equals(history.issuedMissions),"Issued history survives actual compressed world save");
        check(disk.campaign(player.getUUID()).save().equals(original),"Connected player's active/completed board survives disk save");
        check(disk.account(peer.getUUID()).balance==1000+total,"All five kind rewards survive disk save");
        FrontierNetwork.send(player,"");LogUtils.getLogger().info("GN_R21_DAILY_FIXTURE_END");
        return Map.of("passed",List.copyOf(passed),"kinds",5,"sixtyDayHistory",history.issuedMissions.size(),"savedFile",file.toString(),"peer",peer.getUUID().toString(),"totalRewards",total,"twoConnectedClients",false,"combatScope","Real native mob deaths; real ship/helicopter entities passed through the production destruction handler, without a flight/sea battle.");
    }
}
