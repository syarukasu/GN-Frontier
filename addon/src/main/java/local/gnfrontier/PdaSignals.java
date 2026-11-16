package local.gnfrontier;

import java.util.*;
import local.gnfrontier.core.Campaign;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** A tiny change-only state update also reaches clients viewing a standalone map. */
@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class PdaSignals {
    private static final Map<UUID,FrontierNetwork.SignalState> sent=new HashMap<>();
    public static void sync(ServerPlayer player){
        var world=WorldState.get(player.serverLevel());
        boolean lost=world.enabled&&Campaign.signalLost(world.campaign(player.getUUID()),world.account(player.getUUID()),player.server.overworld().getGameTime());
        var next=new FrontierNetwork.SignalState(world.enabled,lost);
        if(!next.equals(sent.put(player.getUUID(),next)))FrontierNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),next);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer player){sent.remove(player.getUUID());sync(player);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){sent.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.getServer().getTickCount()%20==0)for(var player:e.getServer().getPlayerList().getPlayers())sync(player);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){sent.clear();}
}
