package local.gnreview;

import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import local.gnfrontier.*;
import local.gnfrontier.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Real server/inventory checks, only on the disposable r31 world. */
public final class ModularDeliveryChecks {
    private static final List<String> passed=new ArrayList<>();
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);passed.add(reason);}
    private static void owned(ServerPlayer p){if(!p.getServer().getWorldData().getLevelName().equals("GN_Review_r31_deliveries"))throw new IllegalStateException("Owned r31 world required");}
    private static String inventory(ServerPlayer p){ListTag n=new ListTag();p.getInventory().save(n);return n.toString();}
    private static Campaign.Mission multi(){
        for(int seed=0;seed<10000;seed++){
            var m=DeliveryModules.sample(DailyMissions.catalog().deliveries(),new SplittableRandom(seed));
            if(m.requirements().size()==3&&m.requirements().stream().allMatch(r->r.gunId().isEmpty()&&r.count()<=32))return m;
        }throw new AssertionError("No three-item fixture");
    }
    private static Campaign.State board(ServerPlayer p,Campaign.Mission m){
        var w=WorldState.get(p.serverLevel());w.enabled=true;Campaign.State c=new Campaign.State();c.day=p.getServer().overworld().getGameTime()/24000;c.dailyInitialized=true;c.dailyMissions=List.of(m);c.issuedMissions.add(m.id());c.issuedAppearances.add(m.appearance());w.campaigns.put(p.getUUID(),c);
        Economy.State account=new Economy.State();account.balance=1000;w.players.put(p.getUUID(),account);((net.minecraft.world.level.saveddata.SavedData)w).setDirty();p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.setHealth(p.getMaxHealth());return c;
    }
    private static void supply(ServerPlayer p,Campaign.Mission m){
        p.getInventory().clearContent();int slot=9;
        for(var r:m.requirements()){
            ItemStack s=new ItemStack(TradeService.item(r.item()),r.count()+2);if(!r.gunId().isEmpty())s.getOrCreateTag().putString("GunId",r.gunId());p.getInventory().setItem(slot,s);slot+=3;
        }
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
    }
    private static void reject(ServerPlayer p,Runnable operation,String label){
        var w=WorldState.get(p.serverLevel());var c=w.campaign(p.getUUID()).save();var a=WorldState.encode(w.account(p.getUUID()));String inventory=inventory(p);
        try{operation.run();throw new AssertionError("Unexpected success: "+label);}catch(IllegalArgumentException expected){}
        check(c.equals(w.campaign(p.getUUID()).save())&&a.equals(WorldState.encode(w.account(p.getUUID())))&&inventory.equals(inventory(p)),label);
    }
    public static Map<String,Object> verify(ServerPlayer player,Path evidence)throws Exception{
        owned(player);passed.clear();var w=WorldState.get(player.serverLevel());var playerBefore=w.campaign(player.getUUID()).save();var moneyBefore=WorldState.encode(w.account(player.getUUID()));String playerInventory=inventory(player);
        var pool=DailyMissions.catalog().deliveries();for(var r:pool.items().values()){
            check(TradeService.exists(r.item()),"Registered material: "+r.item());
            if(!r.gunId().isEmpty())check(com.tacz.guns.api.TimelessAPI.getCommonGunIndex(new net.minecraft.resources.ResourceLocation(r.gunId())).isPresent(),"Registered TaCZ gun: "+r.gunId());
        }
        Set<Integer> sizes=new HashSet<>();
        for(int seed=0;seed<1000;seed++){
            var generated=DeliveryModules.sample(pool,new SplittableRandom(seed));sizes.add(generated.requirements().size());
            for(var r:generated.requirements()){
                var target=pool.items().values().stream().filter(t->t.item().equals(r.item())&&t.gunId().equals(r.gunId())).findFirst().orElseThrow();
                if(generated.requirements().size()>pool.bundleLimits().get(target.id()).maxSize(r.count()))throw new AssertionError("Quantity cap violated in live catalog");
            }
        }
        check(sizes.equals(Set.of(1,2,3)),"All 1-3 item sizes and quantity caps work in the loaded live catalog");
        ServerPlayer peer=FakePlayerFactory.get(player.serverLevel(),new GameProfile(UUID.fromString("86f77910-73c2-4b2d-8015-a03103103103"),"GN_DeliveryPeer"));peer.moveTo(player.position());
        var m=multi();var c=board(peer,m);supply(peer,m);String token=Campaign.token(c,0);
        reject(peer,()->TradeService.claim(peer,0,token),"Unaccepted bundle changes no state");TradeService.accept(peer,0,token);
        check(!TradeService.hasPda(peer),"Daily bundle requires no handheld PDA");
        for(int index=0;index<3;index++){
            supply(peer,m);peer.getInventory().getItem(9+index*3).setCount(m.requirements().get(index).count()-1);
            peer.getInventory().getItem(9+((index+1)%3)*3).setCount(64);
            reject(peer,()->TradeService.claim(peer,0,token),"Missing material "+index+" cannot be covered by excess of another");
        }
        supply(peer,m);ItemStack blocked=peer.getInventory().getItem(9).copy();peer.getInventory().setItem(9,ItemStack.EMPTY);peer.getInventory().setItem(5,blocked.copy());peer.getInventory().offhand.set(0,blocked.copy());
        reject(peer,()->TradeService.claim(peer,0,token),"Disabled hotbar and offhand do not satisfy delivery");
        supply(peer,m);peer.getInventory().getItem(9).getOrCreateTag().putString("customNameMarker","must remain");
        reject(peer,()->TradeService.claim(peer,0,token),"Tagged equipment is not silently consumed as plain materials");
        supply(peer,m);w.account(peer.getUUID()).balance=Economy.MAX_MONEY;
        reject(peer,()->TradeService.claim(peer,0,token),"Wallet ceiling preserves every material");w.account(peer.getUUID()).balance=1000;
        reject(peer,()->TradeService.claim(peer,0,token+"stale"),"Stale contract token rejected atomically");
        TradeService.claim(peer,0,token);check(w.account(peer.getUUID()).balance==1000+m.reward(),"Exact bundle reward committed once");check(Economy.standing(w.account(peer.getUUID()),m.merchant())==5,"Correct issuing faction standing");
        for(var r:m.requirements())check(TradeService.requirementCount(peer,r)==2,"Exact quantity removed for "+r.item());
        reject(peer,()->TradeService.claim(peer,0,token),"Double claim changes nothing");
        var persisted=Campaign.State.load(w.campaign(peer.getUUID()).save());w.campaigns.put(peer.getUUID(),persisted);reject(peer,()->TradeService.claim(peer,0,token),"Claim remains closed after actual NBT reload");
        // Two different TaCZ GunIds share one item registry ID and must never overlap.
        var legacy=DailyMissions.catalog();Campaign.Mission gun=null;
        for(int i=0;i<legacy.variants();i++){var candidate=DailyMissions.variant(legacy,i);if(!candidate.gunId().isEmpty()){gun=candidate;break;}}
        var g=Objects.requireNonNull(gun);var gc=board(peer,g);String gt=Campaign.token(gc,0);TradeService.accept(peer,0,gt);peer.getInventory().clearContent();
        ItemStack wrong=new ItemStack(TradeService.item(g.item()),g.count());wrong.getOrCreateTag().putString("GunId","hamster:wrong_review_gun");peer.getInventory().setItem(9,wrong);
        reject(peer,()->TradeService.claim(peer,0,gt),"Wrong TaCZ GunId does not satisfy gun request");wrong.getOrCreateTag().putString("GunId",g.gunId());TradeService.claim(peer,0,gt);check(TradeService.requirementCount(peer,g.requirements().get(0))==0,"Exact TaCZ gun accepted");
        check(playerBefore.equals(w.campaign(player.getUUID()).save())&&moneyBefore.equals(WorldState.encode(w.account(player.getUUID())))&&playerInventory.equals(inventory(player)),"Another player's inventory, board and wallet remain untouched");
        w.campaigns.remove(peer.getUUID());w.players.remove(peer.getUUID());((net.minecraft.world.level.saveddata.SavedData)w).setDirty();
        return Map.of("status","PASS","checks",passed.size(),"passed",List.copyOf(passed),"items",pool.items().size(),"sample",m.save().toString(),"productionWorldUsed",false);
    }
    public static Map<String,Object> prepare(ServerPlayer p,Path evidence){
        owned(p);for(String command:List.of("gamerule doMobSpawning false","gamerule doDaylightCycle false","gamerule doWeatherCycle false","time set 6000","weather clear"))p.getServer().getCommands().performPrefixedCommand(p.getServer().createCommandSourceStack(),command);
        var m=multi();board(p,m);supply(p,m);p.setGameMode(GameType.CREATIVE);
        return Map.of("status","PASS","mission",m.save().toString(),"reward",m.reward(),"items",m.requirements().size(),"noPdaItem",!TradeService.hasPda(p));
    }
    public static Map<String,Object> pdaVerify(ServerPlayer p,Path evidence){
        owned(p);passed.clear();var w=WorldState.get(p.serverLevel());var c=w.campaign(p.getUUID());var m=Campaign.mission(c,0);
        check(c.accepted==1&&c.claimed==1,"Native PDA buttons accepted and claimed the generated bundle through network");check(w.account(p.getUUID()).balance==1000+m.reward(),"PDA paid the exact displayed reward");
        for(var r:m.requirements())check(TradeService.requirementCount(p,r)==2,"PDA consumed exactly "+r.count()+" of "+r.item());
        check(!TradeService.hasPda(p),"PDA still uses a hotkey without a handheld item");
        return Map.of("status","PASS","checks",passed.size(),"passed",List.copyOf(passed),"balance",w.account(p.getUUID()).balance);
    }
    public static Map<String,Object> gunPrepare(ServerPlayer p,Path evidence){
        owned(p);var catalog=DailyMissions.catalog();
        for(int i=0;i<catalog.variants();i++){
            var m=DailyMissions.variant(catalog,i);if(m.gunId().isEmpty()||m.count()!=1)continue;
            board(p,m);supply(p,m);p.setGameMode(GameType.CREATIVE);
            return Map.of("status","PASS","gunId",m.gunId(),"mission",m.save().toString());
        }throw new AssertionError("No gun fixture");
    }
}
