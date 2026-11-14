package local.gnfrontier.core;

import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Offline city-footprint survey. Never creates a server, level or save. */
public final class WorldgenCoverage {
    private static final int MIN = -1024, MAX = 1024, STEP = 4;
    private record Centre(int x, int z, float radius) {}

    public static void main(String[] args) throws Exception {
        Class<?> profileClass = Class.forName("mcjty.lostcities.config.LostCityProfile");
        Class<?> dimensionClass = Class.forName("mcjty.lostcities.worldgen.IDimensionInfo");
        Class<?> coordClass = Class.forName("mcjty.lostcities.varia.ChunkCoord");
        Class<?> cityClass = Class.forName("mcjty.lostcities.worldgen.lost.City");
        Constructor<?> coordConstructor = coordClass.getConstructors()[0];
        Method isCenter = cityClass.getMethod("isCityCenter", coordClass, dimensionClass);
        Method getRadius = cityClass.getMethod("getCityRadius", coordClass, dimensionClass);
        // There are no predefined assets in this survey. Empty caches avoid accessing a world.
        for (String field : List.of("predefinedCityMap", "predefinedBuildingMap", "predefinedStreetMap")) {
            Field f = cityClass.getDeclaredField(field); f.setAccessible(true); f.set(null, Map.of());
        }
        JsonArray report = new JsonArray();
        String baseJson = Files.readString(Path.of(args[0]));
        double[] chances = {0.00001, 0.00004, 0.000045, 0.00005, 0.000055, 0.00006};
        int samples = (MAX - MIN) / STEP, margin = (2000 + 15) / 16;
        Object[] active = new Object[1];
        Object dimension = Proxy.newProxyInstance(dimensionClass.getClassLoader(), new Class<?>[]{dimensionClass}, (p,m,a) -> {
            return switch (m.getName()) {
                case "getProfile", "getOutsideProfile" -> active[0];
                case "getWorld", "getType", "dimension" -> null;
                case "getSeed" -> 47120420L;
                case "hashCode" -> 1;
                case "equals" -> p == a[0];
                case "toString" -> "offline-city-footprint-survey";
                default -> throw new UnsupportedOperationException("Unexpected world access: " + m.getName());
            };
        });
        // Centre locations do not depend on world seed on this installed 7.5.7 code path.
        // Query the actual mod once at the highest candidate probability, then filter using
        // its same Java Random result. Validate that formula against every returned centre.
        JsonObject highest = JsonParser.parseString(baseJson).getAsJsonObject();
        highest.getAsJsonObject("cities").addProperty("cityChance", chances[chances.length-1]);
        active[0] = profileClass.getConstructor(String.class, String.class).newInstance("coverage", highest.toString());
        List<Centre> allCentres = new ArrayList<>();
        Map<Centre, Double> draws = new HashMap<>();
        for (int x=MIN-margin; x<MAX+margin; x++) for (int z=MIN-margin; z<MAX+margin; z++) {
            Object coord = coordConstructor.newInstance(null, x, z);
            if ((boolean)isCenter.invoke(null, coord, dimension)) {
                Centre c = new Centre(x,z,((Number)getRadius.invoke(null,coord,dimension)).floatValue());
                double draw = new Random(z*797003437L+x*295075153L).nextDouble();
                if (draw >= chances[chances.length-1]) throw new AssertionError("Installed centre formula differs");
                allCentres.add(c); draws.put(c,draw);
            }
        }
        for (double chance : chances) {
            float[] factors = new float[samples*samples]; int centers = 0;
            for (Centre c : allCentres) {
                if (draws.get(c)>=chance) continue;
                centers++;
                int radiusChunks=(int)Math.ceil(c.radius/16);
                int x0=Math.max(0,Math.floorDiv(c.x-radiusChunks-MIN,STEP));
                int x1=Math.min(samples-1,Math.floorDiv(c.x+radiusChunks-MIN,STEP));
                int z0=Math.max(0,Math.floorDiv(c.z-radiusChunks-MIN,STEP));
                int z1=Math.min(samples-1,Math.floorDiv(c.z+radiusChunks-MIN,STEP));
                for (int ix=x0;ix<=x1;ix++) for (int iz=z0;iz<=z1;iz++) {
                    int dx=(MIN+ix*STEP-c.x)*16,dz=(MIN+iz*STEP-c.z)*16;
                    float d2=dx*dx+dz*dz;
                    if (d2<c.radius*c.radius) factors[ix*samples+iz]+=(c.radius-(float)Math.sqrt(d2))/c.radius;
                }
            }
            int urban=0,suburban=0,core=0;
            for(float f:factors) {
                if(f>0.1f) {urban++;if(f<0.4f)suburban++;else core++;}
            }
            JsonObject row=new JsonObject();row.addProperty("cityChance",chance);
            row.addProperty("centres_including_margin",centers);
            row.addProperty("eligible_terrain_city_fraction",urban/(double)factors.length);
            row.addProperty("suburb_fraction",suburban/(double)factors.length);
            row.addProperty("core_fraction",core/(double)factors.length);
            row.addProperty("sample_points",factors.length);
            report.add(row);System.out.println(row);
        }
        JsonObject out=new JsonObject();out.addProperty("method","Installed Lost Cities isCityCenter/getCityRadius plus its radial sum; flat eligible terrain only");
        out.addProperty("min_block",MIN*16);out.addProperty("max_block_exclusive",MAX*16);
        out.addProperty("sample_step_blocks",STEP*16);out.addProperty("world_generated",false);
        out.addProperty("excludes","Terrain height, biome multipliers, villages, structure avoidance, void, predefined buildings and final street placement");
        out.add("candidates",report);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(out));
        System.out.println("GN_WORLDGEN_SURVEY_COMPLETE worldGenerated=false");
    }
}
