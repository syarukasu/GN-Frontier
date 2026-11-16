package local.gnfrontier.core;

import java.util.*;

/** No Minecraft dependency: all prices, authority and bounds live on the server. */
public final class Economy {
    public static final long MAX_MONEY = 1_000_000_000_000L;
    public static final long DAY_TICKS = 24_000L;
    public static final int JAM_ALERT = 60;
    public enum Faction { EAST, WEST }
    public enum Merchant { EAST, WEST, NEUTRAL, BLACK }
    public enum Direction { BUY, SELL }
    public record Offer(String id, Merchant merchant, String item, int count, long buy, long sell,
                        int requiredReputation, int dailyStock) {
        public Offer {
            Objects.requireNonNull(id); Objects.requireNonNull(merchant); Objects.requireNonNull(item);
            if (count < 1 || count > 64 || buy < 0 || sell < 0 || buy > MAX_MONEY || sell > MAX_MONEY
                || (buy > 0 && sell >= buy) || requiredReputation < 0 || dailyStock < 1)
                throw new IllegalArgumentException("Invalid offer: " + id);
        }
    }
    public record Task(String id, String item, int count, long reward, Merchant merchant) {}
    public record Trade(State after, String item, int remove, int add, long delta) {}

    public static final List<Offer> OFFERS = List.of(
        new Offer("market_meat",Merchant.BLACK,"survival_instinct:meat_can",1,6,0,0,24),
        new Offer("market_fruit",Merchant.BLACK,"survival_instinct:fruit_can",5,26,0,0,24),
        new Offer("market_milk",Merchant.BLACK,"lrtactical:consumable",1,20,0,0,24),
        new Offer("market_energy",Merchant.BLACK,"survival_instinct:energy_can",1,10,0,0,24),
        new Offer("market_wine",Merchant.BLACK,"survival_instinct:wine",1,15,0,0,24),
        new Offer("market_dagger",Merchant.BLACK,"lrtactical:melee",1,50,0,0,8),
        new Offer("market_french_nail",Merchant.BLACK,"lrtactical:melee",1,50,0,0,8),
        new Offer("market_makarov",Merchant.BLACK,"tacz:modern_kinetic_gun",1,100,0,0,8),
        new Offer("market_repair",Merchant.BLACK,"hbm_m:gun_kit_2",1,300,0,0,8),
        new Offer("ration", Merchant.NEUTRAL, "survival_instinct:mre", 1, 100, 0, 0, 24),
        new Offer("bandage", Merchant.NEUTRAL, "survival_instinct:bandage", 1, 60, 0, 0, 24),
        new Offer("antibiotics", Merchant.NEUTRAL, "survival_instinct:antibiotics", 1, 180, 0, 10, 8),
        new Offer("mask", Merchant.NEUTRAL, "survival_instinct:gas_mask_helmet", 1, 800, 0, 20, 4),
        new Offer("scrap", Merchant.NEUTRAL, "gnfrontier:metal_scrap", 4, 80, 20, 0, 96),
        new Offer("wire", Merchant.EAST, "tfmg:copper_wire", 4, 160, 45, 0, 96),
        new Offer("steel_east", Merchant.EAST, "tfmg:steel_ingot", 4, 0, 220, 10, 64),
        new Offer("steel_west", Merchant.WEST, "tfmg:steel_ingot", 4, 0, 220, 10, 64),
        new Offer("battery", Merchant.WEST, "survival_instinct:batteries", 2, 180, 60, 0, 48),
        new Offer("medkit", Merchant.WEST, "survival_instinct:survival_medkit", 1, 280, 0, 20, 8),
        new Offer("electronics", Merchant.BLACK, "survival_instinct:electronic_scrap", 4, 300, 100, 0, 48),
        new Offer("diesel", Merchant.BLACK, "tfmg:diesel_bucket", 1, 0, 160, 20, 32),
        new Offer("filter",Merchant.NEUTRAL,"gnfrontier:gas_filter",1,120,0,0,24),
        new Offer("pentium",Merchant.BLACK,"gnfrontier:cpu_pentium_g4560",1,200,50,0,4),
        new Offer("i3",Merchant.BLACK,"gnfrontier:cpu_i3_6100",1,150,37,10,4),
        new Offer("i5",Merchant.BLACK,"gnfrontier:cpu_i5_2500k",1,250,62,20,4),
        new Offer("gpu8800",Merchant.BLACK,"gnfrontier:gpu_8800_gtx",1,200,50,10,3),
        new Offer("gpu480",Merchant.BLACK,"gnfrontier:gpu_gtx_480",1,300,75,25,3),
        new Offer("mining_rig",Merchant.BLACK,"gnfrontier:mining_rig",1,200,0,30,2),
        new Offer("military_chip",Merchant.EAST,"superbwarfare:intelligent_chip",1,0,180,20,48),
        new Offer("steel_plate",Merchant.WEST,"tfmg:steel_ingot",16,0,900,30,16),
        new Offer("advanced_circuit",Merchant.BLACK,"hbm_m:advanced_circuit",1,0,220,25,32),
        new Offer("rocket_engine",Merchant.WEST,"ad_astra:steel_engine",1,0,1200,40,8),
        new Offer("swarm_drone",Merchant.EAST,"superbwarfare:swarm_drone",4,0,800,40,12),
        new Offer("food_supply",Merchant.NEUTRAL,"survival_instinct:mre",8,0,160,10,24),
        new Offer("chromium",Merchant.BLACK,"gnfrontier:chromium_concentrate",4,480,60,15,20),
        new Offer("permit_i",Merchant.NEUTRAL,"gnfrontier:military_permit_i",1,80,0,0,16),
        new Offer("permit_ii",Merchant.NEUTRAL,"gnfrontier:military_permit_ii",1,300,0,0,8),
        new Offer("permit_iii",Merchant.NEUTRAL,"gnfrontier:military_permit_iii",1,900,0,0,4),
        new Offer("vehicle_lav150",Merchant.WEST,"superbwarfare:container",1,12000,3000,0,2),
        new Offer("vehicle_bmp",Merchant.EAST,"superbwarfare:container",1,28000,8000,25,2),
        new Offer("vehicle_m1a2",Merchant.WEST,"superbwarfare:container",1,65000,18000,60,1),
        new Offer("vehicle_ah6",Merchant.WEST,"superbwarfare:container",1,36000,10000,25,2)
    );
    public static final List<Task> TASKS = List.of(
        new Task("scrap_supply", "gnfrontier:metal_scrap", 16, 180, Merchant.NEUTRAL),
        new Task("wire_supply", "tfmg:copper_wire", 12, 240, Merchant.EAST),
        new Task("battery_supply", "survival_instinct:batteries", 4, 240, Merchant.WEST),
        new Task("cloth_supply", "survival_instinct:cloth", 12, 180, Merchant.NEUTRAL),
        new Task("electronics_supply", "survival_instinct:electronic_scrap", 8, 300, Merchant.BLACK),
        new Task("steel_supply", "tfmg:steel_ingot", 8, 650, Merchant.EAST)
    );

