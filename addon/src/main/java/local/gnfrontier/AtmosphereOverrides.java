package local.gnfrontier;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/** A completed Noah project changes Ad Astra's planet-wide oxygen/temperature API, with client sync. */
public final class AtmosphereOverrides {
    public static void resetClient(){client=Set.of();}
    private static volatile Set<ResourceLocation> client=Set.of();
    private static volatile Set<ResourceLocation> serverPlanets=Set.of();
    public static boolean complete(ResourceKey<Level> key){
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();
        return (server!=null && server.isSameThread()?serverPlanets:client).contains(key.location());
    }
    public static void refresh(MinecraftServer server){
        WorldState w=WorldState.get(server.overworld());Set<ResourceLocation> set=new HashSet<>();
        if(w.enabled)for(String planet:w.terraformedPlanets)set.add(new ResourceLocation(planet));
        serverPlanets=Set.copyOf(set);for(ServerPlayer p:server.getPlayerList().getPlayers())send(p);
    }
    public static void send(ServerPlayer p){
        CompoundTag n=new CompoundTag();ListTag planets=new ListTag();serverPlanets.stream().map(ResourceLocation::toString).sorted().forEach(x->planets.add(StringTag.valueOf(x)));
        n.put("atmosphere",planets);FrontierNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new FrontierNetwork.WorldSnapshot(n));
    }
    public static void receive(CompoundTag n){Set<ResourceLocation> s=new HashSet<>();for(Tag t:n.getList("atmosphere",Tag.TAG_STRING))s.add(new ResourceLocation(t.getAsString()));client=Set.copyOf(s);}
    public static void reset(){serverPlanets=Set.of();client=Set.of();}
}
