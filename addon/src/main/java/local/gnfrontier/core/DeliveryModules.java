package local.gnfrontier.core;

import com.google.gson.*;
import java.util.*;

/** Shared authored modules, sampled on demand; no Cartesian product is allocated. */
public final class DeliveryModules {
    public record Requester(String id,String label,Economy.Merchant merchant) {}
    public record Purpose(String id,String label,String reason,List<String> items,List<Requester> requesters) {}
    public record BundleLimit(int maxForThree,int maxForTwo) {
        public int maxSize(int count){return count>maxForTwo?1:count>maxForThree?2:3;}
    }
    private record Selection(DailyMissions.Target target,int count,int maxSize) {}
    public record Pool(Map<String,DailyMissions.Target> items,List<Purpose> purposes,
                       List<DailyMissions.Phrase> titles,List<DailyMissions.Phrase> openings,
                       List<DailyMissions.Phrase> circumstances,List<DailyMissions.Phrase> instructions,
                       List<DailyMissions.Phrase> endings,List<Integer> sizes,Map<String,BundleLimit> bundleLimits) {}
    private DeliveryModules() {}
    private static String id(JsonObject row,String key){String s=row.get(key).getAsString();if(!s.matches("[a-z0-9_]{1,48}"))throw new IllegalArgumentException("Invalid module ID: "+s);return s;}
    private static String text(JsonObject row,String key,int max){String s=row.get(key).getAsString();if(s.isBlank()||s.length()>max||s.contains("§"))throw new IllegalArgumentException("Invalid delivery text: "+key);return s;}
    private static String resource(String s){if(!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid delivery resource: "+s);return s;}
    private static int number(JsonObject row,String key,int min,int max){int n=row.get(key).getAsBigDecimal().intValueExact();if(n<min||n>max)throw new IllegalArgumentException("Invalid delivery number: "+key);return n;}
    private static List<Integer> numbers(JsonArray array,int max){List<Integer> out=new ArrayList<>();for(var e:array){int n=e.getAsBigDecimal().intValueExact();if(n<1||n>max||out.contains(n)||out.size()>=32)throw new IllegalArgumentException("Invalid quantity/size pool");out.add(n);}if(out.isEmpty())throw new IllegalArgumentException("Empty quantity/size pool");return List.copyOf(out);}
    private static List<DailyMissions.Phrase> phrases(JsonObject doc,String key){
        List<DailyMissions.Phrase> out=new ArrayList<>();Set<String> ids=new HashSet<>(),texts=new HashSet<>();
        for(var e:doc.getAsJsonArray(key)){var r=e.getAsJsonObject();String value=text(r,"text",160);
            // Only these two tokens are supported. Typos fail reload instead of reaching the PDA.
            if(value.replace("{purpose}","").replace("{requester}","").matches("(?s).*[{}].*"))throw new IllegalArgumentException("Unknown delivery placeholder");
            var p=new DailyMissions.Phrase(id(r,"id"),value);if(!ids.add(p.id())||!texts.add(value)||out.size()>=32)throw new IllegalArgumentException("Duplicate/too many "+key);out.add(p);}
        if(out.isEmpty())throw new IllegalArgumentException("Missing delivery "+key);return List.copyOf(out);
    }
    public static Pool parse(JsonObject doc){
        Map<String,DailyMissions.Target> items=new LinkedHashMap<>();Set<String> resources=new HashSet<>();
        Map<String,BundleLimit> limits=new LinkedHashMap<>();
        for(var e:doc.getAsJsonArray("items")){
            var r=e.getAsJsonObject();String key=id(r,"id"),item=resource(r.get("item").getAsString()),gun=r.has("gun_id")?resource(r.get("gun_id").getAsString()):"";
            if(!gun.isEmpty()&&!item.equals("tacz:modern_kinetic_gun"))throw new IllegalArgumentException("Gun ID requires a TaCZ gun");
            if(items.containsKey(key)||!resources.add(item+"|"+gun)||items.size()>=128)throw new IllegalArgumentException("Duplicate/too many delivery items");
            items.put(key,new DailyMissions.Target(key,text(r,"label",64),item,gun,List.of(),numbers(r.getAsJsonArray("counts"),512),text(r,"unit",8),number(r,"unit_reward",1,100000),number(r,"base_reward",0,100000)));
            BundleLimit limit=new BundleLimit(512,512);
            if(r.has("bundle_limits")){
                var l=r.getAsJsonObject("bundle_limits");
                limit=new BundleLimit(number(l,"max_for_three",0,512),number(l,"max_for_two",0,512));
                if(limit.maxForThree>limit.maxForTwo)throw new IllegalArgumentException("Three-item quantity limit exceeds two-item limit: "+key);
            }
            limits.put(key,limit);
        }
        Map<String,Requester> requesters=new LinkedHashMap<>();
        for(var e:doc.getAsJsonArray("requesters")){var r=e.getAsJsonObject();String key=id(r,"id");if(requesters.containsKey(key)||requesters.size()>=64)throw new IllegalArgumentException("Duplicate/too many requesters");requesters.put(key,new Requester(key,text(r,"label",64),Economy.Merchant.valueOf(r.get("merchant").getAsString())));}
        List<Purpose> purposes=new ArrayList<>();Set<String> ids=new HashSet<>(),usedItems=new HashSet<>(),usedRequesters=new HashSet<>();
        for(var e:doc.getAsJsonArray("purposes")){
            var r=e.getAsJsonObject();String key=id(r,"id");if(!ids.add(key)||purposes.size()>=64)throw new IllegalArgumentException("Duplicate/too many purposes");
            List<String> allowed=new ArrayList<>();for(var v:r.getAsJsonArray("items")){String k=v.getAsString();if(!items.containsKey(k)||allowed.contains(k))throw new IllegalArgumentException("Unknown/duplicate purpose item: "+k);allowed.add(k);usedItems.add(k);}
            List<Requester> issuers=new ArrayList<>();for(var v:r.getAsJsonArray("requesters")){String k=v.getAsString();Requester q=requesters.get(k);if(q==null||issuers.contains(q))throw new IllegalArgumentException("Unknown/duplicate purpose requester: "+k);issuers.add(q);usedRequesters.add(k);}
            if(allowed.isEmpty()||issuers.isEmpty())throw new IllegalArgumentException("Unreachable delivery purpose");
            String label=text(r,"label",64),reason=text(r,"reason",160);if((label+reason).matches("(?s).*[{}].*"))throw new IllegalArgumentException("Purpose text cannot contain tokens");
            purposes.add(new Purpose(key,label,reason,List.copyOf(allowed),List.copyOf(issuers)));
        }
        List<Integer> sizes=numbers(doc.getAsJsonArray("sizes"),3);
        if(purposes.isEmpty()||!usedItems.equals(items.keySet())||!usedRequesters.equals(requesters.keySet()))throw new IllegalArgumentException("Unused or missing delivery module");
        for(var p:purposes)if(sizes.stream().noneMatch(n->n<=p.items.size()))throw new IllegalArgumentException("Purpose cannot satisfy any order size");
        return new Pool(Collections.unmodifiableMap(items),List.copyOf(purposes),phrases(doc,"titles"),phrases(doc,"openings"),phrases(doc,"circumstances"),phrases(doc,"instructions"),phrases(doc,"endings"),sizes,Collections.unmodifiableMap(limits));
    }
    private static <T>T pick(List<T> values,SplittableRandom random){return values.get(random.nextInt(values.size()));}
    private static String render(String text,Purpose p,Requester r){return text.replace("{purpose}",p.label).replace("{requester}",r.label);}
    public static Campaign.Mission sample(Pool pool,SplittableRandom random){
        Purpose purpose=pick(pool.purposes,random);Requester requester=pick(purpose.requesters,random);
        int size=pick(pool.sizes.stream().filter(n->n<=purpose.items.size()).toList(),random);
        List<String> choices=new ArrayList<>(purpose.items);List<Selection> selected=new ArrayList<>();
        for(int i=0;i<size;i++){
            DailyMissions.Target target=pool.items.get(choices.remove(random.nextInt(choices.size())));
            var limit=pool.bundleLimits.get(target.id());
            // Draw small lots for a mixed order when available; do not make three-item contracts vanishingly rare.
            var compatible=target.counts().stream().filter(n->limit.maxSize(n)>=size).toList();
            int count=pick(compatible.isEmpty()?target.counts():compatible,random);
            selected.add(new Selection(target,count,pool.bundleLimits.get(target.id()).maxSize(count)));
        }
        // Keep the large order itself, then remove companion lines before pricing or assigning identity.
        // Stable ties preserve the random selection order; legacy definitions default to no reduction.
        selected.sort(Comparator.comparingInt(Selection::maxSize));
        int retained=Math.min(selected.size(),selected.get(0).maxSize());
        List<Campaign.DeliveryItem> required=new ArrayList<>();long reward=0;
        for(var entry:selected.subList(0,retained)){
            var target=entry.target();int count=entry.count();
            required.add(new Campaign.DeliveryItem(target.item(),target.gunId(),target.label(),target.unit(),count));
            reward=Math.addExact(reward,Math.addExact(target.baseReward(),Math.multiplyExact(target.unitReward(),count)));
        }
        required.sort(Comparator.comparing(Campaign.DeliveryItem::key));
        var title=pick(pool.titles,random);var opening=pick(pool.openings,random);var circumstance=pick(pool.circumstances,random);var instruction=pick(pool.instructions,random);var ending=pick(pool.endings,random);
        String quantities=String.join(";",required.stream().map(r->r.key()+"="+r.count()).toList());
        String objective="DELIVERY|"+String.join(";",required.stream().map(Campaign.DeliveryItem::key).toList());
        String identity="modules_v2/"+purpose.id+"/"+requester.id+"/"+title.id()+"/"+opening.id()+"/"+circumstance.id()+"/"+instruction.id()+"/"+ending.id()+"/"+quantities;
        String description=render("依頼主：{requester}\n"+opening.text()+"\n"+purpose.reason+"\n"+circumstance.text()+"\n"+instruction.text()+"\n"+ending.text(),purpose,requester);
        // Canonical order means swapping lines or renaming module IDs never evades the duplicate guard.
        String appearance=DailyMissions.hash(requester.merchant+"|"+quantities+"|"+description);
        var first=required.get(0);Campaign.Mission mission=new Campaign.Mission("daily_"+DailyMissions.hash(identity),render(title.text(),purpose,requester),Campaign.Kind.DELIVERY,first.item(),first.gunId(),required.stream().mapToInt(Campaign.DeliveryItem::count).sum(),reward,requester.merchant,description,List.of(),objective,appearance,List.copyOf(required));
        mission.validate();return mission;
    }
    public static Set<String> objectives(Campaign.Mission mission){
        if(mission.kind()!=Campaign.Kind.DELIVERY)return Set.of(mission.objective());
        Set<String> keys=new HashSet<>();for(var r:mission.requirements())keys.add("DELIVERY|"+r.item()+"|"+r.gunId()+"|");return keys;
    }
}
