package local.gnfrontier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Checks loaded structure starts only; encounters never generate terrain to find a base. */
public final class MilitarySites {
    private static final TagKey<Structure> BASES=TagKey.create(Registries.STRUCTURE,Frontier.id("military_sites"));
    public static boolean near(ServerLevel level,BlockPos position) {
        int radius=PrototypeConfig.ROTOR_BASE_RADIUS.get();
        int chunks=(radius+128+15)/16;
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for(int x=(position.getX()>>4)-chunks;x<=(position.getX()>>4)+chunks;x++)
            for(int z=(position.getZ()>>4)-chunks;z<=(position.getZ()>>4)+chunks;z++) {
                LevelChunk chunk=level.getChunkSource().getChunkNow(x,z);
                if(chunk==null)continue;
                for(var entry:chunk.getAllStarts().entrySet()) {
                    if(!entry.getValue().isValid() || !registry.wrapAsHolder(entry.getKey()).is(BASES))continue;
                    var box=entry.getValue().getBoundingBox();
                    int dx=Math.max(Math.max(box.minX()-position.getX(),position.getX()-box.maxX()),0);
                    int dz=Math.max(Math.max(box.minZ()-position.getZ(),position.getZ()-box.maxZ()),0);
                    if((long)dx*dx+(long)dz*dz<=(long)radius*radius)return true;
                }
            }
        return false;
    }
    public static boolean allowRotor(ServerLevel level,BlockPos position) {
        return !WorldState.get(level).enabled || near(level,position);
    }
}
