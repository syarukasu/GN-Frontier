package local.gnfrontier;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Worldgen bypasses the native Level.onPlace helper setup. Reuse its original repair hook. */
@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class VehicleCollisionRepair {
    private static final class Work {
        final ServerLevel level;final LevelChunk chunk;int section;
        Work(ServerLevel level,LevelChunk chunk){this.level=level;this.chunk=chunk;}
    }
    private static final ConcurrentLinkedQueue<Work> WORK=new ConcurrentLinkedQueue<>();
    @SubscribeEvent public static void loaded(ChunkEvent.Load event){
        if(event.getLevel() instanceof ServerLevel level&&event.getChunk() instanceof LevelChunk chunk)WORK.add(new Work(level,chunk));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Work work=WORK.peek();if(work==null)return;
        var level=work.level;var chunk=work.chunk;
        if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk){WORK.poll();return;}
        if(!WorldState.get(level).enabled){WORK.poll();return;}
        var sections=chunk.getSections();
        // Palette rejection costs no block scan; at most one relevant 16^3 section per tick.
        while(work.section<sections.length){
            int index=work.section++;var section=sections[index];
            if(section.hasOnlyAir()||!section.maybeHas(state->state.is(Frontier.SALVAGE_VEHICLES)))continue;
            int y=chunk.getSectionYFromSectionIndex(index)<<4;
            var pos=new BlockPos.MutableBlockPos();
            for(int dy=0;dy<16;dy++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                var state=section.getBlockState(x,dy,z);if(!state.is(Frontier.SALVAGE_VEHICLES))continue;
                pos.set(chunk.getPos().getMinBlockX()+x,y+dy,chunk.getPos().getMinBlockZ()+z);
                state.randomTick(level,pos,level.random);
            }
            break;
        }
        if(work.section>=sections.length)WORK.poll();
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){WORK.clear();}
}
