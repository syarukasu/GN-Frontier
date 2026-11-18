package local.gnfrontier;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.registries.ForgeRegistries;

/** A Doomsday vehicle has two model anchors, not one block with a whole-car mesh. */
public final class VehicleAssemblies {
    public record Model(String template,String front,String rear,boolean large) {}
    public static final List<Model> MODELS=load();
    private static final float DISMANTLE_TICKS = 56;
    private static List<Model> load() {
        try(InputStream in=Objects.requireNonNull(VehicleAssemblies.class.getResourceAsStream("/data/gnfrontier/vehicle_assemblies.json"));
            Reader reader=new InputStreamReader(in,StandardCharsets.UTF_8)) {
            return List.of(new Gson().fromJson(reader,Model[].class));
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
    public static BlockPos rearPosition(BlockPos front,Direction facing){return front.relative(facing.getOpposite(),3);}
    public static BlockState state(String id,Direction facing) {
        Block block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("doomsday_decoration",id));
        if(block==null)throw new IllegalStateException("Missing Doomsday vehicle part: "+id);
        BlockState state=block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,facing);
        Property<?> parts=state.getBlock().getStateDefinition().getProperty("parts");
        if(parts instanceof IntegerProperty p)state=state.setValue(p,0);
        if(state.hasProperty(BlockStateProperties.WATERLOGGED))state=state.setValue(BlockStateProperties.WATERLOGGED,false);
        return state;
    }
    public static List<BlockPos> anchors(BlockPos front,Direction facing){return List.of(front,rearPosition(front,facing));}
    public static boolean clearAndSupported(ServerLevel level,BlockPos front,Direction facing) {
        for(BlockPos anchor:anchors(front,facing)) {
            for(BlockPos p:BlockPos.betweenClosed(anchor.offset(-1,0,-1),anchor.offset(1,2,1))) {
                if(!level.hasChunkAt(p)||!level.getBlockState(p).canBeReplaced()||!level.getFluidState(p).isEmpty()||level.getBlockEntity(p)!=null)return false;
            }
            for(BlockPos p:BlockPos.betweenClosed(anchor.offset(-1,-1,-1),anchor.offset(1,-1,1)))
                if(!level.hasChunkAt(p)||!level.getBlockState(p).isFaceSturdy(level,p,Direction.UP))return false;
        }
        return true;
    }
    public static boolean place(ServerLevel level,BlockPos front,Direction facing,Model model) {
        if(!clearAndSupported(level,front,facing))return false;
        BlockPos rear=rearPosition(front,facing);
        BlockState a=state(model.front(),facing),b=state(model.rear(),facing.getOpposite());
        BlockState oldA=level.getBlockState(front),oldB=level.getBlockState(rear);
        if(level.setBlock(front,a,3)&&level.setBlock(rear,b,3)&&level.getBlockState(front).equals(a)&&level.getBlockState(rear).equals(b))return true;
        // Do not leave an orphan anchor if either write failed.
        if(level.getBlockState(front).is(a.getBlock()))level.setBlock(front,oldA,3);
        if(level.getBlockState(rear).is(b.getBlock()))level.setBlock(rear,oldB,3);
        return false;
    }
    public record Mate(BlockPos pos,BlockState state) {}
    public static Mate mate(ServerLevel level,BlockPos pos,BlockState state) {
        if(!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))return null;
        ResourceLocation id=ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if(id==null||!id.getNamespace().equals("doomsday_decoration"))return null;
        Direction facing=state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos other=rearPosition(pos,facing);
        if(!level.hasChunkAt(other))return null;
        BlockState actual=level.getBlockState(other);
        for(Model model:MODELS) {
            String expected=id.getPath().equals(model.front())?model.rear():id.getPath().equals(model.rear())?model.front():null;
            if(expected==null)continue;
            ResourceLocation otherId=ForgeRegistries.BLOCKS.getKey(actual.getBlock());
            if(new ResourceLocation("doomsday_decoration",expected).equals(otherId)
                &&actual.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                &&actual.getValue(BlockStateProperties.HORIZONTAL_FACING)==facing.getOpposite())return new Mate(other.immutable(),actual);
        }
        return null;
    }

    private record Pending(ServerLevel level,UUID player,BlockPos broken,BlockState original,Mate mate) {}
    private final List<Pending> pending=new ArrayList<>();
    private boolean checkingMate;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void breakSpeed(PlayerEvent.BreakSpeed event) {
        var player = event.getEntity();
        var state = event.getState();
        if (player.isCreative() || !state.is(Frontier.SALVAGE_VEHICLES)
                || player.level() instanceof ServerLevel level && !WorldState.get(level).enabled) return;

        ItemStack tool = player.getMainHandItem();
        if (!SalvageLoot.canDismantle(tool)) {
            event.setNewSpeed(0);
            return;
        }
        float hardness = state.getDestroySpeed(player.level(), event.getPosition().orElse(BlockPos.ZERO));
        if (hardness < 0) return;
        float divisor = ForgeHooks.isCorrectToolForDrops(state, player) ? 30 : 100;
        // Measured from multiple uninterrupted wrecks in Part 2. A better tool cannot turn it into an instant break.
        float originalSpeed = Math.max(1, tool.getDestroySpeed(state));
        float conditions = Math.max(0, Math.min(1, event.getNewSpeed() / originalSpeed));
        event.setNewSpeed(hardness * divisor * conditions / DISMANTLE_TICKS);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public void breaking(BlockEvent.BreakEvent event) {
        if(checkingMate||event.isCanceled()||!(event.getLevel() instanceof ServerLevel level)
            ||!(event.getPlayer() instanceof ServerPlayer player)||!WorldState.get(level).enabled)return;
        if (event.getState().is(Frontier.SALVAGE_VEHICLES) && !player.isCreative()
                && !SalvageLoot.canDismantle(player.getMainHandItem())) {
            event.setCanceled(true);
            return;
        }
        Mate mate=mate(level,event.getPos(),event.getState());if(mate==null)return;
        boolean permitted=level.mayInteract(player,mate.pos())&&player.mayBuild()&&level.getBlockEntity(mate.pos())==null;
        if(permitted)try {
            checkingMate=true;
            permitted=ForgeHooks.onBlockBreakEvent(level,player.gameMode.getGameModeForPlayer(),player,mate.pos())!=-1;
        } finally {checkingMate=false;}
        if(!permitted){event.setCanceled(true);return;}
        pending.add(new Pending(level,player.getUUID(),event.getPos().immutable(),event.getState(),mate));
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||pending.isEmpty())return;
        List<Pending> work=new ArrayList<>(pending);pending.clear();
        for(Pending p:work) {
            if(!p.level().hasChunkAt(p.broken())||!p.level().hasChunkAt(p.mate().pos()))continue;
            // A cancelled/failed original break must leave both halves intact.
            if(p.level().getBlockState(p.broken()).is(p.original().getBlock())
                ||!p.level().getBlockState(p.mate().pos()).equals(p.mate().state()))continue;
            ServerPlayer player=p.level().getServer().getPlayerList().getPlayer(p.player());
            if(player==null||player.serverLevel()!=p.level()||!p.level().mayInteract(player,p.mate().pos()))continue;
            // The original mined anchor supplies loot and tool wear once for the vehicle.
            p.level().destroyBlock(p.mate().pos(),false,player);
        }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event){pending.clear();}
}
