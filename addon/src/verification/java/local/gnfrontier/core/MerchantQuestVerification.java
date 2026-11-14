package local.gnfrontier.core;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import local.gnfrontier.*;
import net.minecraft.nbt.*;

public final class MerchantQuestVerification {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void reject(Runnable op){checks++;try{op.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid dedicated quest accepted");}
    public static void main(String[] args)throws Exception {
        JsonObject definition=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var catalog=MerchantQuests.parse(definition);check(catalog.size()==8,"Eight configured contracts");
        Campaign.State campaign=new Campaign.State();Economy.State account=new Economy.State();
        UUID player=UUID.fromString("12345678-1234-1234-1234-123456789abc"),other=UUID.fromString("12345678-1234-1234-1234-123456789abd");
        Campaign.refresh(campaign,player,0);Economy.State initial=account;Campaign.State fresh=campaign;
        reject(()->MerchantQuests.accept(fresh,initial,catalog.get(0)));
        Economy.rewardStanding(account,Economy.Merchant.BLACK,10);
        reject(()->MerchantQuests.accept(fresh,initial,catalog.get(1)));
        reject(()->MerchantQuests.resolve(catalog,"black_contact@0"));reject(()->MerchantQuests.resolve(catalog,"unknown@1"));
        long paid=0;
        for(var quest:catalog) {
            var original=campaign;var before=account;Map<String,Integer> items=new HashMap<>();quest.items().forEach(i->items.put(i.item(),i.count()));
            reject(()->MerchantQuests.claim(original,before,quest,items));
            campaign=MerchantQuests.accept(campaign,account,quest);var accepted=campaign;CompoundTag unchanged=accepted.save();
            var shortItems=new HashMap<>(items);var last=quest.items().get(quest.items().size()-1);shortItems.put(last.item(),last.count()-1);
            reject(()->MerchantQuests.claim(accepted,before,quest,shortItems));check(accepted.save().equals(unchanged),"Short final ingredient leaves every progress field unchanged");
            Economy.State capped=account.copy();capped.balance=Economy.MAX_MONEY;
            reject(()->MerchantQuests.claim(accepted,capped,quest,items));check(accepted.save().equals(unchanged),"Overflow leaves progress unchanged");
            var claim=MerchantQuests.claim(campaign,account,quest,items);paid+=quest.reward();
            check(account.balance==paid-quest.reward()&&claim.account().balance==paid,"Exact reward without mutating old account");
            campaign=claim.campaign();account=claim.account();var done=campaign;var rewarded=account;
            reject(()->MerchantQuests.claim(done,rewarded,quest,items));reject(()->MerchantQuests.accept(done,rewarded,quest));
            Campaign.refresh(campaign,player,(campaign.day+1)*Economy.DAY_TICKS);
            check(campaign.merchantCompleted.contains(quest.id()),"Daily reset preserves completed contracts");
            campaign=Campaign.State.load(campaign.save());check(campaign.merchantCompleted.contains(quest.id()),"NBT reload preserves completion");
            check(campaign.merchantAccepted.contains(quest.id()),"Permanent acceptance kept for audit");
        }
        check(account.traderReputation[Economy.Merchant.BLACK.ordinal()]==55,"Exact standing after the complete chain");
        WorldState world=new WorldState();world.players.put(player,account);world.campaigns.put(player,campaign);world.players.put(other,new Economy.State());
        CompoundTag saved=world.save(new CompoundTag());WorldState reload=WorldState.load(saved);
        check(reload.campaign(player).merchantCompleted.size()==8&&reload.campaign(other).merchantCompleted.isEmpty(),"SavedData keeps player progress isolated");
        CompoundTag legacy=campaign.save();legacy.remove("merchantAccepted");legacy.remove("merchantCompleted");
        var migrated=Campaign.State.load(legacy);check(migrated.day==campaign.day&&migrated.merchantAccepted.isEmpty(),"Legacy campaign migration preserves daily state");
        var updated=definition.deepCopy();updated.getAsJsonArray("quests").get(0).getAsJsonObject().addProperty("revision",2);
        var revised=MerchantQuests.parse(updated);reject(()->MerchantQuests.resolve(revised,catalog.get(0).token()));
        var resized=definition.deepCopy();resized.getAsJsonArray("quests").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject().addProperty("count",25);
        var resizedCatalog=MerchantQuests.parse(resized);reject(()->MerchantQuests.resolve(resizedCatalog,catalog.get(0).token()));
        var fractional=definition.deepCopy();fractional.getAsJsonArray("quests").get(0).getAsJsonObject().addProperty("reward",1.5);reject(()->MerchantQuests.parse(fractional));
        var done=campaign;var rewarded=account;reject(()->MerchantQuests.accept(done,rewarded,revised.get(0)));
        var broken=definition.deepCopy();broken.getAsJsonArray("quests").get(0).getAsJsonObject().getAsJsonArray("requires").add("black_bulk_order");reject(()->MerchantQuests.parse(broken));
        var duplicate=definition.deepCopy();duplicate.getAsJsonArray("quests").add(duplicate.getAsJsonArray("quests").get(0).deepCopy());reject(()->MerchantQuests.parse(duplicate));
        check(paid==11600,"Configured campaign pays the documented total");
        check(WorldgenBounds.within(-16,-16,0,0,1)&&WorldgenBounds.within(31,31,0,0,1),"Writable region includes both edge corners");
        check(!WorldgenBounds.within(-17,0,0,0,1)&&!WorldgenBounds.within(32,0,0,0,1),"One block beyond either X edge is rejected");
        check(!WorldgenBounds.within(0,-17,0,0,1)&&!WorldgenBounds.within(0,32,0,0,1),"One block beyond either Z edge is rejected");
        check(WorldgenBounds.within(-32,-32,-2,-2,0)&&WorldgenBounds.within(-17,-17,-2,-2,0),"Negative-center chunk is included");
        check(!WorldgenBounds.within(-16,-32,-2,-2,0)&&!WorldgenBounds.within(-33,-32,-2,-2,0),"Negative-center neighboring chunks are rejected");
        check(!WorldgenBounds.within(0,0,0,0,-1),"Read-only generation region rejects every write");
        System.out.println("GN_MERCHANT_QUEST_PASS checks="+checks+" contracts="+catalog.size()+" totalReward="+paid+" runtimeServer=false");
    }
}
