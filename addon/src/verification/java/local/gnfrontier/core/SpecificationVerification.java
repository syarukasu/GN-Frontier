package local.gnfrontier.core;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;

/** Independent acceptance conditions for migration, objective identity and dawn timing. */
public final class SpecificationVerification {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        var clock=new DailyCycle();clock.observe(100,23998,7);check(clock.day()==7&&clock.remaining()==2,"Migration retains current board until the next dawn");
        clock.observe(101,23999,0);check(clock.day()==7&&clock.remaining()==1,"Last night tick has not rolled");
        clock.observe(102,24000,0);check(clock.day()==8&&clock.remaining()==24000,"Natural dawn rolls once");
        clock.observe(102,24000,0);check(clock.day()==8,"Multiple polls never advance a day");
        clock.observe(103,48000,0);check(clock.day()==8,"Forward time command cannot reroll a board");
        clock.observe(104,0,0);check(clock.day()==8,"Backward time command cannot repeat an old board");
        clock.observe(24104,0,0);check(clock.day()==8,"Paused daylight does not expire quests");
        clock=DailyCycle.load(clock.save());clock.observe(48104,24000,0);check(clock.day()==9,"Restart followed by a natural dawn renews contracts");
        var account=new Economy.State();check(FactionStanding.relation(account,0)==FactionStanding.Relation.NEUTRAL,"Initial regular army relation");
        account.reputation[0]=30;check(FactionStanding.relation(account,0)==FactionStanding.Relation.ALLY,"Standing grants alliance");
        account.alert[0]=30;check(FactionStanding.relation(account,0)==FactionStanding.Relation.ENEMY,"High alert overrides alliance");
        account.alert[0]=0;account.reputation[0]=-20;check(FactionStanding.relation(account,0)==FactionStanding.Relation.ENEMY,"Bad standing is hostile without a current combat target");
        var catalog=DailyMissions.parse(JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject());DailyMissions.install(catalog);
        var state=new Campaign.State();var player=new UUID(0x123456789abcdefL,0x123456789abcdefL);Set<String> seen=new HashSet<>();
        int deliveries=0;
        for(int day=0;day<365;day++){
            Campaign.refreshForDay(state,player,day);
            for(var mission:state.dailyMissions){check(seen.add(mission.contentKey()),"No repeat of requester, target and quantity over 365 days");if(mission.kind()==Campaign.Kind.DELIVERY)deliveries++;}
            if(day%17==0){var saved=state.save();state=Campaign.State.load(saved);check(saved.equals(state.save()),"Canonical histories survive restart exactly");}
        }
        check(deliveries>500,"Fresh mixed deliveries replace exhausted combat contents");
        var active=state.dailyMissions.get(0);var legacy=state.save();legacy.remove("issuedContents");var migrated=Campaign.State.load(legacy);
        check(migrated.issuedContents.contains(active.contentKey())&&migrated.dailyMissions.equals(state.dailyMissions),"Legacy active board and its identity are retained");
        System.out.println("GN_SPECIFICATION_PASS checks="+checks+" days=365 deliveries="+deliveries);
    }
}
