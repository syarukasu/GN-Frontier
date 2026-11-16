package local.gnfrontier.core;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Real NBT, finite-pool exhaustion, migration, identity and accounting checks. */
public final class DailyMissionVerification {
    private static int checks;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private static void reject(Runnable task){checks++;try{task.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid daily operation accepted");}
    public static void main(String[] args)throws Exception {
        JsonObject document=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        var catalog=DailyMissions.parse(document);UUID player=UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        Campaign.State legacy=new Campaign.State();Campaign.refresh(legacy,player,0);String legacyToken=Campaign.token(legacy,0);Campaign.accept(legacy,0,legacyToken);
        CompoundTag old=legacy.save();old.remove("issuedMissions");old.remove("issuedAppearances");old.remove("recentObjectives");
        legacy=Campaign.State.load(old);DailyMissions.install(catalog);Campaign.refresh(legacy,player,0);
        check(!legacy.dailyInitialized&&Campaign.token(legacy,0).equals(legacyToken)&&legacy.accepted==1,"Existing day's mission and acceptance remain intact");
        Campaign.refresh(legacy,player,24000);check(legacy.dailyInitialized&&legacy.accepted==0,"Next day migrates legacy board");
        Set<String> catalogIds=new HashSet<>(),appearances=new HashSet<>(),contents=new HashSet<>();Set<Campaign.Kind> kinds=new HashSet<>();
        for(int i=0;i<catalog.variants();i++) {
            var m=DailyMissions.variant(catalog,i);m.validate();check(catalogIds.add(m.id()),"Catalog identities unique");check(appearances.add(m.appearance()),"Visible contract signatures unique");kinds.add(m.kind());
            contents.add(m.contentKey());
            check(!m.description().contains("{")&&!m.title().contains("{"),"Placeholders fully resolved");
            check(Campaign.Mission.load(m.save()).equals(m),"Every issued payload round trips typed NBT");
            check(!m.description().contains("ノア"),"Merchant wording does not imply Noah knowledge");
        }
        check(kinds.equals(EnumSet.allOf(Campaign.Kind.class)),"All five mission kinds present");
        Campaign.State state=new Campaign.State();Set<String> seen=new HashSet<>(),seenAppearance=new HashSet<>(),seenContents=new HashSet<>();
        int day=0;long started=System.nanoTime();
        while(seenContents.size()<contents.size()&&day<catalog.variants()+2) {
            Campaign.refresh(state,player,(long)day*24000);
            Set<String> objectives=new HashSet<>();
            for(var m:state.dailyMissions){check(seen.add(m.id()),"No issued combination ever repeats");check(seenAppearance.add(m.appearance()),"No identical visible contract repeats");check(seenContents.add(m.contentKey()),"No wording-only repeat of an objective");check(objectives.add(m.objective()),"No same-target duplicates on one board");}
            if(day<3){check(state.dailyMissions.stream().anyMatch(m->m.kind()==Campaign.Kind.DELIVERY),"Early boards include delivery");check(state.dailyMissions.stream().anyMatch(m->m.kind()==Campaign.Kind.HUNT||m.kind()==Campaign.Kind.SOLDIER),"Early boards include ground hunt");}
            if(day%127==0){CompoundTag saved=state.save();state=Campaign.State.load(saved);check(state.save().equals(saved),"Board/history survives actual NBT");}
            if(day==100)DailyMissions.install(DailyMissions.parse(document));
            day++;
        }
        check(seenContents.equals(contents),"Every unique objective can be reached without repeating its wording variants");
        Campaign.refresh(state,player,(long)day*24000);check(Campaign.missionCount(state)==0,"Exhausted catalog never silently repeats");
        check(!state.clientSnapshot().contains("issuedMissions")&&!state.clientSnapshot().contains("issuedAppearances"),"History is never sent in PDA polling snapshots");
        var full=Campaign.State.load(state.save());Campaign.refresh(full,player,0);check(full.day==day&&full.dailyMissions.isEmpty(),"Clock rewind cannot refill exhausted pool");
        Campaign.State first=new Campaign.State(),same=new Campaign.State(),other=new Campaign.State();Campaign.refresh(first,player,0);Campaign.refresh(same,player,0);Campaign.refresh(other,new UUID(1,2),0);
        check(first.dailyMissions.equals(same.dailyMissions),"Same initial UUID/day is stable");check(!first.dailyMissions.equals(other.dailyMissions),"Other players have independent boards");
        String token=Campaign.token(first,0);Campaign.accept(first,0,token);var accepted=first.save();Campaign.refresh(first,player,1);
        check(first.save().equals(accepted),"Reopen/refresh does not reroll an accepted mission");DailyMissions.install(DailyMissions.parse(document));check(first.save().equals(accepted),"Reload keeps issued count/reward/text snapshots");
        for(Campaign.Kind kind:Campaign.Kind.values()) {
            Campaign.Mission m=null;for(int i=0;i<catalog.variants();i++){var candidate=DailyMissions.variant(catalog,i);if(candidate.kind()==kind){m=candidate;break;}}
            Campaign.State s=new Campaign.State();s.day=0;s.dailyInitialized=true;s.dailyMissions=List.of(m);s.issuedMissions.add(m.id());s.issuedAppearances.add(m.appearance());
            final Campaign.Mission mission=m;String t=Campaign.token(s,0);Economy.State money=new Economy.State();
            reject(()->Campaign.claim(s,money,0,t,mission.count()));check(!Campaign.credit(s,kind,UUID.randomUUID(),m.entities().isEmpty()?"":m.entities().get(0)),"Unaccepted kills ignored");
            Campaign.accept(s,0,t);reject(()->Campaign.accept(s,0,t+"old"));
            if(kind==Campaign.Kind.DELIVERY)reject(()->Campaign.claim(s,money,0,t,mission.count()-1));
            else {
                if(!m.entities().isEmpty())check(!Campaign.credit(s,kind,UUID.randomUUID(),"minecraft:cow"),"Wrong entity rejected");
                String entity=m.entities().isEmpty()?"":m.entities().get(0);
                for(int i=0;i<m.count();i++){UUID victim=UUID.randomUUID();check(Campaign.credit(s,kind,victim,entity),"Correct target counted");check(!Campaign.credit(s,kind,victim,entity),"Same target UUID refused");}
                check(!Campaign.credit(s,kind,UUID.randomUUID(),entity),"Completed kill objective does not grow");
            }
            Economy.State capped=new Economy.State();capped.balance=Economy.MAX_MONEY;CompoundTag before=s.save();reject(()->Campaign.claim(s,capped,0,t,mission.count()));check(s.save().equals(before),"Cap refusal preserves progress and claim");
            Economy.State paid=Campaign.claim(s,money,0,t,m.count());check(paid.balance==m.reward()&&money.balance==0,"Exact reward paid once");
            Campaign.State saved=Campaign.State.load(s.save());reject(()->Campaign.claim(saved,paid,0,t,mission.count()));
            Campaign.refresh(saved,player,24000);reject(()->Campaign.accept(saved,0,t));
        }
        JsonObject bad=document.deepCopy();bad.getAsJsonArray("templates").get(0).getAsJsonObject().getAsJsonArray("targets").get(0).getAsJsonObject().getAsJsonArray("counts").set(0,new JsonPrimitive(0));reject(()->DailyMissions.parse(bad));
        System.out.println("GN_DAILY_MISSIONS_PASS checks="+checks+" variants="+catalog.variants()+" exhaustedAfterDays="+day+" fullPoolMs="+((System.nanoTime()-started)/1000000)+" runtimeServer=false");
        ModularDeliveryVerification.run(Path.of(args[0]));
    }
}
