package local.gnreview;

import java.lang.reflect.*;
import java.util.*;
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

/** Native prefab and FULL chunk checks in the sole registered review world. */
public final class WorldgenChecks {
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
        var provider=Registration.LOSTCITY_FEATURE.get().getDimensionInfo(level);
        if(action.equals("chunk")) {
            int x=250,z=250;long start=System.nanoTime();
            var chunk=level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,true);
            if(chunk==null||!chunk.getStatus().isOrAfter(ChunkStatus.FULL))throw new AssertionError("FULL chunk required");
            var info=BuildingInfo.getBuildingInfo(new ChunkCoord(level.dimension(),x,z),provider);
            return Map.of("status",chunk.getStatus().toString(),"chunk",List.of(x,z),"elapsedMs",(System.nanoTime()-start)/1_000_000,"noisiumLoaded",true,"profile",provider.getProfile().getName(),"worldStyle",provider.getProfile().getWorldStyle(),"cityStyle",info.getCityStyle().getName());
        }
        if(!action.equals("tower-before")&&!action.equals("tower-after"))throw new IllegalArgumentException(action);
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
        return Map.of("fixture",action,"nativePart","lostcities:radio_tower1_0_0_0","partSlices",tower.getSliceCount(),"ruinFraction",.85,"floatingStructuralBlocks",floating,"totalBlocks",total,"maxY",maxY,"components",components,"scope","Native generatePart and transformed generateRuins; existing owned review world only");
    }
}
