package local.gnfrontier.core;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Persistence, constrained generation, exhaustion and all-or-nothing accounting. */
public final class ModularDeliveryVerification {
    private static int checks;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private static void reject(Runnable action){checks++;try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid modular delivery accepted");}
    private static Campaign.State board(Campaign.Mission m){Campaign.State s=new Campaign.State();s.day=0;s.dailyInitialized=true;s.dailyMissions=List.of(m);s.issuedMissions.add(m.id());s.issuedAppearances.add(m.appearance());return s;}
    private static JsonObject limitFixture(JsonObject source,int firstCount){
        JsonObject doc=source.deepCopy(),m=doc.getAsJsonObject("deliveries");JsonArray selected=new JsonArray(),ids=new JsonArray();
        for(int i=0;i<3;i++){
            var row=m.getAsJsonArray("items").get(i).getAsJsonObject();row.add("counts",JsonParser.parseString("["+(i==0?firstCount:4)+"]"));
            row.add("bundle_limits",JsonParser.parseString("{\"max_for_three\":4,\"max_for_two\":8}"));selected.add(row);ids.add(row.get("id"));
        }
        m.add("items",selected);m.add("sizes",JsonParser.parseString("[3]"));
        for(String key:List.of("requesters","purposes")){JsonArray one=new JsonArray();one.add(m.getAsJsonArray(key).get(0));m.add(key,one);}
        var purpose=m.getAsJsonArray("purposes").get(0).getAsJsonObject();purpose.add("items",ids);
        JsonArray requesters=new JsonArray();requesters.add(m.getAsJsonArray("requesters").get(0).getAsJsonObject().get("id"));purpose.add("requesters",requesters);
        return doc;
    }
    private static void verifyLimits(JsonObject doc){
        for(int count:List.of(4,5,8,9,512)){
            var fixture=limitFixture(doc,count);var pool=DailyMissions.parse(fixture).deliveries();int expected=count<=4?3:count<=8?2:1;
            var first=pool.items().values().iterator().next();
            for(int seed=0;seed<40;seed++){
                var mission=DeliveryModules.sample(pool,new SplittableRandom(seed));check(mission.requirements().size()==expected,"Quantity boundaries reduce companions: "+count);
                check(mission.requirements().stream().anyMatch(r->r.item().equals(first.item())&&r.count()==count),"The bulk target and its original quantity survive reduction");
                long reward=0;for(var r:mission.requirements()){var item=pool.items().values().stream().filter(t->t.item().equals(r.item())).findFirst().orElseThrow();reward+=item.baseReward()+r.count()*item.unitReward();}
                check(mission.reward()==reward&&mission.count()==mission.requirements().stream().mapToInt(Campaign.DeliveryItem::count).sum(),"Removed companions contribute neither count nor reward");
            }
        }
        JsonObject legacy=limitFixture(doc,16);for(var item:legacy.getAsJsonObject("deliveries").getAsJsonArray("items"))item.getAsJsonObject().remove("bundle_limits");
        check(DeliveryModules.sample(DailyMissions.parse(legacy).deliveries(),new SplittableRandom(2)).requirements().size()==3,"Old definitions without limits keep legacy behavior");
        JsonObject invalid=limitFixture(doc,4);invalid.getAsJsonObject("deliveries").getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("bundle_limits").addProperty("max_for_three",9);
        reject(()->DailyMissions.parse(invalid));
        invalid.getAsJsonObject("deliveries").getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("bundle_limits").addProperty("max_for_three",-1);reject(()->DailyMissions.parse(invalid));
    }
    public static void run(Path path)throws Exception{
        long start=System.nanoTime();JsonObject doc=JsonParser.parseString(Files.readString(path)).getAsJsonObject();var catalog=DailyMissions.parse(doc);var pool=catalog.deliveries();check(pool!=null,"Schema 2 uses shared modules");
        verifyLimits(doc);
        Set<String> seenPurposes=new HashSet<>(),seenItems=new HashSet<>();Set<Integer> sizes=new HashSet<>();Map<Integer,Integer> distribution=new TreeMap<>();List<Campaign.Mission> examples=new ArrayList<>();Campaign.Mission multi=null;
        for(int i=0;i<10000;i++){
            var m=DeliveryModules.sample(pool,new SplittableRandom(i));m.validate();sizes.add(m.requirements().size());
            distribution.merge(m.requirements().size(),1,Integer::sum);
            var purpose=pool.purposes().stream().filter(p->m.description().contains(p.reason())).findFirst().orElseThrow();seenPurposes.add(purpose.id());
            check(purpose.requesters().stream().anyMatch(r->m.description().contains("依頼主："+r.label()+"\n")&&r.merchant()==m.merchant()),"Requester is permitted for purpose and reward faction");
            long reward=0;Set<String> distinct=new HashSet<>();
            for(var r:m.requirements()){
                var t=pool.items().values().stream().filter(x->x.item().equals(r.item())&&x.gunId().equals(r.gunId())).findFirst().orElseThrow();
                check(purpose.items().contains(t.id())&&t.counts().contains(r.count()),"Each selected material and quantity belongs to compatible pool");check(distinct.add(r.key()),"Bundle has distinct materials");seenItems.add(t.id());reward+=t.baseReward()+t.unitReward()*r.count();
                check(m.requirements().size()<=pool.bundleLimits().get(t.id()).maxSize(r.count()),"Every item satisfies its quantity-dependent bundle limit");
            }
            check(reward==m.reward(),"Reward derived from actual materials and quantities");check(!m.description().contains("{")&&!m.title().contains("{")&&!m.description().contains("ノア"),"Rendered text has no unresolved token or Noah knowledge");
            check(Campaign.Mission.load(m.save()).equals(m),"Multiple requirements round-trip real NBT");
            if(examples.size()<pool.purposes().size()&&examples.stream().noneMatch(x->x.description().contains(purpose.reason())))examples.add(m);
            if(m.requirements().size()==3)multi=m;
        }
        check(sizes.equals(Set.of(1,2,3)),"All requested bundle sizes reachable");check(seenItems.equals(pool.items().keySet()),"Every material reachable");check(seenPurposes.size()==pool.purposes().size(),"Every purpose reachable");
        DailyMissions.install(catalog);UUID player=new UUID(1,8);Campaign.State s=new Campaign.State(),same=new Campaign.State(),other=new Campaign.State();Campaign.refresh(s,player,0);Campaign.refresh(same,player,0);Campaign.refresh(other,new UUID(1,9),0);
        check(s.dailyMissions.equals(same.dailyMissions)&&!s.dailyMissions.equals(other.dailyMissions),"Deterministic per-player daily draw");
        for(int user=0;user<4;user++){
            Campaign.State history=new Campaign.State();Set<String> ids=new HashSet<>(),appearances=new HashSet<>(),contents=new HashSet<>();
            for(int day=0;day<240;day++){
                Campaign.refresh(history,new UUID(4,user),(long)day*24000);Set<String> targets=new HashSet<>();check(history.dailyMissions.size()==3,"Full board while large candidate pools remain");
                check(history.dailyMissions.stream().anyMatch(m->m.kind()==Campaign.Kind.DELIVERY),"Daily board includes delivery");
                for(var m:history.dailyMissions){check(ids.add(m.id())&&appearances.add(m.appearance())&&contents.add(m.contentKey()),"No repeated contents, ID or visible contract over saved days");for(String key:DeliveryModules.objectives(m))check(targets.add(key),"No overlapping material between today's bundles");}
                if(day%31==0){history=Campaign.State.load(history.save());DailyMissions.install(DailyMissions.parse(doc));}
            }
        }
        var m=Objects.requireNonNull(multi);s=board(m);String token=Campaign.token(s,0);Campaign.accept(s,0,token);final Campaign.State accepted=s;
        Economy.State money=new Economy.State();List<Integer> counts=m.requirements().stream().map(Campaign.DeliveryItem::count).toList();
        for(int i=0;i<3;i++){
            List<Integer> shortage=new ArrayList<>(counts);shortage.set(i,shortage.get(i)-1);shortage.set((i+1)%3,shortage.get((i+1)%3)+500);
            var before=s.save();reject(()->Campaign.claim(accepted,money,0,token,shortage));check(before.equals(s.save())&&money.balance==0,"Extra of another item cannot cover shortage; no reward/progress mutation");
        }
        reject(()->Campaign.claim(accepted,money,0,token,m.count()));reject(()->Campaign.claim(accepted,money,0,token,counts.subList(0,2)));
        var issued=s.save();JsonObject repriced=doc.deepCopy();repriced.getAsJsonObject("deliveries").getAsJsonArray("items").get(0).getAsJsonObject().addProperty("unit_reward",999);
        DailyMissions.install(DailyMissions.parse(repriced));Campaign.refresh(s,player,1);check(s.save().equals(issued),"Reload and reopen preserve issued payload, price and acceptance");
        var paid=Campaign.claim(s,money,0,token,counts);check(paid.balance==m.reward()&&money.balance==0,"Complete bundle pays exactly once");reject(()->Campaign.claim(accepted,paid,0,token,counts));
        var corrupt=m.save();corrupt.getList("requirements",Tag.TAG_COMPOUND).getCompound(0).putInt("count",0);reject(()->Campaign.Mission.load(corrupt));
        var legacy=Campaign.MISSIONS.get(0);var old=legacy.save();old.remove("requirements");check(Campaign.Mission.load(old).equals(legacy),"Pre-upgrade single-item NBT migrates without changing amount or reward");
        JsonObject bad=doc.deepCopy();bad.getAsJsonObject("deliveries").getAsJsonArray("titles").get(0).getAsJsonObject().addProperty("text","{missing}");reject(()->DailyMissions.parse(bad));
        JsonObject tiny=doc.deepCopy();var modules=tiny.getAsJsonObject("deliveries");
        for(String key:List.of("items","requesters","purposes","titles","openings","circumstances","instructions","endings")){JsonArray one=new JsonArray();one.add(modules.getAsJsonArray(key).get(0));modules.add(key,one);}
        var purpose=modules.getAsJsonArray("purposes").get(0).getAsJsonObject();
        JsonArray soleItem=new JsonArray();soleItem.add(modules.getAsJsonArray("items").get(0).getAsJsonObject().get("id"));purpose.add("items",soleItem);
        JsonArray soleRequester=new JsonArray();soleRequester.add(modules.getAsJsonArray("requesters").get(0).getAsJsonObject().get("id"));purpose.add("requesters",soleRequester);
        modules.add("sizes",JsonParser.parseString("[1]"));modules.getAsJsonArray("items").get(0).getAsJsonObject().add("counts",JsonParser.parseString("[1]"));
        DailyMissions.install(DailyMissions.parse(tiny));Campaign.State exhausted=new Campaign.State();Campaign.refresh(exhausted,player,0);check(exhausted.dailyMissions.stream().filter(x->x.kind()==Campaign.Kind.DELIVERY).count()==1,"Tiny pool issues its sole delivery once");
        Campaign.refresh(exhausted,player,24000);check(exhausted.dailyMissions.stream().noneMatch(x->x.kind()==Campaign.Kind.DELIVERY),"Exhausted modular pool never reuses a contract");
        DailyMissions.install(catalog);
        Path evidence=Path.of("build/reports/modular-deliveries/generated-examples.json").toAbsolutePath();
        Files.createDirectories(evidence.getParent());
        Files.writeString(evidence,new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(examples));
        Files.writeString(evidence.resolveSibling("quantity-distribution.json"),new GsonBuilder().setPrettyPrinting().create().toJson(distribution));
        System.out.println("GN_MODULAR_DELIVERIES_PASS checks="+checks+" samples=10000 playerDays=960 items="+pool.items().size()+" purposes="+pool.purposes().size()+" elapsedMs="+(System.nanoTime()-start)/1000000+" runtimeServer=false");
    }
}
