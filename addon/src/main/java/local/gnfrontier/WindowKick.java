package local.gnfrontier;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/** A request contains no coordinates. Reach, line of sight and protection are server-owned. */
public final class WindowKick {
    public static final int IMPACT_TICK = 3;
    public static final int RECOVERY_TICKS = 11;
    private static final TagKey<Block> WINDOWS=TagKey.create(Registries.BLOCK,new ResourceLocation("gnfrontier:kickable_windows"));
    private static final Map<UUID,Long> last=new HashMap<>();
    private record Impact(ServerLevel level, long at) {}
    private static final Map<UUID, Impact> pending = new HashMap<>();

    public static void kick(ServerPlayer p) {
        if (!canKick(p)) return;
        long now=p.serverLevel().getGameTime(),previous=last.getOrDefault(p.getUUID(),Long.MIN_VALUE/2);
        if(now>=previous && now-previous<14)return;
        last.put(p.getUUID(),now);
        FrontierNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new FrontierNetwork.KickAnimation(p.getId()));
        pending.put(p.getUUID(), new Impact(p.serverLevel(), now + IMPACT_TICK));
    }

    private static boolean canKick(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.isVisuallySwimming()
                && !Hypoxia.down(p) && p.mayBuild() && WorldState.get(p.serverLevel()).enabled;
    }

    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Impact impact = pending.get(player.getUUID());
        if (impact == null) return;
        if (player.serverLevel() != impact.level() || !canKick(player)) {
            pending.remove(player.getUUID());
        } else if (player.serverLevel().getGameTime() >= impact.at()) {
            pending.remove(player.getUUID());
            strike(player);
        }
    }

    private static void strike(ServerPlayer p) {
        Vec3 eye=p.getEyePosition();
        BlockHitResult hit=p.serverLevel().clip(new ClipContext(eye,eye.add(p.getLookAngle().scale(2.0)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
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
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){
        UUID player = e.getEntity().getUUID();
        last.remove(player);
        pending.remove(player);
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent e){last.clear();pending.clear();}
}
