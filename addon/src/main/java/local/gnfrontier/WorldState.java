package local.gnfrontier;

import local.gnfrontier.core.Economy;
import local.gnfrontier.core.Campaign;
import local.gnfrontier.core.RespawnPolicy;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** World-owned data: death, respawn and dimension changes do not replace the account. */
public final class WorldState extends SavedData {
    public boolean enabled;
    public final Map<UUID, Economy.State> players = new HashMap<>();
    public final Map<UUID, Campaign.State> campaigns = new HashMap<>();
    public final Map<UUID, RespawnPolicy.State> respawns = new HashMap<>();
    public CompoundTag emission = new CompoundTag();
    public final Set<String> terraformedPlanets=new TreeSet<>();
    public final Set<UUID> starterGiven = new HashSet<>();
    public final Set<String> placedBlocks = new HashSet<>();
    public final Map<String, Integer> usedStock = new HashMap<>();
    public long stockDay = -1;
    public long lastAlertDecay = -1;
    public local.gnfrontier.core.DailyCycle dailyCycle=new local.gnfrontier.core.DailyCycle();
    private long dailyRefreshTick=Long.MIN_VALUE,dailyRefreshSun=Long.MIN_VALUE;
    public static WorldState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(WorldState::load, WorldState::new, "gnfrontier");
    }
    public Economy.State account(UUID id) {
        if (!players.containsKey(id)) { players.put(id, new Economy.State()); setDirty(); }
        return players.get(id);
    }
    public Campaign.State campaign(UUID id) {
        if(!campaigns.containsKey(id)){campaigns.put(id,new Campaign.State());setDirty();}
        return campaigns.get(id);
    }
    public void refresh(long tick) {
        long day = Math.max(0, tick / Economy.DAY_TICKS);
        refreshAt(tick,day);
    }
    public void refresh(ServerLevel level){
        var overworld=level.getServer().overworld();
        if(dailyRefreshTick==overworld.getGameTime()&&dailyRefreshSun==overworld.getDayTime())return;
        dailyRefreshTick=overworld.getGameTime();dailyRefreshSun=overworld.getDayTime();
        long legacyDay=dailyCycle.initialized()?0:Math.max(stockDay,campaigns.values().stream().mapToLong(s->s.day).max().orElse(0));
        dailyCycle.observe(overworld.getGameTime(),overworld.getDayTime(),legacyDay);
        refreshAt(overworld.getGameTime(),dailyCycle.day());setDirty();
    }
    public Campaign.State refreshCampaign(UUID player,ServerLevel level){
        refresh(level);var campaign=campaign(player);Campaign.refreshForDay(campaign,player,dailyCycle.day());return campaign;
    }
    private void refreshAt(long tick,long day){
        if (day > stockDay) { stockDay = day; usedStock.clear(); setDirty(); }
        if (lastAlertDecay < 0) { lastAlertDecay = tick; setDirty(); }
        if (tick >= lastAlertDecay + 1200) {
            for (Economy.State state : players.values()) {
                state.alert[0] = Math.max(0, state.alert[0] - 1);
                state.alert[1] = Math.max(0, state.alert[1] - 1);
            }
            lastAlertDecay = tick; setDirty();
        }
    }
    public int stock(Economy.Offer offer, Economy.Direction direction) {
        return Math.max(0, offer.dailyStock() - usedStock.getOrDefault(offer.id() + ":" + direction, 0));
    }
    public void consumeStock(Economy.Offer offer, Economy.Direction direction, int units) {
        usedStock.merge(offer.id() + ":" + direction, units, Math::addExact); setDirty();
    }
    public static CompoundTag encode(Economy.State s) {
        s.validate();
        CompoundTag n = new CompoundTag();
        n.putLong("balance", s.balance); n.putIntArray("reputation", s.reputation); n.putIntArray("alert", s.alert);
        n.putIntArray("traderReputation", s.traderReputation); n.putLong("taskDay", s.taskDay);
        n.putInt("claimedMask", s.claimedMask); n.putInt("taskRotation", s.taskRotation);
        n.putLong("respawnChangedAt", s.respawnChangedAt); n.putString("spawnDimension", s.spawnDimension);
        n.putLong("spawnPosition", s.spawnPosition);
        ListTag unlocked = new ListTag(); s.unlocked.forEach(v -> unlocked.add(StringTag.valueOf(v)));
        n.put("unlocked", unlocked); return n;
    }
    public static Economy.State decode(CompoundTag n) {
        Economy.State s = new Economy.State();
        s.balance = n.getLong("balance");
        readArray(n, "reputation", s.reputation); readArray(n, "alert", s.alert); readArray(n, "traderReputation", s.traderReputation);
        s.taskDay = n.getLong("taskDay"); s.claimedMask = n.getInt("claimedMask"); s.taskRotation = n.getInt("taskRotation");
        s.respawnChangedAt = n.getLong("respawnChangedAt"); s.spawnDimension = n.getString("spawnDimension");
        s.spawnPosition = n.getLong("spawnPosition");
        for (Tag tag : n.getList("unlocked", Tag.TAG_STRING)) s.unlocked.add(tag.getAsString());
        s.validate(); return s;
    }
    private static void readArray(CompoundTag n, String key, int[] target) {
        int[] values = n.getIntArray(key);
        if (values.length != target.length) throw new IllegalStateException("Invalid GN Frontier saved field: " + key);
        System.arraycopy(values, 0, target, 0, target.length);
    }
    public static WorldState load(CompoundTag n) {
        if (n.getInt("schema") != 1) throw new IllegalStateException("Unsupported GN Frontier save schema; refusing to reset accounts");
        WorldState data = new WorldState(); data.enabled = n.getBoolean("enabled");
        data.dailyCycle=local.gnfrontier.core.DailyCycle.load(n.getCompound("dailyCycle"));
        for (Tag tag : n.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) tag;
            UUID id = row.getUUID("uuid");
            data.players.put(id, decode(row.getCompound("account")));
            if(row.contains("campaign",Tag.TAG_COMPOUND))data.campaigns.put(id,Campaign.State.load(row.getCompound("campaign")));
            if(row.contains("respawn",Tag.TAG_COMPOUND))data.respawns.put(id,RespawnPolicy.State.load(row.getCompound("respawn")));
            if (row.getBoolean("starterGiven")) data.starterGiven.add(id);
        }
        data.stockDay = n.getLong("stockDay"); data.lastAlertDecay = n.getLong("lastAlertDecay");
        data.emission=n.getCompound("emission").copy();
        for(Tag planet:n.getList("terraformedPlanets",Tag.TAG_STRING))data.terraformedPlanets.add(planet.getAsString());
        for (Tag tag : n.getList("placedBlocks", Tag.TAG_STRING)) data.placedBlocks.add(tag.getAsString());
        CompoundTag stock = n.getCompound("stock");
        for (String key : stock.getAllKeys()) {
            int value = stock.getInt(key);
            if (value < 0) throw new IllegalStateException("Negative stored stock consumption");
            data.usedStock.put(key, value);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag n) {
        n.putInt("schema", 1); n.putBoolean("enabled", enabled);
        n.putLong("stockDay", stockDay); n.putLong("lastAlertDecay", lastAlertDecay);
        n.put("dailyCycle",dailyCycle.save());
        emission=EnvironmentEvents.captureEmission(emission);n.put("emission",emission.copy());
        ListTag planets=new ListTag();terraformedPlanets.forEach(p->planets.add(StringTag.valueOf(p)));n.put("terraformedPlanets",planets);
        ListTag list = new ListTag();
        players.forEach((id, state) -> {
            CompoundTag row = new CompoundTag(); row.putUUID("uuid", id); row.put("account", encode(state));
            row.putBoolean("starterGiven", starterGiven.contains(id)); list.add(row);
            if(campaigns.containsKey(id))row.put("campaign",campaigns.get(id).save());
            if(respawns.containsKey(id))row.put("respawn",respawns.get(id).save());
        });
        n.put("players", list);
        ListTag placed = new ListTag(); placedBlocks.forEach(v -> placed.add(StringTag.valueOf(v))); n.put("placedBlocks", placed);
        CompoundTag stock = new CompoundTag(); usedStock.forEach(stock::putInt); n.put("stock", stock);
        return n;
    }
}
