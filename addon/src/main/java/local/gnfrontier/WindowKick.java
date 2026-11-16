package local.gnfrontier;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/** A request contains no coordinates. Reach, line of sight and protection are server-owned. */
public final class WindowKick {
    private static final TagKey<Block> WINDOWS=TagKey.create(Registries.BLOCK,new ResourceLocation("gnfrontier:kickable_windows"));
    private static final Map<UUID,Long> last=new HashMap<>();
    public static void kick(ServerPlayer p) {
        if(!p.isAlive() || p.isSpectator() || Hypoxia.down(p) || !p.mayBuild() || !WorldState.get(p.serverLevel()).enabled)return;
        long now=p.serverLevel().getGameTime(),previous=last.getOrDefault(p.getUUID(),Long.MIN_VALUE/2);
        if(now>=previous && now-previous<14)return;
        last.put(p.getUUID(),now);
        Vec3 eye=p.getEyePosition();
        BlockHitResult hit=p.serverLevel().clip(new ClipContext(eye,eye.add(p.getLookAngle().scale(2.0)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
        FrontierNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new FrontierNetwork.KickAnimation(p.getId()));
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.6f,.8f);
        if(!p.isCreative())p.causeFoodExhaustion(.3f);
        if(hit.getType()!=HitResult.Type.BLOCK)return;
        if(!p.serverLevel().getBlockState(hit.getBlockPos()).is(WINDOWS))return;
        // Clear the two-block-high passage in this same window plane, never a block behind it.
        BlockPos base=new BlockPos(hit.getBlockPos().getX(),p.getBlockY(),hit.getBlockPos().getZ());
        boolean opened=false;
        for(BlockPos pos:List.of(base,base.above())){
            if(!p.serverLevel().hasChunkAt(pos)||!p.serverLevel().getBlockState(pos).is(WINDOWS)
                ||p.serverLevel().getBlockEntity(pos)!=null||!p.serverLevel().mayInteract(p,pos)
                ||Gameplay.protectedAt(p.serverLevel(),pos))continue;
            if(ForgeHooks.onBlockBreakEvent(p.serverLevel(),p.gameMode.getGameModeForPlayer(),p,pos)==-1)continue;
            opened|=p.serverLevel().destroyBlock(pos,false,p);
        }
        if(opened){
            Vec3 forward=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z).normalize().scale(.2);
            p.push(forward.x,0,forward.z);
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){last.remove(e.getEntity().getUUID());}
    @SubscribeEvent public void stopped(ServerStoppedEvent e){last.clear();}
}