    public static final class State {
        public long balance;
        public long taskDay = -1;
        public int claimedMask;
        public int taskRotation;
        public final int[] reputation = new int[2];
        public final int[] alert = new int[2];
        public final int[] traderReputation = new int[4];
        public final Set<String> unlocked = new TreeSet<>();
        public long respawnChangedAt = -1;
        public String spawnDimension = "";
        public long spawnPosition;
        public State copy() {
            State s = new State();
            s.balance = balance; s.taskDay = taskDay; s.claimedMask = claimedMask; s.taskRotation = taskRotation;
            System.arraycopy(reputation, 0, s.reputation, 0, 2);
            System.arraycopy(alert, 0, s.alert, 0, 2);
            System.arraycopy(traderReputation, 0, s.traderReputation, 0, 4);
            s.unlocked.addAll(unlocked); s.respawnChangedAt = respawnChangedAt;
            s.spawnDimension = spawnDimension; s.spawnPosition = spawnPosition;
            return s;
        }
        public boolean jammed() { return alert[0] >= JAM_ALERT || alert[1] >= JAM_ALERT; }
        public void validate() {
            if (balance < 0 || balance > MAX_MONEY || taskDay < -1 || claimedMask < 0 || claimedMask > 7
                || taskRotation < 0 || taskRotation >= TASKS.size() || respawnChangedAt < -1)
                throw new IllegalStateException("Invalid saved player state");
            for (int v : reputation) if (v < -100 || v > 100) throw new IllegalStateException("Invalid reputation");
            for (int v : alert) if (v < 0 || v > 100) throw new IllegalStateException("Invalid alert");
            for (int v : traderReputation) if (v < 0 || v > 100) throw new IllegalStateException("Invalid trader reputation");
        }
    }

