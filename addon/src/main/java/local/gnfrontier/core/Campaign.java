package local.gnfrontier.core;

import java.util.*;
import net.minecraft.nbt.*;

/** Persistent mission identities and progression. Timers use elapsed ticks; production boards use DailyCycle. */
public final class Campaign {
    public enum Kind { DELIVERY, SHIP, HELICOPTER, SOLDIER, HUNT }
    public record DeliveryItem(String item,String gunId,String label,String unit,int count) {
        public String key(){return item+"|"+gunId;}
        public void validate(){
            if(!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||(!gunId.isEmpty()&&(!item.equals("tacz:modern_kinetic_gun")||!gunId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))||label.length()>64||unit.isBlank()||unit.length()>8||count<1||count>512)
                throw new IllegalArgumentException("Invalid delivery requirement");
        }
        public CompoundTag save(){CompoundTag n=new CompoundTag();n.putString("item",item);n.putString("gunId",gunId);n.putString("label",label);n.putString("unit",unit);n.putInt("count",count);return n;}
        public static DeliveryItem load(CompoundTag n){var r=new DeliveryItem(n.getString("item"),n.getString("gunId"),n.getString("label"),n.getString("unit"),n.getInt("count"));r.validate();return r;}
    }
    public record Mission(String id, String title, Kind kind, String item, String gunId, int count, long reward, Economy.Merchant merchant,
                          String description,List<String> entities,String objective,String appearance,List<DeliveryItem> requirements) {
        public Mission { entities=List.copyOf(entities);requirements=List.copyOf(requirements); }
        public Mission(String id,String title,Kind kind,String item,String gunId,int count,long reward,Economy.Merchant merchant,String description,List<String> entities,String objective,String appearance){
            this(id,title,kind,item,gunId,count,reward,merchant,description,entities,objective,appearance,kind==Kind.DELIVERY?List.of(new DeliveryItem(item,gunId,"","個",count)):List.of());
        }
        public Mission(String id,String title,Kind kind,String item,String gunId,int count,long reward,Economy.Merchant merchant) {
            this(id,title,kind,item,gunId,count,reward,merchant,"",List.of(),kind+"|"+item+"|"+gunId,"");
        }
        public String contentKey(){
            String contents=kind==Kind.DELIVERY
                    ?String.join(";",requirements.stream().map(r->r.key()+"="+r.count()).sorted().toList())
                    :String.join(";",new TreeSet<>(entities))+"|"+count;
            return DailyMissions.hash(kind+"|"+merchant+"|"+contents);
        }
        public CompoundTag save() {
            CompoundTag n=new CompoundTag();n.putString("id",id);n.putString("title",title);n.putString("kind",kind.name());
            n.putString("item",item);n.putString("gunId",gunId);n.putInt("count",count);n.putLong("reward",reward);n.putString("merchant",merchant.name());
            n.putString("description",description);n.putString("objective",objective);n.putString("appearance",appearance);
            ListTag targets=new ListTag();entities.forEach(x->targets.add(StringTag.valueOf(x)));n.put("entities",targets);
            ListTag materials=new ListTag();requirements.forEach(x->materials.add(x.save()));n.put("requirements",materials);return n;
        }
        public static Mission load(CompoundTag n) {
            List<String> entities=new ArrayList<>();for(Tag t:n.getList("entities",Tag.TAG_STRING))entities.add(t.getAsString());
            Mission m=new Mission(n.getString("id"),n.getString("title"),Kind.valueOf(n.getString("kind")),n.getString("item"),n.getString("gunId"),
                n.getInt("count"),n.getLong("reward"),Economy.Merchant.valueOf(n.getString("merchant")),n.getString("description"),List.copyOf(entities),n.getString("objective"),n.getString("appearance"));
            if(n.contains("requirements")) {
                List<DeliveryItem> requirements=new ArrayList<>();for(Tag tag:n.getList("requirements",Tag.TAG_COMPOUND))requirements.add(DeliveryItem.load((CompoundTag)tag));
                m=new Mission(m.id,m.title,m.kind,m.item,m.gunId,m.count,m.reward,m.merchant,m.description,m.entities,m.objective,m.appearance,requirements);
            }
            m.validate();return m;
        }
        public void validate() {
            if(!id.matches("[a-z0-9_]{1,80}")||title.isBlank()||title.length()>160||description.length()>1200||objective.length()>2048||entities.size()>32
                ||count<1||count>(kind==Kind.DELIVERY?1536:64)||reward<0||reward>Economy.MAX_MONEY||(!appearance.isEmpty()&&!appearance.matches("[a-f0-9]{32}")))
                throw new IllegalArgumentException("Invalid saved daily mission");
            if(kind==Kind.DELIVERY){
                if(requirements.isEmpty()||requirements.size()>3)throw new IllegalArgumentException("Invalid delivery size");
                Set<String> seen=new HashSet<>();for(var r:requirements){r.validate();if(!seen.add(r.key()))throw new IllegalArgumentException("Duplicate delivery item");}
                if(requirements.stream().mapToInt(DeliveryItem::count).sum()!=count||!item.equals(requirements.get(0).item)||!gunId.equals(requirements.get(0).gunId))throw new IllegalArgumentException("Inconsistent delivery totals");
            }else if(!requirements.isEmpty())throw new IllegalArgumentException("Combat mission contains delivery items");
        }
    }
    public static final List<Mission> MISSIONS = List.of(
        new Mission("scrap","廃材の補給",Kind.DELIVERY,"gnfrontier:metal_scrap","",16,180,Economy.Merchant.NEUTRAL),
        new Mission("wire","東側への銅線補給",Kind.DELIVERY,"tfmg:copper_wire","",12,240,Economy.Merchant.EAST),
        new Mission("medic","救護物資の補給",Kind.DELIVERY,"survival_instinct:bandage","",4,300,Economy.Merchant.NEUTRAL),
        new Mission("colt1892","旧式拳銃 Colt M1892",Kind.DELIVERY,"tacz:modern_kinetic_gun","hamster:coltm1892",1,900,Economy.Merchant.BLACK),
        new Mission("colt1873","旧式拳銃 Colt M1873 SAA",Kind.DELIVERY,"tacz:modern_kinetic_gun","hamster:colt1873",1,950,Economy.Merchant.BLACK),
        new Mission("ship","海上船舶を1隻撃沈",Kind.SHIP,"","",1,1500,Economy.Merchant.EAST),
        new Mission("helicopter","敵ヘリを1機撃墜",Kind.HELICOPTER,"","",1,1800,Economy.Merchant.WEST),
        new Mission("steel","軍需用鋼鉄の納入",Kind.DELIVERY,"tfmg:steel_ingot","",8,650,Economy.Merchant.EAST),
        new Mission("electronics","電子部品の回収",Kind.DELIVERY,"survival_instinct:electronic_scrap","",8,300,Economy.Merchant.BLACK)
    );
    public static final class State {
        public long day=-1, nextRaid=0, nextInterference=0, jamUntil=0;
        public long uplinkUntil=0,deterrenceUntil=0;
        public int satellitesLaunched=0;
        public int rotation, accepted, claimed;
        public final int[] progress=new int[3];
        public final Set<UUID> targets=new HashSet<>();
        public final Set<String> milestones=new TreeSet<>();
        public final Set<String> merchantAccepted=new TreeSet<>(),merchantCompleted=new TreeSet<>();
        public boolean dailyInitialized;
        public List<Mission> dailyMissions=List.of();
        public final Set<String> issuedMissions=new HashSet<>(),issuedAppearances=new HashSet<>(),issuedContents=new HashSet<>();
        public final List<String> recentObjectives=new ArrayList<>();
        public boolean satellite, deterrence, noahComplete;
        public String noahDimension="";
        public long noahAnchor;
        public State copy() {return load(save());}
        public void validate() {
            MerchantQuests.validateState(this);
            if(day < -1 || rotation<0 || rotation>=MISSIONS.size() || accepted<0 || accepted>7 || claimed<0 || claimed>7
                || (claimed & ~accepted)!=0 || targets.size()>4096 || nextRaid<0 || jamUntil<0 || nextInterference<0
                || uplinkUntil<0 || deterrenceUntil<0 || satellitesLaunched<0)
                throw new IllegalArgumentException("Invalid campaign state");
            for(int p:progress) if(p<0 || p>64)throw new IllegalArgumentException("Invalid mission progress");
            if(dailyMissions.size()>3||issuedMissions.size()>DailyMissions.MAX_HISTORY||issuedAppearances.size()>DailyMissions.MAX_HISTORY||issuedContents.size()>DailyMissions.MAX_HISTORY||recentObjectives.size()>9)
                throw new IllegalArgumentException("Invalid daily mission history");
            if(dailyInitialized) {
                int mask=(1<<dailyMissions.size())-1;if((accepted&~mask)!=0||(claimed&~mask)!=0)throw new IllegalArgumentException("Missing active mission");
                Set<String> activeIds=new HashSet<>();for(Mission m:dailyMissions){m.validate();if(!activeIds.add(m.id)||!issuedMissions.contains(m.id)||!issuedAppearances.contains(m.appearance))throw new IllegalArgumentException("Unrecorded active mission");}
            }
        }
        public CompoundTag save() {
            for(Mission mission:dailyMissions)issuedContents.add(mission.contentKey());
            validate();CompoundTag n=new CompoundTag();n.putLong("day",day);n.putInt("rotation",rotation);
            n.putInt("accepted",accepted);n.putInt("claimed",claimed);n.putIntArray("progress",progress);
            n.putLong("nextRaid",nextRaid);n.putLong("nextInterference",nextInterference);n.putLong("jamUntil",jamUntil);
            n.putLong("uplinkUntil",uplinkUntil);n.putLong("deterrenceUntil",deterrenceUntil);n.putInt("satellitesLaunched",satellitesLaunched);
            n.putBoolean("satellite",satellite);n.putBoolean("deterrence",deterrence);n.putBoolean("noahComplete",noahComplete);
            n.putString("noahDimension",noahDimension);n.putLong("noahAnchor",noahAnchor);
            ListTag seen=new ListTag();targets.stream().map(UUID::toString).sorted().forEach(x->seen.add(StringTag.valueOf(x)));n.put("targets",seen);
            ListTag steps=new ListTag();milestones.forEach(x->steps.add(StringTag.valueOf(x)));n.put("milestones",steps);
            ListTag acceptedQuests=new ListTag(),completedQuests=new ListTag();
            merchantAccepted.forEach(x->acceptedQuests.add(StringTag.valueOf(x)));merchantCompleted.forEach(x->completedQuests.add(StringTag.valueOf(x)));
            n.put("merchantAccepted",acceptedQuests);n.put("merchantCompleted",completedQuests);
            if(dailyInitialized) {
                ListTag board=new ListTag();dailyMissions.forEach(m->board.add(m.save()));n.put("dailyMissions",board);
            }
            ListTag history=new ListTag(),appearances=new ListTag(),recent=new ListTag(),contents=new ListTag();
            issuedMissions.stream().sorted().forEach(x->history.add(StringTag.valueOf(x)));issuedAppearances.stream().sorted().forEach(x->appearances.add(StringTag.valueOf(x)));
            issuedContents.stream().sorted().forEach(x->contents.add(StringTag.valueOf(x)));n.put("issuedContents",contents);
            recentObjectives.forEach(x->recent.add(StringTag.valueOf(x)));n.put("issuedMissions",history);n.put("issuedAppearances",appearances);n.put("recentObjectives",recent);return n;
        }
        public CompoundTag clientSnapshot() {
            CompoundTag n=new CompoundTag();n.putBoolean("satellite",satellite);n.putBoolean("deterrence",deterrence);n.putBoolean("noahComplete",noahComplete);
            n.putLong("nextRaid",nextRaid);n.putLong("jamUntil",jamUntil);n.putInt("satellitesLaunched",satellitesLaunched);return n;
        }
        public static State load(CompoundTag n) {
            State s=new State();s.day=n.contains("day")?n.getLong("day"):-1;s.rotation=n.getInt("rotation");
            s.accepted=n.getInt("accepted");s.claimed=n.getInt("claimed");
            int[] p=n.getIntArray("progress");if(p.length!=3)throw new IllegalArgumentException("Invalid campaign progress length");
            System.arraycopy(p,0,s.progress,0,3);s.nextRaid=n.getLong("nextRaid");s.nextInterference=n.getLong("nextInterference");s.jamUntil=n.getLong("jamUntil");
            s.satellite=n.getBoolean("satellite");s.deterrence=n.getBoolean("deterrence");s.noahComplete=n.getBoolean("noahComplete");
            s.uplinkUntil=Math.max(0,n.getLong("uplinkUntil"));s.deterrenceUntil=Math.max(0,n.getLong("deterrenceUntil"));
            s.satellitesLaunched=n.contains("satellitesLaunched")?Math.max(0,n.getInt("satellitesLaunched")):(s.satellite?1:0);
            s.noahDimension=n.getString("noahDimension");s.noahAnchor=n.getLong("noahAnchor");
            for(Tag t:n.getList("targets",Tag.TAG_STRING))s.targets.add(UUID.fromString(t.getAsString()));
            for(Tag t:n.getList("milestones",Tag.TAG_STRING))s.milestones.add(t.getAsString());
            for(Tag t:n.getList("merchantAccepted",Tag.TAG_STRING))s.merchantAccepted.add(t.getAsString());
            for(Tag t:n.getList("merchantCompleted",Tag.TAG_STRING))s.merchantCompleted.add(t.getAsString());
            s.dailyInitialized=n.contains("dailyMissions",Tag.TAG_LIST);List<Mission> board=new ArrayList<>();for(Tag t:n.getList("dailyMissions",Tag.TAG_COMPOUND))board.add(Mission.load((CompoundTag)t));s.dailyMissions=List.copyOf(board);
            for(Tag t:n.getList("issuedMissions",Tag.TAG_STRING))s.issuedMissions.add(t.getAsString());
            for(Tag t:n.getList("issuedAppearances",Tag.TAG_STRING))s.issuedAppearances.add(t.getAsString());
            for(Tag t:n.getList("issuedContents",Tag.TAG_STRING))s.issuedContents.add(t.getAsString());
            // Legacy saves retain only the current board's full payload, not the contents of older boards.
            for(Mission mission:s.dailyMissions)s.issuedContents.add(mission.contentKey());
            for(Tag t:n.getList("recentObjectives",Tag.TAG_STRING))s.recentObjectives.add(t.getAsString());s.validate();return s;
        }
    }
    public static void refresh(State s,UUID player,long tick) {
        long day=Math.max(0,tick/Economy.DAY_TICKS);
        refreshForDay(s,player,day);
    }
    public static void refreshForDay(State s,UUID player,long day) {
        if(day>s.day) {
            if(DailyMissions.catalog()!=null){s.dailyMissions=DailyMissions.draw(s,player,day);s.dailyInitialized=true;}
            s.day=day;s.rotation=Math.floorMod(player.hashCode()+Long.hashCode(day),MISSIONS.size());s.accepted=0;s.claimed=0;
            Arrays.fill(s.progress,0);s.targets.clear();}
    }
    public static int missionCount(State s){return s.day<0?0:s.dailyInitialized?s.dailyMissions.size():3;}
    public static Mission mission(State s,int slot) {
        if(slot<0 || slot>=missionCount(s))throw new IllegalArgumentException("Unknown mission");
        return s.dailyInitialized?s.dailyMissions.get(slot):MISSIONS.get((s.rotation+slot)%MISSIONS.size());
    }
    public static String token(State s,int slot) {return mission(s,slot).id()+"@"+s.day;}
    public static void accept(State s,int slot,String token) {
        verify(s,slot,token);if((s.claimed&(1<<slot))!=0)throw new IllegalArgumentException("受領済みです");s.accepted|=1<<slot;
    }
    public static void verify(State s,int slot,String token) {
        s.validate();if(!token(s,slot).equals(token))throw new IllegalArgumentException("依頼が更新されています");
    }
    public static boolean credit(State s,Kind kind,UUID target) {
        return credit(s,kind,target,"");
    }
    public static boolean credit(State s,Kind kind,UUID target,String entityId) {
        if(kind==Kind.DELIVERY)return false;
        boolean eligible=false;for(int i=0;i<missionCount(s);i++)if(matches(mission(s,i),kind,entityId) && (s.accepted&(1<<i))!=0 && (s.claimed&(1<<i))==0 && s.progress[i]<mission(s,i).count())eligible=true;
        if(!eligible || s.targets.size()>=4096 || !s.targets.add(target))return false;
        for(int i=0;i<missionCount(s);i++)if(matches(mission(s,i),kind,entityId) && (s.accepted&(1<<i))!=0 && (s.claimed&(1<<i))==0 && s.progress[i]<mission(s,i).count()) {
            s.progress[i]=Math.min(mission(s,i).count(),s.progress[i]+1);if(s.dailyInitialized)break;
        }
        return true;
    }
    private static boolean matches(Mission m,Kind kind,String entityId){return m.kind()==kind&&(m.entities().isEmpty()||m.entities().contains(entityId));}
    public static Economy.State claim(State s,Economy.State account,int slot,String token,int deliveryCount) {
        if(mission(s,slot).requirements.size()>1)throw new IllegalArgumentException("納品物を品目ごとに確認してください");
        return claim(s,account,slot,token,mission(s,slot).kind==Kind.DELIVERY?List.of(deliveryCount):List.of());
    }
    public static Economy.State claim(State s,Economy.State account,int slot,String token,List<Integer> deliveryCounts) {
        verify(s,slot,token);Mission m=mission(s,slot);
        if((s.accepted&(1<<slot))==0)throw new IllegalArgumentException("先に依頼を受注してください");
        if((s.claimed&(1<<slot))!=0)throw new IllegalArgumentException("受領済みです");
        if(m.kind==Kind.DELIVERY){
            if(deliveryCounts.size()!=m.requirements.size())throw new IllegalArgumentException("納品物の確認に失敗しました");
            for(int i=0;i<deliveryCounts.size();i++)if(deliveryCounts.get(i)<m.requirements.get(i).count)throw new IllegalArgumentException("必要な物資が不足しています");
        }else if(s.progress[slot]<m.count())throw new IllegalArgumentException("達成条件が不足しています");
        if(account.balance>Economy.MAX_MONEY-m.reward())throw new IllegalArgumentException("残高上限です");
        Economy.State after=account.copy();after.balance+=m.reward();Economy.rewardStanding(after,m.merchant(),5);s.claimed|=1<<slot;return after;
    }
    public static int raidTier(int alert) {return alert>=85?3:alert>=60?2:alert>=30?1:0;}
    public static boolean satelliteOnline(State c,long tick){return c.satellite && c.uplinkUntil>0 && c.uplinkUntil>=tick && c.uplinkUntil-tick<=100;}
    public static boolean deterrenceOnline(State c,long tick){return c.deterrence && c.deterrenceUntil>0 && c.deterrenceUntil>=tick && c.deterrenceUntil-tick<=100;}
    public static int militaryRank(Economy.State e){int r=Math.max(Economy.standing(e,Economy.Merchant.EAST),Economy.standing(e,Economy.Merchant.WEST));return r>=60?3:r>=25?2:1;}
    public static boolean signalLost(State c,Economy.State e,long tick) {return !satelliteOnline(c,tick) && (e.jammed() || tick<c.jamUntil);}
}
