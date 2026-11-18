package local.gnreview;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import mcjty.lostcities.setup.Registration;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.*;
import mcjty.lostcities.worldgen.lost.*;
import mcjty.lostcities.worldgen.lost.cityassets.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.fml.ModList;

/** Keep manually placed algorithm fixtures separate from native FULL generation evidence. */
public final class WorldgenChecks {
    private static boolean c2me() { return ModList.get().isLoaded("c2meforge"); }

    /** Submit the same bounded cold region on both runs; completion never blocks the render thread. */
    private static Map<String,Object> batch(ServerPlayer player, Properties settings)throws Exception {
        var level=player.serverLevel();
        var output=Path.of(settings.getProperty("path")).resolveSibling("batch-"+(c2me()?"candidate":"baseline")+"-complete.json");
        if(Files.exists(output))throw new IllegalStateException("Fresh benchmark result required");
        var cpu=ManagementFactory.getThreadMXBean();
        var active=new java.util.concurrent.atomic.AtomicInteger();
        ThreadPoolExecutor pool=null;
        int configured=0;
        if(c2me()) {
            var globals=Class.forName("com.ishland.c2me.base.common.GlobalExecutors");
            configured=globals.getField("GLOBAL_EXECUTOR_PARALLELISM").getInt(null);
            pool=(ThreadPoolExecutor)globals.getField("executor").get(null);
            if(configured!=4)throw new AssertionError("Four configured C2ME workers required: "+configured);
        }
        final var executor=pool;final int parallelism=configured;
        var monitor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"GN-bounded-worldgen-check");t.setDaemon(true);return t;});
        monitor.scheduleAtFixedRate(()->{if(executor!=null)active.accumulateAndGet(executor.getActiveCount(),Math::max);},0,10,TimeUnit.MILLISECONDS);
        List<CompletableFuture<?>> futures=new ArrayList<>();
        long start=System.nanoTime();
        for(int z=528;z<532;z++)for(int x=528;x<532;x++) {
            var pos=new net.minecraft.world.level.ChunkPos(x,z);
            level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,pos,0,pos);
        }
        for(int z=528;z<532;z++)for(int x=528;x<532;x++)futures.add(level.getChunkSource().getChunkFuture(x,z,ChunkStatus.FULL,true));
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenCompleteAsync((ignored,failure)->{
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("c2meLoaded",c2me());result.put("workersConfigured",parallelism);result.put("peakActiveC2meWorkers",active.get());
            result.put("elapsedMs",(System.nanoTime()-start)/1_000_000);monitor.shutdown();
            try {
                if(failure!=null)throw new RuntimeException(failure);
                for(var future:futures) {
                    var value=(com.mojang.datafixers.util.Either<?,?>)future.join();
                    var generated=value.left().orElseThrow(()->new AssertionError("A requested FULL future returned unloaded"));
                    if(!(generated instanceof net.minecraft.world.level.chunk.ChunkAccess access)||!access.getStatus().isOrAfter(ChunkStatus.FULL))throw new AssertionError("A FULL future returned an incomplete chunk");
                }
                var provider=Registration.LOSTCITY_FEATURE.get().getDimensionInfo(level);
                List<Map<String,Object>> rows=new ArrayList<>();
                for(int z=528;z<532;z++)for(int x=528;x<532;x++) {
                    var chunk=level.getChunk(x,z);
                    if(!chunk.getStatus().isOrAfter(ChunkStatus.FULL))throw new AssertionError("Incomplete requested chunk");
                    var info=BuildingInfo.getBuildingInfo(new ChunkCoord(level.dimension(),x,z),provider);
                    rows.add(Map.of("x",x,"z",z,"status",chunk.getStatus().toString(),"city",info.isCity,"building",info.hasBuilding,"style",info.getCityStyle().getName()));
                }
                var pos=new BlockPos(528*16+2,318,528*16+2);
                level.setBlock(pos,Blocks.CHEST.defaultBlockState(),3);
                var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(pos);
                var token=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER,7);
                token.getOrCreateTag().putString("gn_review_token","c2me-region-16-16");chest.setItem(0,token);chest.setChanged();
                long saveStart=System.nanoTime();player.getServer().saveEverything(true,true,true);
                result.put("saveMs",(System.nanoTime()-saveStart)/1_000_000);
                result.put("chest",Map.of("x",pos.getX(),"y",pos.getY(),"z",pos.getZ(),"item","minecraft:paper","count",7,"token","c2me-region-16-16"));
                result.put("rows",rows);result.put("profile",provider.getProfile().getName());result.put("worldStyle",provider.getProfile().getWorldStyle());
                List<Map<String,Object>> workers=new ArrayList<>();
                for(var t:Thread.getAllStackTraces().keySet())if(t.getName().startsWith("C2ME worker #"))workers.add(Map.of("name",t.getName(),"cpuNanos",cpu.getThreadCpuTime(t.getId())));
                result.put("workers",workers);
                if(c2me()&&(active.get()<2||workers.size()<2))throw new AssertionError("Parallel workers were not observed");
                result.put("status","PASS");
            }catch(Throwable e){result.put("status","ERROR");result.put("error",e.toString());}
            finally {
                for(int z=528;z<532;z++)for(int x=528;x<532;x++) {
                    var pos=new net.minecraft.world.level.ChunkPos(x,z);
                    level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED,pos,0,pos);
                }
            }
            try {Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){throw new RuntimeException(e);}
        },player.getServer());
        return Map.of("queued",true,"c2meLoaded",c2me(),"requestedChunks",16,"completeFile",output.getFileName().toString(),"processorHint",Runtime.getRuntime().availableProcessors());
    }
    private static void set(Object target,String name,Object value)throws Exception {
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try {var f=c.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static BuildingInfo copy(BuildingInfo source)throws Exception {
        var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        var target=(BuildingInfo)((sun.misc.Unsafe)f.get(null)).allocateInstance(BuildingInfo.class);
        for(Class<?> c=source.getClass();c!=null;c=c.getSuperclass())for(var field:c.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())){field.setAccessible(true);field.set(target,field.get(source));}
        return target;
    }
    public static Map<String,Object> server(ServerPlayer player,String action,Properties settings)throws Exception {
        var level=player.serverLevel();
        if(!player.getServer().getWorldData().getLevelName().equals("GN_Review_r39_creation"))throw new IllegalStateException("Owned review world required");
        if(!ModList.get().isLoaded("noisium"))throw new AssertionError("Noisium missing");
        if(action.equals("batch"))return batch(player,settings);
        if(action.equals("tower-sites")) {
            var provider=Registration.LOSTCITY_FEATURE.get().getDimensionInfo(level);
            List<Map<String,Object>> sites=new ArrayList<>();int checked=0;
            search:for(int radius=0;radius<=20;radius++)for(int z=250-radius;z<=250+radius;z++)for(int x=250-radius;x<=250+radius;x++) {
                if(Math.max(Math.abs(x-250),Math.abs(z-250))!=radius)continue;
                var info=BuildingInfo.getBuildingInfo(new ChunkCoord(level.dimension(),x,z),provider);checked++;
                if(info.hasBuilding&&info.buildingType!=null&&info.buildingType.getName().contains("radio_tower")) {
                    sites.add(Map.of("x",x,"z",z,"building",info.buildingType.getName(),"plannedGround",info.getCityGroundLevel(),"cityStyle",info.getCityStyle().getName()));
                    if(sites.size()>=12)break search;
                }
            }
            return Map.of("sites",sites,"coordinatesChecked",checked,"worldBlocksWritten",false,"scope","Native BuildingInfo selection only; no building or height forced");
        }
        if(action.equals("tower-natural")) {
            int cx=Integer.parseInt(settings.getProperty("x")),cz=Integer.parseInt(settings.getProperty("z"));
            if(Math.abs(cx-250)>20||Math.abs(cz-250)>20)throw new IllegalArgumentException("Bounded surveyed tower site required");
            var provider=Registration.LOSTCITY_FEATURE.get().getDimensionInfo(level);
            var selected=BuildingInfo.getBuildingInfo(new ChunkCoord(level.dimension(),cx,cz),provider);
            if(!selected.hasBuilding||selected.buildingType==null||!selected.buildingType.getName().contains("radio_tower"))throw new IllegalStateException("Native tower selection required");
            var output=Path.of(settings.getProperty("path")).resolveSibling("tower-natural-complete.json");
            if(Files.exists(output))throw new IllegalStateException("Fresh natural generation evidence required");
            List<CompletableFuture<?>> futures=new ArrayList<>();
            for(int z=cz-1;z<=cz+2;z++)for(int x=cx-1;x<=cx+2;x++) {
                var pos=new net.minecraft.world.level.ChunkPos(x,z);
                level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,pos,0,pos);
                futures.add(level.getChunkSource().getChunkFuture(x,z,ChunkStatus.FULL,true));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenCompleteAsync((ignored,failure)->{
                Map<String,Object> result=new LinkedHashMap<>();
                try {
                    if(failure!=null)throw new RuntimeException(failure);
                    for(var future:futures)if(((com.mojang.datafixers.util.Either<?,?>)future.join()).left().isEmpty())throw new AssertionError("Native FULL generation incomplete");
                    List<Map<String,Object>> neighbors=new ArrayList<>();
                    for(int z=cz-1;z<=cz+2;z++)for(int x=cx-1;x<=cx+2;x++) {
                        var info=BuildingInfo.getBuildingInfo(new ChunkCoord(level.dimension(),x,z),provider);
                        neighbors.add(Map.of("x",x,"z",z,"ground",info.getCityGroundLevel(),"building",info.hasBuilding&&info.buildingType!=null?info.buildingType.getName():"street","status",level.getChunk(x,z).getStatus().toString()));
                    }
                    result.put("site",Map.of("x",cx,"z",cz,"building",selected.buildingType.getName(),"ground",selected.getCityGroundLevel()));result.put("neighbors",neighbors);
                    List<Map<String,Object>> columns=new ArrayList<>();
                    int ground=selected.getCityGroundLevel();
                    for(int z=cz*16-4;z<=cz*16+35;z+=3)for(int x=cx*16-4;x<=cx*16+35;x+=3) {
                        int top=ground-25;
                        for(int y=ground+4;y>=ground-24;y--)if(level.getBlockState(new BlockPos(x,y,z)).isSolidRender(level,new BlockPos(x,y,z))){top=y;break;}
                        columns.add(Map.of("x",x,"z",z,"topNearPlannedGround",top,"block",String.valueOf(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(new BlockPos(x,top,z)).getBlock()))));
                    }
                    result.put("groundColumns",columns);result.put("nativeFullChunks",16);result.put("manualClearing",false);result.put("manualTerrainPlacement",false);result.put("manualBuildingPlacement",false);
                    result.put("profile",provider.getProfile().getName());result.put("worldStyle",provider.getProfile().getWorldStyle());result.put("status","PASS");
                    player.getServer().saveEverything(true,true,true);
                }catch(Throwable e){result.put("status","ERROR");result.put("error",e.toString());}
                finally {for(int z=cz-1;z<=cz+2;z++)for(int x=cx-1;x<=cx+2;x++){var pos=new net.minecraft.world.level.ChunkPos(x,z);level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED,pos,0,pos);}}
                try{Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){throw new RuntimeException(e);}
            },player.getServer());
            return Map.of("queued",true,"naturalSelectedBuilding",selected.buildingType.getName(),"completeFile",output.getFileName().toString());
        }
        if(action.equals("tower-view")) {
            int cx=Integer.parseInt(settings.getProperty("x")),cz=Integer.parseInt(settings.getProperty("z"));
            if(Math.abs(cx-250)>20||Math.abs(cz-250)>20)throw new IllegalArgumentException("Bounded surveyed tower site required");
            var provider=Registration.LOSTCITY_FEATURE.get().getDimensionInfo(level);
            var info=BuildingInfo.getBuildingInfo(new ChunkCoord(level.dimension(),cx,cz),provider);
            if(!info.hasBuilding||!info.buildingType.getName().contains("radio_tower"))throw new IllegalStateException("Native tower required");
            boolean close=settings.getProperty("camera","wide").equals("base");
            var client=net.minecraft.client.Minecraft.getInstance();client.execute(()->client.options.renderDistance().set(close?4:6));
            player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();player.setInvulnerable(true);
            player.teleportTo(level,cx*16+8,info.getCityGroundLevel()+(close?5:30),cz*16+(close?43:92),180,close?5:0);
            player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(),"time set 6000");
            player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(),"weather clear");
            return Map.of("nativeBuilding",info.buildingType.getName(),"cityGround",info.getCityGroundLevel(),"camera",player.position().toString(),"manualTerrainPlacement",false);
        }
        if(action.equals("clouds-off")) {
            var client=net.minecraft.client.Minecraft.getInstance();
            client.execute(()->client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF));
            return Map.of("scope","Temporary visual diagnosis in the owned review client; launcher restores options","vanillaCloudsRequested","OFF");
        }
        if(action.equals("reload")) {
            int count=0;
            for(int z=528;z<532;z++)for(int x=528;x<532;x++) {
                var chunk=level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,true);
                if(chunk==null||!chunk.getStatus().isOrAfter(ChunkStatus.FULL))throw new AssertionError("Saved FULL chunk did not reload");
                count++;
            }
            var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(new BlockPos(8450,318,8450));
            if(chest==null)throw new AssertionError("Saved native chest missing");
            var item=chest.getItem(0);
            if(!item.is(net.minecraft.world.item.Items.PAPER)||item.getCount()!=7||!"c2me-region-16-16".equals(item.getOrCreateTag().getString("gn_review_token")))throw new AssertionError("Saved contents changed");
            return Map.of("reloadedFullChunks",count,"savedChestContentsIntact",true,"c2meLoaded",c2me(),"scope","Native chunk loader in a newly started client; saved blocks and items read without replacement");
        }
        if(action.equals("view-batch")) {
            // Limit extra chunks requested only for the visual review, after timing has ended.
            var client=net.minecraft.client.Minecraft.getInstance();
            client.execute(()->client.options.renderDistance().set(6));
            double viewY=Double.parseDouble(settings.getProperty("y","105"));
            if(viewY!=105&&viewY!=150)throw new IllegalArgumentException("Bounded review heights");
            player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();player.setInvulnerable(true);
            player.teleportTo(level,8490,viewY,8500,135,viewY==150?32:22);
            player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(),"time set 6000");
            player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(),"weather clear");
            return Map.of("scope","Actual generated benchmark area; no placed terrain or buildings","position",player.position().toString(),"c2meLoaded",c2me());
        }
        var provider=Registration.LOSTCITY_FEATURE.get().getDimensionInfo(level);
        if(action.equals("chunk")) {
            int x=250,z=250;long start=System.nanoTime();
            var chunk=level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,true);
            if(chunk==null||!chunk.getStatus().isOrAfter(ChunkStatus.FULL))throw new AssertionError("FULL chunk required");
            var info=BuildingInfo.getBuildingInfo(new ChunkCoord(level.dimension(),x,z),provider);
            return Map.of("status",chunk.getStatus().toString(),"chunk",List.of(x,z),"elapsedMs",(System.nanoTime()-start)/1_000_000,"noisiumLoaded",true,"profile",provider.getProfile().getName(),"worldStyle",provider.getProfile().getWorldStyle(),"cityStyle",info.getCityStyle().getName());
        }
        if(!action.equals("tower-before")&&!action.equals("tower-after"))throw new IllegalArgumentException(action);
        // Fixed-height algorithm fixture: its cleared plot is never evidence of natural terrain.
        int cx=1,cz=1,ground=70;
        var coord=new ChunkCoord(level.dimension(),cx,cz);
        var info=copy(BuildingInfo.getBuildingInfo(coord,provider));
        var tower=AssetRegistries.PARTS.getOrThrow(level,"lostcities:radio_tower1_0_0_0");
        var building=AssetRegistries.BUILDINGS.getOrThrow(level,"lostcities:radio_tower1_0_0");
        if(tower.getSliceCount()!=70)throw new AssertionError("Pinned native 70-block tower required");
        set(info,"coord",coord);set(info,"groundLevel",ground);set(info,"cityLevel",0);set(info,"floors",0);set(info,"cellars",0);set(info,"hasBuilding",true);set(info,"ruinHeight",.85f);
        info.buildingType=building;info.floorTypes=new BuildingPart[]{tower};info.floorTypes2=new BuildingPart[]{null};
        var chunk=level.getChunk(cx,cz);var feature=provider.getFeature();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=ground;y<265;y++)level.setBlock(new BlockPos(cx*16+x,y,cz*16+z),Blocks.AIR.defaultBlockState(),2);
        try(var scope=GenerationContext.open(level,level.getSeed(),level.dimension(),cx,cz)) {
            var driver=feature.getDriver();driver.setPrimer(level,chunk);
            feature.generatePart(info,tower,Transform.ROTATE_NONE,0,ground,0,LostCityTerrainFeature.HardAirSetting.AIR);
            driver.actuallyGenerate(chunk);
        }
        try(var scope=GenerationContext.open(level,level.getSeed(),level.dimension(),cx,cz)) {
            var driver=feature.getDriver();driver.setPrimer(level,chunk);
            var ruin=LostCityTerrainFeature.class.getDeclaredMethod("generateRuins",BuildingInfo.class);ruin.setAccessible(true);ruin.invoke(feature,info);
            driver.actuallyGenerate(chunk);
        }
        Set<BlockPos> left=new HashSet<>();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=ground;y<ground+85;y++) {
            var pos=new BlockPos(cx*16+x,y,cz*16+z);var state=level.getBlockState(pos);String id=String.valueOf(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()));
            if(!state.isAir()&&!id.contains("vine")&&!id.contains("leaves")&&!id.contains("ladder"))left.add(pos);
        }
        int floating=0,total=left.size(),maxY=ground;List<Map<String,Object>> components=new ArrayList<>();
        while(!left.isEmpty()) {
            var first=left.iterator().next();left.remove(first);var todo=new ArrayDeque<BlockPos>();todo.add(first);int count=0,minY=999,top=-999;
            while(!todo.isEmpty()) {var pos=todo.remove();count++;minY=Math.min(minY,pos.getY());top=Math.max(top,pos.getY());for(var d:net.minecraft.core.Direction.values()){var n=pos.relative(d);if(left.remove(n))todo.add(n);}}
            maxY=Math.max(maxY,top);if(minY>ground&&count>=20)floating+=count;
            if(count>=20)components.add(Map.of("blocks",count,"minY",minY,"maxY",top));
        }
        if(action.endsWith("before")&&floating<200)throw new AssertionError("Native floating defect was not reproduced: "+components);
        if(action.endsWith("after")&&(floating>=20||maxY<=ground+35))throw new AssertionError("Ruin must remain grounded and tall: "+components);
        player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();player.setInvulnerable(true);
        player.teleportTo(level,24,103,103,180,0);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(chunk,level.getLightEngine(),null,null));
        player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(),"time set 6000");
        player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(),"weather clear");
        return Map.of("fixture",action,"nativePart","lostcities:radio_tower1_0_0_0","partSlices",tower.getSliceCount(),"ruinFraction",.85,"floatingStructuralBlocks",floating,"totalBlocks",total,"maxY",maxY,"components",components,"naturalGenerationEvidence",false,"scope","Native ruin algorithm fixture with manual clearing and placement; not natural terrain evidence");
    }
}