    public static Offer offer(String id) {
        return OFFERS.stream().filter(o -> o.id.equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown offer"));
    }
    public static int standing(State s, Merchant merchant) {
        return merchant == Merchant.EAST ? s.reputation[0] : merchant == Merchant.WEST ? s.reputation[1] : s.traderReputation[merchant.ordinal()];
    }
    public static void rewardStanding(State s, Merchant merchant, int delta) {
        int m = merchant.ordinal();
        s.traderReputation[m] = clamp(s.traderReputation[m] + delta, 0, 100);
        if (m < 2) s.reputation[m] = clamp(s.reputation[m] + delta, -100, 100);
        for (Offer o : OFFERS) if (standing(s, o.merchant) >= o.requiredReputation) s.unlocked.add(o.id);
    }
    public static Trade prepareTrade(State before, Offer offer, Direction direction, int units, int stock, int available, int capacity) {
        return prepareTrade(before,offer,direction,units,stock,available,capacity,false);
    }
    public static Trade prepareTrade(State before, Offer offer, Direction direction, int units, int stock, int available, int capacity, boolean satellite) {
        before.validate();
        if (before.jammed() && !satellite) throw new IllegalArgumentException("SIGNAL LOST");
        if (units < 1 || units > 16) throw new IllegalArgumentException("取引数量が範囲外です");
        if (standing(before, offer.merchant) < offer.requiredReputation) throw new IllegalArgumentException("評判が不足しています");
        if (stock < units) throw new IllegalArgumentException("本日の取引枠がありません");
        int count = Math.multiplyExact(offer.count, units);
        long unitPrice = direction == Direction.BUY ? offer.buy : offer.sell;
        if (unitPrice == 0) throw new IllegalArgumentException("この取引方向には対応していません");
        long price = Math.multiplyExact(unitPrice, units);
        State after = before.copy();
        if (direction == Direction.BUY) {
            if (before.balance < price) throw new IllegalArgumentException("残高が不足しています");
            if (capacity < count) throw new IllegalArgumentException("インベントリに空きがありません");
            after.balance -= price;
        } else {
            if (available < count) throw new IllegalArgumentException("納品素材が不足しています");
            if (before.balance > MAX_MONEY - price) throw new IllegalArgumentException("残高上限です");
            after.balance += price;
            rewardStanding(after, offer.merchant, units);
        }
        return new Trade(after, offer.item, direction == Direction.SELL ? count : 0,
                direction == Direction.BUY ? count : 0, direction == Direction.BUY ? -price : price);
    }
    public static void refreshTasks(State s, UUID player, long elapsedTicks) {
        long day = Math.max(0, elapsedTicks / DAY_TICKS);
        if (day > s.taskDay) {
            s.taskDay = day; s.claimedMask = 0;
            s.taskRotation = Math.floorMod(player.hashCode() + Long.hashCode(day), TASKS.size());
        }
    }
    public static Task task(State s, int slot) {
        if (slot < 0 || slot >= 3 || s.taskDay < 0) throw new IllegalArgumentException("Unknown task slot");
        return TASKS.get((s.taskRotation + slot) % TASKS.size());
    }
    public static State prepareClaim(State before, int slot, int available) {
        return prepareClaim(before, slot, claimToken(before, slot), available);
    }
    public static String claimToken(State state, int slot) { return task(state, slot).id + "@" + state.taskDay; }
    public static State prepareClaim(State before, int slot, String expectedToken, int available) {
        before.validate();
        if (before.jammed()) throw new IllegalArgumentException("SIGNAL LOST");
        Task task = task(before, slot);
        if (!claimToken(before, slot).equals(expectedToken)) throw new IllegalArgumentException("依頼が更新されています。PDAを更新してください");
        if ((before.claimedMask & (1 << slot)) != 0) throw new IllegalArgumentException("この依頼は受領済みです");
        if (available < task.count) throw new IllegalArgumentException("依頼素材が不足しています");
        if (before.balance > MAX_MONEY - task.reward) throw new IllegalArgumentException("残高上限です");
        State after = before.copy(); after.balance += task.reward; after.claimedMask |= 1 << slot;
        rewardStanding(after, task.merchant, 5);
        return after;
    }
    public static boolean canMoveSpawn(State s, long tick, String dimension, long pos) {
        if (s.respawnChangedAt < 0) return true;
        if (s.spawnDimension.equals(dimension) && s.spawnPosition == pos) return true;
        return tick >= s.respawnChangedAt && tick - s.respawnChangedAt >= DAY_TICKS * 3;
    }
    public static void aggression(State s, Faction faction) {
        int f = faction.ordinal();
        s.reputation[f] = clamp(s.reputation[f] - 5, -100, 100);
        s.alert[f] = clamp(s.alert[f] + 10, 0, 100);
    }
    public static int clamp(int value, int low, int high) { return Math.max(low, Math.min(high, value)); }
    public static State depositNotes(State before, int count) {
        before.validate();
        if (count < 1 || count > 64 || before.balance > MAX_MONEY - count * 10L) throw new IllegalArgumentException("入金数量または残高上限です");
        State after = before.copy(); after.balance += count * 10L; return after;
    }
}
