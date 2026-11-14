package local.gnfrontier.core;

import com.google.gson.*;
import java.util.*;

/** Permanent, player-owned black-market contracts. Definitions and rewards are server authoritative. */
public final class MerchantQuests {
    public record Requirement(String item,int count) {}
    public record Quest(String id,int revision,String title,String description,List<String> requires,
                        int reputation,long reward,int reputationReward,List<Requirement> items,String signature) {
        public String token(){return id+"@"+revision+"-"+signature;}
    }
    public record Claim(Campaign.State campaign,Economy.State account) {}
    private MerchantQuests() {}
    private static IllegalArgumentException error(String reason){return new IllegalArgumentException("message.gnfrontier.quest."+reason);}
    private static long integer(JsonObject row,String key) {
        try{return row.get(key).getAsBigDecimal().longValueExact();}
        catch(ArithmeticException failure){throw new IllegalArgumentException("Expected bounded integer: "+key,failure);}
    }
    private static int smallInteger(JsonObject row,String key) {
        long value=integer(row,key);if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new IllegalArgumentException("Integer out of bounds: "+key);return (int)value;
    }
    private static String signature(JsonObject row) {
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(row.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,12);}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static void validateState(Campaign.State s) {
        if(s.merchantAccepted.size()>512||s.merchantCompleted.size()>512||!s.merchantAccepted.containsAll(s.merchantCompleted))throw new IllegalArgumentException("Invalid merchant quest state");
        for(String id:s.merchantAccepted)if(!id.matches("[a-z0-9_]{1,40}"))throw new IllegalArgumentException("Invalid saved merchant quest ID");
    }
    public static List<Quest> parse(JsonObject document) {
        if(integer(document,"schema")!=1)throw new IllegalArgumentException("Unsupported merchant quest schema");
        List<Quest> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(JsonElement element:document.getAsJsonArray("quests")) {
            JsonObject row=element.getAsJsonObject();String id=row.get("id").getAsString();
            if(!id.matches("[a-z0-9_]{1,40}")||!seen.add(id)||result.size()>=64)throw new IllegalArgumentException("Duplicate or invalid quest ID: "+id);
            int revision=smallInteger(row,"revision"),reputation=smallInteger(row,"reputation"),repReward=smallInteger(row,"reputation_reward");long reward=integer(row,"reward");
            if(revision<1||reputation<0||reputation>100||repReward<0||repReward>100||reward<0||reward>Economy.MAX_MONEY)throw new IllegalArgumentException("Invalid quest bounds: "+id);
            String title=row.get("title").getAsString(),description=row.get("description").getAsString();
            if(!title.matches("quest\\.gnfrontier\\.[a-z0-9_.]+")||!description.matches("quest\\.gnfrontier\\.[a-z0-9_.]+"))throw new IllegalArgumentException("Invalid translation keys: "+id);
            List<String> requires=new ArrayList<>();
            for(JsonElement dependency:row.getAsJsonArray("requires")) {
                String prior=dependency.getAsString();
                if(prior.equals(id)||!seen.contains(prior)||requires.contains(prior))throw new IllegalArgumentException("Quest dependencies must precede the quest: "+id);
                requires.add(prior);
            }
            List<Requirement> items=new ArrayList<>();Set<String> unique=new HashSet<>();
            for(JsonElement ingredient:row.getAsJsonArray("items")) {
                JsonObject entry=ingredient.getAsJsonObject();String item=entry.get("item").getAsString();int count=smallInteger(entry,"count");
                if(!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!unique.add(item)||count<1||count>512||items.size()>=8)throw new IllegalArgumentException("Invalid delivery requirement: "+id);
                items.add(new Requirement(item,count));
            }
            if(items.isEmpty())throw new IllegalArgumentException("A delivery quest needs items: "+id);
            result.add(new Quest(id,revision,title,description,List.copyOf(requires),reputation,reward,repReward,List.copyOf(items),signature(row)));
        }
        if(result.isEmpty())throw new IllegalArgumentException("Empty merchant quest catalog");return List.copyOf(result);
    }
    public static Quest resolve(List<Quest> catalog,String token) {
        return catalog.stream().filter(q->q.token().equals(token)).findFirst().orElseThrow(()->error("stale"));
    }
    public static boolean unlocked(Campaign.State s,Economy.State a,Quest q) {
        return Economy.standing(a,Economy.Merchant.BLACK)>=q.reputation()&&s.merchantCompleted.containsAll(q.requires());
    }
    private static void eligible(Campaign.State s,Economy.State a,Quest q) {
        s.validate();a.validate();if(s.merchantCompleted.contains(q.id()))throw error("completed");
        if(!unlocked(s,a,q))throw error("locked");
    }
    public static Campaign.State accept(Campaign.State s,Economy.State a,Quest q) {
        eligible(s,a,q);Campaign.State next=s.copy();next.merchantAccepted.add(q.id());return next;
    }
    public static Claim claim(Campaign.State s,Economy.State a,Quest q,Map<String,Integer> available) {
        eligible(s,a,q);if(!s.merchantAccepted.contains(q.id()))throw error("accept_first");
        for(Requirement item:q.items())if(available.getOrDefault(item.item(),0)<item.count())throw error("missing");
        if(a.balance>Economy.MAX_MONEY-q.reward())throw error("balance_limit");
        Campaign.State next=s.copy();Economy.State account=a.copy();next.merchantCompleted.add(q.id());
        account.balance+=q.reward();Economy.rewardStanding(account,Economy.Merchant.BLACK,q.reputationReward());
        return new Claim(next,account);
    }
}
