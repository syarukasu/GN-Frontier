package local.gncombatreview;

import java.util.*;
import mcjty.lostcities.*;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import mcjty.lostcities.worldgen.lost.City;
import mcjty.lostcities.worldgen.lost.cityassets.AssetRegistries;
import local.gnfrontier.CityDistricts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.registries.ForgeRegistries;

final class CityGenerationChecks {
    static Map<String,Object> run(ServerLevel level)throws Exception{
        var api=(LostCitiesImp.LostCityInformation)LostCities.lostCitiesImp.getLostInfo(level);var provider=api.dimensionInfo();var profile=provider.getProfile();
        if(Math.abs(profile.CITY_CHANCE-.000125)>.00000001||profile.CITY_MINHEIGHT!=35||profile.CITY_MAXHEIGHT!=200)throw new IllegalStateException("Final city profile was not applied");
        var expected=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("config/gnfrontier-city-review.json"))).getAsJsonObject();
        int urban=0;Map<String,Integer> types=new TreeMap<>();
        for(var point:expected.getAsJsonArray("samples")){
            var row=point.getAsJsonObject();var coord=new ChunkCoord(level.dimension(),row.get("chunkX").getAsInt(),row.get("chunkZ").getAsInt());
            boolean city=BuildingInfo.isCityRaw(coord,provider,profile);
            if(city!=row.getAsJsonArray("city").get(2).getAsBoolean())throw new IllegalStateException("District styles changed the native urban footprint at "+coord);
            if(city){urban++;String style=City.getCityStyle(coord,provider,profile).getName();String direct=CityDistricts.style(coord,provider,profile);if(!style.equals(direct))throw new IllegalStateException("Native city style cache did not use current district: "+style+" vs "+direct);types.merge(style,1,Integer::sum);}
        }
        if(urban!=321||types.keySet().stream().filter(s->s.endsWith("_city")).count()!=3)throw new IllegalStateException("Expected 321/400 urban points and all three city styles: "+urban+" "+types);
        for(String asset:List.of("industrial_factory","industrial_green_factory","military_bunker","military_outpost"))AssetRegistries.MULTI_BUILDINGS.getOrThrow(level,"gnfrontier:"+asset);
        var facilities=new ArrayList<Map<String,Object>>();
        for(String district:List.of("industrial","military")){
            ChunkCoord selected=null;
            search:for(var point:expected.getAsJsonArray("samples")){
                var row=point.getAsJsonObject();var coord=new ChunkCoord(level.dimension(),row.get("chunkX").getAsInt(),row.get("chunkZ").getAsInt());
                if(!BuildingInfo.isCityRaw(coord,provider,profile)||!City.getCityStyle(coord,provider,profile).getName().equals("gnfrontier:"+district+"_city"))continue;
                for(int dx=-6;dx<=6;dx+=2)for(int dz=-6;dz<=6;dz+=2){
                    var c=coord.offset(dx,dz);String id=String.valueOf(api.getChunkInfo(c.chunkX(),c.chunkZ()).getBuildingId());
                    if(id.startsWith("gnfrontier:"+district+"_")){selected=c;break search;}
                }
            }
            if(selected==null)throw new IllegalStateException("No native facility plan for district "+district);
            var chunk=level.getChunk(selected.chunkX(),selected.chunkZ());
            String id=String.valueOf(api.getChunkInfo(selected.chunkX(),selected.chunkZ()).getBuildingId());
            facilities.add(Map.of("district",district,"chunkX",selected.chunkX(),"chunkZ",selected.chunkZ(),"building",id,"status",chunk.getStatus().toString()));
            System.out.println("GN_DISTRICT_FACILITY_GENERATED "+district+" "+id);
        }
        int bx=-1006,bz=-931;boolean previous=BuildingInfo.isCityRaw(new ChunkCoord(level.dimension(),bx,bz),provider,profile);int boundary=Integer.MIN_VALUE;
        for(int dx=1;dx<=256;dx++){
            boolean current=BuildingInfo.isCityRaw(new ChunkCoord(level.dimension(),bx+dx,bz),provider,profile);
            if(current!=previous){boundary=bx+dx;break;}previous=current;
        }
        if(boundary==Integer.MIN_VALUE)throw new IllegalStateException("No city/nature boundary on the fixed review transect");
        var rows=new ArrayList<Map<String,Object>>();int city=0,nature=0;long start=System.nanoTime();
        for(int dx=-2;dx<=2;dx++){
            int x=boundary+dx;var chunk=level.getChunk(x,bz);var info=api.getChunkInfo(x,bz);
            if(info.isCity())city++;else nature++;
            var materials=new TreeMap<String,Integer>();
            for(int lx=0;lx<16;lx+=3)for(int lz=0;lz<16;lz+=3){
                var pos=new BlockPos(x*16+lx,api.getRealHeight(info.getCityLevel()),bz*16+lz);
                String id=ForgeRegistries.BLOCKS.getKey(chunk.getBlockState(pos).getBlock()).toString();materials.merge(id,1,Integer::sum);
            }
            rows.add(Map.of("chunkX",x,"chunkZ",bz,"isCity",info.isCity(),"building",Objects.toString(info.getBuildingId(),"street_or_nature"),"status",chunk.getStatus().toString(),"groundSamples",materials));
            System.out.println("GN_CITY_GENERATED chunk="+x+","+bz+" city="+info.isCity());
        }
        if(city==0||nature==0)throw new IllegalStateException("Generated transect did not include both city and natural terrain");
        var result=new LinkedHashMap<String,Object>();result.put("status","PASS");result.put("chunks",rows);result.put("facilities",facilities);result.put("cityChunks",city);result.put("natureChunks",nature);result.put("profile",profile.getName());result.put("elapsedSeconds",(System.nanoTime()-start)/1e9);result.put("screenUsed",false);result.put("urbanSampleFraction",urban/400.0);result.put("districtCounts",types);result.put("globalSurfaceAreaMeasured",false);return result;
    }
}
