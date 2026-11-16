package local.gnfrontier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Same first matching registered type as Lost Cities, including cached absence. */
@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class BlockEntityTypeLookup {
    private static final Map<Block,Optional<BlockEntityType<?>>> TYPES=new ConcurrentHashMap<>();
    private static final Set<String> WARNED=ConcurrentHashMap.newKeySet();
    public static BlockEntityType<?> resolve(BlockState state){
        return TYPES.computeIfAbsent(state.getBlock(),block->Optional.ofNullable(scan(state))).orElse(null);
    }
    public static BlockEntityType<?> scan(BlockState state){
        for(var type:ForgeRegistries.BLOCK_ENTITY_TYPES.getValues())if(type.isValid(state))return type;
        return null;
    }
    public static boolean firstWarning(String message){return WARNED.add(message);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){TYPES.clear();WARNED.clear();}
}
