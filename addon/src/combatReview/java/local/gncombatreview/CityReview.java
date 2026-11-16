package local.gncombatreview;

import com.google.gson.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import mcjty.lostcities.LostCities;
import mcjty.lostcities.LostCitiesImp;
import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import mcjty.lostcities.worldgen.lost.City;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Samples the installed transformed generator in the existing isolated world, without a client. */
final class CityReview {
    private record Candidate(LostCityProfile profile,IDimensionInfo provider){}
    private final List<Candidate> candidates=new ArrayList<>();
    private final List<Map<String,Object>> rows=new ArrayList<>();
    private final int[] cities=new int[6];
    private int sample;
    private boolean done;
    private long started;
    void tick(MinecraftServer server) {
        if(done)return;
        try {
            var level=server.overworld();
            if(!server.getWorldData().getLevelName().equals("GN_Review_r39_creation"))throw new IllegalStateException("Owned review world required");
            if(candidates.isEmpty())initialize(level);
            long budget=System.nanoTime()+25_000_000L;
            do {sample(level);}while(sample<400&&System.nanoTime()<budget);
            if(sample==400)finish(level);
        }catch(Throwable error){done=true;write(Map.of("status","FAIL","error",error.toString(),"screenUsed",false));error.printStackTrace();}
    }
    private void initialize(ServerLevel level)throws Exception{
        started=System.nanoTime();
        var nativeInfo=((LostCitiesImp.LostCityInformation)LostCities.lostCitiesImp.getLostInfo(level)).dimensionInfo();
        String text=Files.readString(Path.of("config/lostcities/profiles/gn_azzz15flat_nature.json"));
        double[] chances={.00005,.0001,.000125,.00015,.000175,.0002};
        int[] low={50,35,35,35,35,35},high={130,200,200,200,200,200};
        for(int i=0;i<chances.length;i++){
            var json=JsonParser.parseString(text).getAsJsonObject();var city=json.getAsJsonObject("cities");
            city.addProperty("cityChance",chances[i]);city.addProperty("cityMinHeight",low[i]);city.addProperty("cityMaxHeight",high[i]);
            var profile=new LostCityProfile("gn-coverage-"+i,json.toString());
            IDimensionInfo info=(IDimensionInfo)Proxy.newProxyInstance(IDimensionInfo.class.getClassLoader(),new Class<?>[]{IDimensionInfo.class},(p,m,a)->switch(m.getName()){
                case "getProfile","getOutsideProfile"->profile;
                case "hashCode"->System.identityHashCode(p);
                case "equals"->p==a[0];
                case "toString"->profile.getName();
                default->m.invoke(nativeInfo,a);
            });
            candidates.add(new Candidate(profile,info));
        }
    }
    private void sample(ServerLevel level){
        int grid=20,cell=32768/grid;var random=new Random(0x474e3830L+sample*7919L);
        int bx=-16384+(sample%grid)*cell+random.nextInt(cell),bz=-16384+(sample/grid)*cell+random.nextInt(cell);
        var coord=new ChunkCoord(level.dimension(),bx>>4,bz>>4);var nativeInfo=((LostCitiesImp.LostCityInformation)LostCities.lostCitiesImp.getLostInfo(level)).dimensionInfo();
        int height=nativeInfo.getHeightmap(coord).getHeight();var flags=new ArrayList<Boolean>();
        for(int i=0;i<candidates.size();i++){
            var c=candidates.get(i);boolean city=BuildingInfo.isCityRaw(coord,c.provider(),c.profile());flags.add(city);if(city)cities[i]++;
        }
        rows.add(Map.of("chunkX",coord.chunkX(),"chunkZ",coord.chunkZ(),"naturalHeight",height,"city",flags));sample++;
        if(sample%40==0)System.out.println("GN_CITY_SURVEY samples="+sample+" cities="+Arrays.toString(cities));
    }
    private void finish(ServerLevel level){
        var results=new ArrayList<Map<String,Object>>();
        for(int i=0;i<candidates.size();i++){var p=candidates.get(i).profile();results.add(Map.of("index",i,"chance",p.CITY_CHANCE,"minHeight",p.CITY_MINHEIGHT,"maxHeight",p.CITY_MAXHEIGHT,"citySamples",cities[i],"fraction",cities[i]/400.0));}
        done=true;write(Map.of("status","PASS","method","Installed Lost Cities + LC2H transformed BuildingInfo.isCityRaw with actual Terralith/Arboria world, stratified deterministic samples", "sampleCount",sample,"extentBlocks",32768,"seed",level.getSeed(),"candidates",results,"samples",rows,"elapsedSeconds",(System.nanoTime()-started)/1e9,"screenUsed",false));
    }
    private void write(Object result){try{Files.writeString(Path.of(System.getProperty("gn.combatEvidence")),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception error){throw new RuntimeException(error);}}
}
