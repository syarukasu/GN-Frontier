import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import local.gnfrontier.core.*;

/** Read-only diagnostic against compiled core classes. No game launch or save access. */
class QuestRepeatProbe {
    record Seen(int day, String id, String title, String description) {}
    public static void main(String[] args) throws Exception {
        DailyMissions.install(DailyMissions.parse(JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject()));
        var state = new Campaign.State();
        var player = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        Map<String,Seen> seen = new HashMap<>();
        int repeats = 0;
        for (int day=0; day<365; day++) {
            for (var m : DailyMissions.draw(state,player,day)) {
                if (m.kind()!=Campaign.Kind.DELIVERY) continue;
                String key=m.merchant()+"|"+String.join(";",m.requirements().stream().map(r->r.key()+"="+r.count()).toList());
                var prior=seen.putIfAbsent(key,new Seen(day,m.id(),m.title(),m.description()));
                if(prior!=null && ++repeats<=3) {
                    System.out.println("Same merchant, items and quantities: "+key);
                    System.out.println("days="+prior.day()+","+day+"; differentIds="+!prior.id().equals(m.id())+"; differentDescriptions="+!prior.description().equals(m.description()));
                    System.out.println("titles="+prior.title()+" / "+m.title());
                }
            }
        }
        System.out.println("365 days; repeated delivery objectives="+repeats+"; unique="+seen.size());
    }
}
