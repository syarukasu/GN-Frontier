package local.gnfrontier;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.registries.ForgeRegistries;

/** Resumable local biosphere, four columns/tick; never load a chunk or replace a building. */
public final class NoahLandscape {
    public static int column(ServerLevel l,BlockPos anchor,UUID owner,int cursor) {
        int dx=cursor%129-64,dz=cursor/129-64;if(dx*dx+dz*dz>4096)return 0;
        int x=anchor.getX()+dx,z=anchor.getZ()+dz;if(!l.hasChunk(x>>4,z>>4))return -1;
        BlockPos p=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(x,0,z)).below();
        var current=l.getBlockState(p);var id=ForgeRegistries.BLOCKS.getKey(current.getBlock());
        boolean nativeGround=id!=null&&id.getNamespace().equals("ad_astra")&&(id.getPath().endsWith("stone")||id.getPath().endsWith("sand")||id.getPath().equals("permafrost"));
        boolean glacioSnow=l.dimension().location().toString().equals("ad_astra:glacio")&&(current.is(Blocks.SNOW_BLOCK)||current.is(Blocks.POWDER_SNOW));
        if(!nativeGround&&!glacioSnow)return 0;
        if(l.getBlockEntity(p)!=null || Gameplay.protectedAt(l,p)||WorldState.get(l).placedBlocks.contains(Gameplay.key(l,p)))return 0;
        var actor=FakePlayerFactory.get(l,new GameProfile(owner,"GN_Noah"));
        if(ForgeEventFactory.onBlockPlace(actor,BlockSnapshot.create(l.dimension(),l,p),Direction.UP))return 0;
        l.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),3);
        // Plants only replace empty air. No copied building or biome NBT.
        BlockPos above=p.above();if(l.isEmptyBlock(above)){
            int choice=Math.floorMod(x*734287+z*912931,100);
            var plant=choice<2?Blocks.OAK_SAPLING:choice<7?Blocks.DANDELION:choice<12?Blocks.POPPY:choice<35?Blocks.GRASS:null;
            if(plant!=null && plant.defaultBlockState().canSurvive(l,above)&&!ForgeEventFactory.onBlockPlace(actor,BlockSnapshot.create(l.dimension(),l,above),Direction.UP))l.setBlock(above,plant.defaultBlockState(),3);
        }return 1;
    }
}
