package local.gnfrontier;

import java.util.*;
import mcjty.lostcities.LostCities;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Adds real Doomsday vehicle models to new Lost Cities streets, never to an existing save chunk. */
public final class VehicleStreets {
    private static final net.minecraft.tags.TagKey<Block> ROAD=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,Frontier.id("vehicle_road_surfaces"));
    @SubscribeEvent public void generated(ChunkEvent.Load event) {
        if(!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel l) || l.dimension()!=Level.OVERWORLD
            || !(event.getChunk() instanceof LevelChunk chunk))return;
        if(l.getServer().overworld()!=null && !WorldState.get(l).enabled && l.getGameTime()!=0)return;
        var info=LostCities.lostCitiesImp.getLostInfo(l);if(info==null)return;
        var c=info.getChunkInfo(chunk.getPos().x,chunk.getPos().z);
        if(!c.isCity() || c.getBuildingId()!=null || c.getMaxHighwayLevel()>=0)return;
        Random rng=new Random(l.getSeed() ^ chunk.getPos().toLong() * 0x9E3779B97F4A7C15L);
        if(rng.nextInt(100)>=PrototypeConfig.STREET_VEHICLE_PERCENT.get())return;
        int y=info.getRealHeight(c.getCityLevel());
        for(int attempt=0;attempt<6;attempt++) {
            int x=chunk.getPos().getMinBlockX()+5+rng.nextInt(6),z=chunk.getPos().getMinBlockZ()+5+rng.nextInt(6);
            for(int dy=-2;dy<=3;dy++) {
                BlockPos p=new BlockPos(x,y+dy,z);
                if(!chunk.getBlockState(p.below()).is(ROAD))continue;
                int ns=roadScore(chunk,p,Direction.NORTH)+roadScore(chunk,p,Direction.SOUTH);
                int ew=roadScore(chunk,p,Direction.EAST)+roadScore(chunk,p,Direction.WEST);
                boolean eastWest=ew>ns || (ew==ns&&rng.nextBoolean());
                Direction facing=eastWest?(rng.nextBoolean()?Direction.EAST:Direction.WEST):(rng.nextBoolean()?Direction.NORTH:Direction.SOUTH);
                var model=VehicleAssemblies.MODELS.get(rng.nextInt(VehicleAssemblies.MODELS.size()));
                if(!VehicleAssemblies.place(l,p,facing,model))continue;
                chunk.setUnsaved(true);return;
            }
        }
    }
    private static int roadScore(LevelChunk chunk,BlockPos p,Direction d){int score=0;for(int i=1;i<=4;i++)if(chunk.getBlockState(p.below().relative(d,i)).is(ROAD))score++;return score;}
}
