package local.gnfrontier.core;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Authored, compatible sentence/target pools. Only new boards draw variants. */
public final class DailyMissions {
    public static final int MAX_HISTORY=100000;
    public record Phrase(String id,String text) {}
    public record Target(String id,String label,String item,String gunId,List<String> entities,
                         List<Integer> counts,String unit,long unitReward,long baseReward) {}
    public record Template(String id,Campaign.Kind kind,Economy.Merchant merchant,String title,
                           List<Phrase> openings,List<Phrase> middles,List<Phrase> endings,List<Target> targets) {
        public int variants(){return Math.multiplyExact(openings.size()*middles.size()*endings.size(),targets.stream().mapToInt(t->t.counts.size()).sum());}
    }
    public record Catalog(List<Template> templates,int variants,DeliveryModules.Pool deliveries) {}
    private record Compiled(Catalog catalog,List<Campaign.Mission> variants) {}
    private static volatile Compiled active;
    private DailyMissions() {}
    public static Catalog catalog(){Compiled data=active;return data==null?null:data.catalog;}
    public static void install(Catalog catalog){Objects.requireNonNull(catalog);List<Campaign.Mission> variants=new ArrayList<>();for(int i=0;i<catalog.variants;i++)variants.add(variant(catalog,i));active=new Compiled(catalog,List.copyOf(variants));}
    private static String id(JsonObject o,String key){String v=o.get(key).getAsString();if(!v.matches("[a-z0-9_]{1,48}"))throw new IllegalArgumentException("Invalid mission ID: "+v);return v;}
    private static String text(JsonObject o,String key,int max){String v=o.get(key).getAsString();if(v.isBlank()||v.length()>max||v.contains("\u00a7"))throw new IllegalArgumentException("Invalid mission text: "+key);return v;}
    private static int number(JsonObject o,String key,int min,int max){int v=o.get(key).getAsBigDecimal().intValueExact();if(v<min||v>max)throw new IllegalArgumentException("Invalid mission number: "+key);return v;}
    private static String resource(String value){if(!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid resource: "+value);return value;}
    private static List<Phrase> phrases(JsonObject row,String key) {
        List<Phrase> out=new ArrayList<>();Set<String> ids=new HashSet<>(),texts=new HashSet<>();
        for(JsonElement e:row.getAsJsonArray(key)) {JsonObject o=e.getAsJsonObject();Phrase p=new Phrase(id(o,"id"),text(o,"text",240));
            if(!ids.add(p.id)||!texts.add(p.text)||out.size()>=16)throw new IllegalArgumentException("Duplicate/too many phrases: "+key);out.add(p);}
        if(out.isEmpty())throw new IllegalArgumentException("Missing phrases: "+key);return List.copyOf(out);
    }
    public static Catalog parse(JsonObject document) {
        int schema=number(document,"schema",1,2);
        List<Template> out=new ArrayList<>();Set<String> ids=new HashSet<>();int variants=0;boolean delivery=false,ground=false;
        for(JsonElement e:document.getAsJsonArray("templates")) {
            JsonObject row=e.getAsJsonObject();String id=id(row,"id");if(!ids.add(id)||out.size()>=64)throw new IllegalArgumentException("Duplicate/too many templates");
            Campaign.Kind kind=Campaign.Kind.valueOf(row.get("kind").getAsString());Economy.Merchant merchant=Economy.Merchant.valueOf(row.get("merchant").getAsString());
            List<Target> targets=new ArrayList<>();Set<String> targetIds=new HashSet<>();
            for(JsonElement t:row.getAsJsonArray("targets")) {
                JsonObject o=t.getAsJsonObject();String targetId=id(o,"id");if(!targetIds.add(targetId)||targets.size()>=32)throw new IllegalArgumentException("Invalid target count/ID");
                String item=o.has("item")?resource(o.get("item").getAsString()):"",gun=o.has("gun_id")?resource(o.get("gun_id").getAsString()):"";
                List<String> entities=new ArrayList<>();if(o.has("entities"))for(JsonElement entity:o.getAsJsonArray("entities"))entities.add(resource(entity.getAsString()));
                if(entities.size()>32||new HashSet<>(entities).size()!=entities.size())throw new IllegalArgumentException("Invalid entity targets");
                if(kind==Campaign.Kind.DELIVERY?(item.isEmpty()||!entities.isEmpty()):(!item.isEmpty()||!gun.isEmpty()))throw new IllegalArgumentException("Mismatched objective payload");
                if((kind==Campaign.Kind.HUNT||kind==Campaign.Kind.SOLDIER)&&entities.isEmpty())throw new IllegalArgumentException("A hunt needs explicit entity IDs");
                if(!gun.isEmpty()&&!item.equals("tacz:modern_kinetic_gun"))throw new IllegalArgumentException("Gun ID requires TaCZ gun item");
                List<Integer> counts=new ArrayList<>();for(JsonElement count:o.getAsJsonArray("counts")) {
                    int n=count.getAsBigDecimal().intValueExact();if(n<1||n>(kind==Campaign.Kind.DELIVERY?512:64)||counts.contains(n)||counts.size()>=32)throw new IllegalArgumentException("Invalid count variant");counts.add(n);}
                if(counts.isEmpty())throw new IllegalArgumentException("No quantity variants");
                targets.add(new Target(targetId,text(o,"label",64),item,gun,List.copyOf(entities),List.copyOf(counts),text(o,"unit",8),number(o,"unit_reward",1,100000),number(o,"base_reward",0,100000)));
            }
            if(targets.isEmpty())throw new IllegalArgumentException("No mission targets");
            Template t=new Template(id,kind,merchant,text(row,"title",80),phrases(row,"openings"),phrases(row,"middles"),phrases(row,"endings"),List.copyOf(targets));
            variants=Math.addExact(variants,t.variants());if(variants>200000)throw new IllegalArgumentException("Daily catalog too large");out.add(t);
            delivery|=kind==Campaign.Kind.DELIVERY;ground|=kind==Campaign.Kind.HUNT||kind==Campaign.Kind.SOLDIER;
        }
        if(!delivery||!ground)throw new IllegalArgumentException("Daily catalog needs delivery and ground combat");return new Catalog(List.copyOf(out),variants,schema==2?DeliveryModules.parse(document.getAsJsonObject("deliveries")):null);
    }
    private static String render(String source,Target target,int count){return source.replace("{target}",target.label).replace("{count}",Integer.toString(count)).replace("{unit}",target.unit);}
    public static String hash(String input) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))).substring(0,32);}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static Campaign.Mission variant(Catalog catalog,int index) {
        if(index<0||index>=catalog.variants)throw new IllegalArgumentException("Variant outside catalog");
        for(Template t:catalog.templates) {
            if(index>=t.variants()){index-=t.variants();continue;}
            Phrase end=t.endings.get(index%t.endings.size());index/=t.endings.size();
            Phrase middle=t.middles.get(index%t.middles.size());index/=t.middles.size();
            Phrase opening=t.openings.get(index%t.openings.size());index/=t.openings.size();
            for(Target target:t.targets) {
                if(index>=target.counts.size()){index-=target.counts.size();continue;}
                int count=target.counts.get(index);String objective=t.kind+"|"+target.item+"|"+target.gunId+"|"+String.join(",",new TreeSet<>(target.entities));
                String identity=t.id+"/"+target.id+"/"+count+"/"+opening.id+"/"+middle.id+"/"+end.id;
                String description=render(opening.text+"\n"+middle.text+"\n"+end.text,target,count);
                // Equivalent wording changes cannot produce an identical visible contract twice.
                String appearance=hash(t.kind+"|"+t.merchant+"|"+objective+"|"+count+"|"+description);
                return new Campaign.Mission("daily_"+hash(identity),render(t.title,target,count),t.kind,target.item,target.gunId,count,
                    target.baseReward+target.unitReward*count,t.merchant,description,target.entities,objective,appearance);
            }
        }
        throw new IllegalStateException("Invalid catalog index");
    }
    private static int gcd(int a,int b){while(b!=0){int t=a%b;a=b;b=t;}return a;}
    public static List<Campaign.Mission> draw(Campaign.State state,UUID player,long day) {
        Compiled data=Objects.requireNonNull(active,"Daily catalog was not loaded");Catalog catalog=data.catalog;
        if(catalog.deliveries!=null)return drawModules(data,state,player,day);
        List<Campaign.Mission> board=new ArrayList<>();Set<String> today=new HashSet<>();
        if(state.issuedMissions.size()>=MAX_HISTORY)return List.of();
        long seed=player.getMostSignificantBits()^Long.rotateLeft(player.getLeastSignificantBits(),19)^day*0x9e3779b97f4a7c15L;
        SplittableRandom rng=new SplittableRandom(seed);int start=rng.nextInt(catalog.variants),step=rng.nextInt(1,catalog.variants+1);
        while(gcd(step,catalog.variants)!=1)step=step%catalog.variants+1;
        for(int slot=0;slot<3;slot++) {
            Campaign.Mission picked=null;
            // Prefer a delivery and ground hunt; if their unused pools are exhausted, use any remaining kind.
            for(int pass=0;pass<4&&picked==null;pass++)for(int i=0;i<catalog.variants;i++) {
                int index=(int)((start+(long)(i+slot*7919)*step)%catalog.variants);Campaign.Mission m=data.variants.get(index);
                if(pass<2&&slot==0&&m.kind()!=Campaign.Kind.DELIVERY)continue;
                if(pass<2&&slot==1&&m.kind()!=Campaign.Kind.HUNT&&m.kind()!=Campaign.Kind.SOLDIER)continue;
                if(today.contains(m.objective())||state.issuedMissions.contains(m.id())||state.issuedAppearances.contains(m.appearance()))continue;
                if((pass==0||pass==2)&&state.recentObjectives.contains(m.objective()))continue;
                picked=m;break;
            }
            if(picked==null||state.issuedMissions.size()>=MAX_HISTORY)break;
            board.add(picked);today.add(picked.objective());state.issuedMissions.add(picked.id());state.issuedAppearances.add(picked.appearance());
        }
        for(Campaign.Mission m:board)state.recentObjectives.add(m.objective());
        while(state.recentObjectives.size()>9)state.recentObjectives.remove(0);
        return List.copyOf(board);
    }
    private static boolean available(Campaign.State state,Set<String> today,Campaign.Mission m,boolean avoidRecent){
        Set<String> objectives=DeliveryModules.objectives(m);
        return !state.issuedMissions.contains(m.id())&&!state.issuedAppearances.contains(m.appearance())
            &&Collections.disjoint(today,objectives)&&(!avoidRecent||Collections.disjoint(state.recentObjectives,objectives));
    }
    private static List<Campaign.Mission> drawModules(Compiled data,Campaign.State state,UUID player,long day){
        SplittableRandom random=new SplittableRandom(player.getMostSignificantBits()^Long.rotateLeft(player.getLeastSignificantBits(),19)^day*0x9e3779b97f4a7c15L);
        List<Campaign.Mission> board=new ArrayList<>();Set<String> today=new HashSet<>();
        for(int slot=0;slot<3&&state.issuedMissions.size()<MAX_HISTORY;slot++){
            Campaign.Mission chosen=null;boolean delivery=slot==0||(slot==2&&random.nextInt(4)!=0);
            if(delivery){
                // Bounded attempts regardless of the theoretical combination count or saved history size.
                for(int attempt=0;attempt<192&&chosen==null;attempt++){
                    var m=DeliveryModules.sample(data.catalog.deliveries,random);
                    if(available(state,today,m,attempt<128))chosen=m;
                }
            }else{
                int start=random.nextInt(data.variants.size());
                for(int pass=0;pass<4&&chosen==null;pass++)for(int i=0;i<data.variants.size();i++){
                    var m=data.variants.get((start+i)%data.variants.size());if(m.kind()==Campaign.Kind.DELIVERY)continue;
                    if(slot==1&&pass<2&&m.kind()!=Campaign.Kind.HUNT&&m.kind()!=Campaign.Kind.SOLDIER)continue;
                    if(available(state,today,m,pass%2==0)){chosen=m;break;}
                }
            }
            if(chosen==null)continue;
            board.add(chosen);today.addAll(DeliveryModules.objectives(chosen));state.issuedMissions.add(chosen.id());state.issuedAppearances.add(chosen.appearance());
        }
        for(var m:board)state.recentObjectives.addAll(new TreeSet<>(DeliveryModules.objectives(m)));
        while(state.recentObjectives.size()>9)state.recentObjectives.remove(0);
        return List.copyOf(board);
    }
}
