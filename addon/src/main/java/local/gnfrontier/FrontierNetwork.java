package local.gnfrontier;

import local.gnfrontier.core.Economy;
import local.gnfrontier.core.Campaign;
import local.gnfrontier.core.MerchantQuests;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;

public final class FrontierNetwork {
    private static final String VERSION = "8";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Frontier.id("pda"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static final Map<UUID, Long> LAST_REFRESH = new HashMap<>();
    public record Action(String action, String id, int quantity) {
        static void encode(Action m, FriendlyByteBuf b) { b.writeUtf(m.action, 16); b.writeUtf(m.id, 64); b.writeVarInt(m.quantity); }
        static Action decode(FriendlyByteBuf b) { return new Action(b.readUtf(16), b.readUtf(64), b.readVarInt()); }
        static void handle(Action m, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            ServerPlayer player = context.getSender();
            context.enqueueWork(() -> {
                if (player == null || !player.isAlive() || player.isSpectator() || Hypoxia.down(player)) return;
                long now = player.serverLevel().getServer().overworld().getGameTime();
                // The automatic status poll must not consume a user's action allowance.
                Map<UUID,Long> requests=m.action.equals("refresh")?LAST_REFRESH:LAST_REQUEST;
                Long previous = requests.get(player.getUUID());
                if (previous != null && now >= previous && now - previous < 5) return;
                requests.put(player.getUUID(), now);
                String message = "";
                try {
                    if (m.action.equals("buy") || m.action.equals("sell")) TradeService.trade(player, m.id, m.action.equals("buy") ? Economy.Direction.BUY : Economy.Direction.SELL, m.quantity);
                    else if (m.action.equals("claim")) TradeService.claim(player, m.quantity, m.id);
                    else if (m.action.equals("accept")) TradeService.accept(player,m.quantity,m.id);
                    else if(m.action.equals("quest_accept")&&m.quantity==0)TradeService.acceptSpecial(player,m.id);
                    else if(m.action.equals("quest_claim")&&m.quantity==0)TradeService.claimSpecial(player,m.id);
                    else if (!m.action.equals("refresh") && !m.action.equals("open")) return;
                } catch (IllegalArgumentException ex) { message = ex.getMessage(); }
                send(player, message, m.action.equals("open"));
            });
            context.setPacketHandled(true);
        }
    }
    public record Snapshot(CompoundTag data) {
        static void encode(Snapshot m, FriendlyByteBuf b) { b.writeNbt(m.data); }
        static Snapshot decode(FriendlyByteBuf b) { return new Snapshot(Objects.requireNonNull(b.readNbt())); }
        static void handle(Snapshot m, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> local.gnfrontier.client.PdaScreen.accept(m.data)));
            context.setPacketHandled(true);
        }
    }
    public record WorldSnapshot(CompoundTag data) {
        static void encode(WorldSnapshot m,FriendlyByteBuf b){b.writeNbt(m.data);}
        static WorldSnapshot decode(FriendlyByteBuf b){return new WorldSnapshot(Objects.requireNonNull(b.readNbt()));}
        static void handle(WorldSnapshot m,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->AtmosphereOverrides.receive(m.data));ctx.get().setPacketHandled(true);}
    }
    public record KickRequest() {
        static void encode(KickRequest m,FriendlyByteBuf b){}
        static KickRequest decode(FriendlyByteBuf b){return new KickRequest();}
        static void handle(KickRequest m,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)WindowKick.kick(c.getSender());});c.setPacketHandled(true);}
    }
    public record KickAnimation(int entityId) {
        static void encode(KickAnimation m,FriendlyByteBuf b){b.writeVarInt(m.entityId);}
        static KickAnimation decode(FriendlyByteBuf b){return new KickAnimation(b.readVarInt());}
        static void handle(KickAnimation m,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.gnfrontier.client.KickClient.animate(m.entityId)));c.setPacketHandled(true);}
    }
    public record Condition(int entityId,boolean collapsed,int debt,int recovery) {
        static void encode(Condition m,FriendlyByteBuf b){b.writeVarInt(m.entityId);b.writeBoolean(m.collapsed);b.writeVarInt(m.debt);b.writeVarInt(m.recovery);}
        static Condition decode(FriendlyByteBuf b){return new Condition(b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarInt());}
        static void handle(Condition m,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->local.gnfrontier.client.SurvivalVisuals.receive(m)));c.setPacketHandled(true);}
    }
    public static void register() {
        CHANNEL.registerMessage(0, Action.class, Action::encode, Action::decode, Action::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2,WorldSnapshot.class,WorldSnapshot::encode,WorldSnapshot::decode,WorldSnapshot::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3,KickRequest.class,KickRequest::encode,KickRequest::decode,KickRequest::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4,KickAnimation.class,KickAnimation::encode,KickAnimation::decode,KickAnimation::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5,Condition.class,Condition::encode,Condition::decode,Condition::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void forget(UUID player) { LAST_REQUEST.remove(player); LAST_REFRESH.remove(player); }
    public static void send(ServerPlayer player, String message) {
        send(player, message, true);
    }
    private static void send(ServerPlayer player, String message, boolean open) {
        WorldState world = WorldState.get(player.serverLevel());
        long tick = player.serverLevel().getServer().overworld().getGameTime();
        world.refresh(tick);
        Economy.State account = world.account(player.getUUID());
        Economy.refreshTasks(account, player.getUUID(), tick); world.setDirty();
        Campaign.State campaign=world.campaign(player.getUUID());Campaign.refresh(campaign,player.getUUID(),tick);
        CompoundTag n = WorldState.encode(account);
        n.putBoolean("open", open);
        n.putBoolean("enabled", world.enabled); n.putBoolean("signalLost", Campaign.signalLost(campaign,account,tick)); n.putString("message", message);
        n.put("campaign",campaign.clientSnapshot());n.putLong("taskTicksRemaining",Economy.DAY_TICKS-tick%Economy.DAY_TICKS);
        n.putInt("militaryRank",Campaign.militaryRank(account));n.putBoolean("satelliteOnline",Campaign.satelliteOnline(campaign,tick));n.putBoolean("deterrenceOnline",Campaign.deterrenceOnline(campaign,tick));
        ListTag offers = new ListTag();
        for (Economy.Offer offer : Economy.OFFERS) {
            CompoundTag row = new CompoundTag(); row.putString("id", offer.id()); row.putString("item", offer.item());
            row.putString("merchant", offer.merchant().name()); row.putInt("count", offer.count());
            row.putLong("buy", offer.buy()); row.putLong("sell", offer.sell()); row.putInt("required", offer.requiredReputation());
            row.putInt("buyStock", world.stock(offer, Economy.Direction.BUY)); row.putInt("sellStock", world.stock(offer, Economy.Direction.SELL));
            row.putBoolean("unlocked", Economy.standing(account, offer.merchant()) >= offer.requiredReputation() && Campaign.militaryRank(account)>=VehicleMarket.rank(offer.id()));
            row.putBoolean("progressUnlocked", IndustrialProgress.canBuy(player,offer.id()));
            row.putInt("rank",VehicleMarket.rank(offer.id()));row.put("stack",VehicleMarket.stack(offer.id(),offer.item()).save(new CompoundTag()));
            if(VehicleMarket.CATALOG.containsKey(offer.id()))row.putString("displayName",VehicleMarket.CATALOG.get(offer.id()).title());
            row.putBoolean("available", TradeService.exists(offer.item())); offers.add(row);
        }
        n.put("offers", offers);
        ListTag tasks = new ListTag();
        for (int i = 0; i < Campaign.missionCount(campaign); i++) {
            Campaign.Mission task = Campaign.mission(campaign, i); CompoundTag row = new CompoundTag();
            row.putString("id", task.id()); row.putString("item", task.item()); row.putString("merchant", task.merchant().name());
            row.putString("title",task.title());row.putString("description",task.description());row.putString("kind",task.kind().name());row.putString("claimToken", Campaign.token(campaign, i));
            row.putInt("count", task.count()); row.putLong("reward", task.reward()); row.putInt("have", task.kind()==Campaign.Kind.DELIVERY?TradeService.missionCount(player,task):campaign.progress[i]);
            ListTag required=new ListTag();boolean ready=true;
            for(var ingredient:task.requirements()){
                CompoundTag entry=ingredient.save();int have=TradeService.requirementCount(player,ingredient);entry.putInt("have",have);required.add(entry);ready&=have>=ingredient.count();
            }
            row.put("items",required);row.putBoolean("ready",task.kind()==Campaign.Kind.DELIVERY?ready:campaign.progress[i]>=task.count());
            row.putBoolean("accepted",(campaign.accepted&(1<<i))!=0);row.putBoolean("claimed", (campaign.claimed & (1 << i)) != 0); tasks.add(row);
        }
        n.put("tasks", tasks);
        ListTag quests=new ListTag();
        for(var quest:MerchantQuestData.catalog()) {
            CompoundTag row=new CompoundTag();row.putString("id",quest.id());row.putString("token",quest.token());
            row.putString("title",quest.title());row.putString("description",quest.description());row.putInt("reputation",quest.reputation());
            row.putInt("standing",Economy.standing(account,Economy.Merchant.BLACK));row.putLong("reward",quest.reward());row.putInt("reputationReward",quest.reputationReward());
            row.putBoolean("unlocked",MerchantQuests.unlocked(campaign,account,quest));row.putBoolean("accepted",campaign.merchantAccepted.contains(quest.id()));row.putBoolean("completed",campaign.merchantCompleted.contains(quest.id()));
            ListTag required=new ListTag();boolean ready=true;
            for(var item:quest.items()) {CompoundTag ingredient=new CompoundTag();int have=TradeService.count(player,item.item());
                ingredient.putString("item",item.item());ingredient.putInt("count",item.count());ingredient.putInt("have",have);required.add(ingredient);ready&=have>=item.count();}
            row.put("items",required);row.putBoolean("ready",ready);ListTag prerequisites=new ListTag();
            for(String id:quest.requires())if(!campaign.merchantCompleted.contains(id)) {
                var previous=MerchantQuestData.catalog().stream().filter(q->q.id().equals(id)).findFirst().orElseThrow();prerequisites.add(StringTag.valueOf(previous.title()));
            }
            row.put("prerequisites",prerequisites);quests.add(row);
        }
        n.put("specialQuests",quests);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(n));
    }
}
