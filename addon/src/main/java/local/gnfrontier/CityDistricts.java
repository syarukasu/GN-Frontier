package local.gnfrontier;

import java.util.*;
import local.gnfrontier.core.CityCentreDraw;
import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.lost.City;
import mcjty.lostcities.worldgen.lost.cityassets.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** City extent remains native. The strongest actual city centre owns a continuous district. */
@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class CityDistricts {
    public record Centre(int x,int z,float radius){}
    private record Tile(ResourceKey<Level> dimension,long seed,double chance,int minRadius,int maxRadius,int x,int z){}
    private static final int SIZE=128,MAX_TILES=256;
    private static final Map<Tile,List<Centre>> TILES=Collections.synchronizedMap(new LinkedHashMap<>(64,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Tile,List<Centre>> entry){return size()>MAX_TILES;}
    });
    public static boolean enabled(IDimensionInfo info,LostCityProfile profile){return info.getWorld()!=null&&info.getType()==Level.OVERWORLD&&profile.CITY_CHANCE>=0&&profile.getWorldStyle().equals("gnfrontier:ruined_city");}
    public static Centre centre(ChunkCoord coord,IDimensionInfo info,LostCityProfile profile){
        Tile key=new Tile(info.getType(),info.getSeed(),profile.CITY_CHANCE,profile.CITY_MINRADIUS,profile.CITY_MAXRADIUS,Math.floorDiv(coord.chunkX(),SIZE),Math.floorDiv(coord.chunkZ(),SIZE));
        List<Centre> centres=TILES.get(key);
        if(centres==null){centres=scan(key,info);TILES.put(key,centres);}
        Centre best=null;double score=0;
        for(Centre centre:centres){
            double dx=(coord.chunkX()-(double)centre.x())*16,dz=(coord.chunkZ()-(double)centre.z())*16;
            double strength=1-Math.sqrt(dx*dx+dz*dz)/centre.radius();
            if(strength>score){score=strength;best=centre;}
        }
        return best;
    }
    private static List<Centre> scan(Tile tile,IDimensionInfo info){
        int radius=(tile.maxRadius()+15)/16,x0=tile.x()*SIZE-radius,z0=tile.z()*SIZE-radius,x1=(tile.x()+1)*SIZE-1+radius,z1=(tile.z()+1)*SIZE-1+radius;
        Map<Long,Centre> centres=new TreeMap<>();
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            // Installed Lost Cities 7.5.7 normal-city draw. Confirm the rare candidates with the native API;
            // do not populate LC2H's per-chunk caches with every rejected coordinate.
            if(CityCentreDraw.draw(x,z)>=tile.chance())continue;
            var coord=new ChunkCoord(info.getType(),x,z);
            if(City.isCityCenter(coord,info))centres.put(((long)x<<32)^(z&0xffffffffL),new Centre(x,z,City.getCityRadius(coord,info)));
        }
        for(var predefined:AssetRegistries.PREDEFINED_CITIES.getIterable()){
            int x=predefined.getChunkX(),z=predefined.getChunkZ();
            if(predefined.getDimension()==info.getType()&&x>=x0&&x<=x1&&z>=z0&&z<=z1)centres.put(((long)x<<32)^(z&0xffffffffL),new Centre(x,z,predefined.getRadius()));
        }
        return List.copyOf(centres.values());
    }
    public static String style(ChunkCoord coord,IDimensionInfo info,LostCityProfile profile){
        Centre centre=centre(coord,info,profile);if(centre==null)return null;
        String style=City.getCityStyleForCityCenter(new ChunkCoord(info.getType(),centre.x(),centre.z()),info);
        if(City.getCityFactor(coord,info,profile)<profile.CITY_STYLE_THRESHOLD){
            style=switch(style){case "gnfrontier:residential_city"->"gnfrontier:residential_edge";case "gnfrontier:industrial_city"->"gnfrontier:industrial_edge";case "gnfrontier:military_city"->"gnfrontier:military_edge";default->style;};
        }
        return style;
    }
    public static CityStyle resolve(ChunkCoord coord,IDimensionInfo info,LostCityProfile profile){
        if(!enabled(info,profile))return null;String id=style(coord,info,profile);return id==null?null:AssetRegistries.CITYSTYLES.getOrThrow(info.getWorld(),id);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){TILES.clear();}
}
