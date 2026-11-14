package local.gnfrontier.core;

import local.gnfrontier.WorldState;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
import static local.gnfrontier.core.Economy.*;

/** Offline transaction and actual NBT format checks; no Minecraft server/world is started. */
public final class CoreVerification {
    private static int checks;
    private static void check(boolean value, String description) {
        checks++; if (!value) throw new AssertionError(description);
    }
    private static void rejected(Runnable action, String description) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Accepted invalid operation: " + description);
    }
    public static void main(String[] args) {
        State s = new State(); s.balance = 1000;
        Offer scrap = offer("scrap");
        Trade buy = prepareTrade(s, scrap, Direction.BUY, 2, 96, 0, 8);
        check(buy.after().balance == 840 && buy.add() == 8 && buy.remove() == 0, "Buy quantity and exact cost");
        check(s.balance == 1000 && s.unlocked.isEmpty(), "Planning does not mutate input");
        Trade sell = prepareTrade(buy.after(), scrap, Direction.SELL, 2, 96, 8, 0);
        check(sell.after().balance == 880 && sell.remove() == 8, "Sell payout and spread");
        check(sell.after().traderReputation[2] == 2 && sell.after().reputation[0] == 0 && sell.after().alert[0] == 0, "Independent merchant/faction/alert values");
        rejected(() -> prepareTrade(s, scrap, Direction.BUY, 1, 0, 0, 64), "Out of stock");
        rejected(() -> prepareTrade(s, scrap, Direction.BUY, 0, 96, 0, 64), "Zero amount");
        rejected(() -> prepareTrade(s, scrap, Direction.BUY, 17, 96, 0, 64), "Oversized request");
        rejected(() -> prepareTrade(s, scrap, Direction.BUY, 1, 96, 0, 3), "Full inventory");
        rejected(() -> prepareTrade(s, scrap, Direction.SELL, 1, 96, 3, 64), "Insufficient items");
        rejected(() -> prepareTrade(s, offer("steel_east"), Direction.SELL, 1, 64, 4, 0), "Reputation locked");
        rejected(() -> prepareTrade(s, offer("ration"), Direction.SELL, 1, 24, 1, 0), "Unsupported direction");
        State poor = new State();
        rejected(() -> prepareTrade(poor, scrap, Direction.BUY, 1, 96, 0, 64), "Insufficient money");
        State full = new State(); full.balance = MAX_MONEY;
        rejected(() -> prepareTrade(full, scrap, Direction.SELL, 1, 96, 4, 0), "Money overflow");
        State jam = s.copy(); jam.alert[1] = JAM_ALERT;
        rejected(() -> prepareTrade(jam, scrap, Direction.BUY, 1, 96, 0, 64), "Communication jam");
        check(jam.balance == 1000, "Rejected trade preserved state");
        State deposited = depositNotes(s, 4);
        check(deposited.balance == 1040 && s.balance == 1000 && deposited.reputation[0] == 0 && deposited.alert[1] == 0, "Loot cash deposit preserves independent state");
        rejected(() -> depositNotes(full, 1), "Cash deposit overflow");
        rejected(() -> depositNotes(s, 0), "Empty cash deposit");
        State east = prepareTrade(s, offer("wire"), Direction.SELL, 4, 96, 16, 0).after();
        check(east.reputation[0] == 4 && east.reputation[1] == 0 && east.traderReputation[0] == 4, "East sales do not implicitly punish West");
        aggression(east, Faction.WEST);
        check(east.reputation[0] == 4 && east.reputation[1] == -5 && east.alert[1] == 10 && east.balance == 1180, "Aggression independent from balance");

        UUID id = UUID.fromString("cae7dc99-0efd-45f3-a551-47871d075c1c");
        State task = new State(); refreshTasks(task, id, 0);
        check(task.taskDay == 0 && new HashSet<>(List.of(task(task, 0).id(), task(task, 1).id(), task(task, 2).id())).size() == 3, "Three distinct daily tasks");
        Task first = task(task, 0);
        String originalToken = claimToken(task, 0);
        rejected(() -> prepareClaim(task, 0, "forged-token", 1000), "Wrong task identity");
        rejected(() -> prepareClaim(task, 0, first.count() - 1), "Incomplete delivery");
        State claimed = prepareClaim(task, 0, first.count());
        check(claimed.balance == first.reward() && claimed.claimedMask == 1 && task.claimedMask == 0, "Single reward at commit");
        rejected(() -> prepareClaim(claimed, 0, first.count()), "Duplicate reward");
        refreshTasks(claimed, id, DAY_TICKS - 1);
        check(claimed.claimedMask == 1, "No reset before 24000 elapsed ticks");
        refreshTasks(claimed, id, DAY_TICKS);
        check(claimed.claimedMask == 0 && claimed.taskDay == 1, "Daily reset");
        rejected(() -> prepareClaim(claimed, 0, originalToken, 1000), "Stale UI at day boundary must not consume a different task's items");
        claimed.claimedMask = 1; refreshTasks(claimed, id, 0);
        check(claimed.claimedMask == 1 && claimed.taskDay == 1, "Clock rewind cannot reclaim");
        rejected(() -> prepareClaim(claimed, 3, 1000), "Fourth task request");

        State home = new State();
        check(canMoveSpawn(home, 0, "minecraft:overworld", 10), "First bed registration");
        home.respawnChangedAt = 100; home.spawnDimension = "minecraft:overworld"; home.spawnPosition = 10;
        check(canMoveSpawn(home, 101, "minecraft:overworld", 10), "Reusing own bed does not reset cooldown");
        check(!canMoveSpawn(home, 72099, "minecraft:overworld", 11), "Three day cooldown lower boundary");
        check(canMoveSpawn(home, 72100, "minecraft:overworld", 11), "Three day cooldown boundary");
        check(!canMoveSpawn(home, 99, "minecraft:overworld", 11), "Clock rewind cannot move home");

        Random random = new Random(47120420L);
        for (int i = 0; i < 3000; i++) {
            int units = random.nextInt(16) + 1;
            State before = new State(); before.balance = 100000;
            for (int f = 0; f < 2; f++) before.reputation[f] = 100;
            Arrays.fill(before.traderReputation, 100);
            Offer offer = OFFERS.get(random.nextInt(OFFERS.size()));
            before.balance=Math.max(before.balance,offer.buy()*units+1);
            if (offer.buy() > 0) {
                Trade purchase = prepareTrade(before, offer, Direction.BUY, units, units, 0, 1024);
                check(purchase.add() == units * offer.count() && purchase.after().balance + units * offer.buy() == before.balance, "Random purchase conservation");
                if (offer.sell() > 0) {
                    Trade sale = prepareTrade(purchase.after(), offer, Direction.SELL, units, units, purchase.add(), 0);
                    check(sale.remove() == purchase.add() && sale.after().balance < before.balance, "No buy/sell arbitrage");
                }
            } else {
                Trade sale = prepareTrade(before, offer, Direction.SELL, units, units, 1024, 0);
                check(sale.remove() == units * offer.count() && sale.after().balance - units * offer.sell() == before.balance, "Random sale conservation");
            }
        }

        // Real Minecraft NBT classes, but no server, registry bootstrap or world creation.
        WorldState world = new WorldState(); world.enabled = true; world.players.put(id, east);
        world.starterGiven.add(id); world.stockDay = 8; world.lastAlertDecay = 2000;
        world.placedBlocks.add("minecraft:overworld:12345"); world.consumeStock(scrap, Direction.SELL, 3);
        east.unlocked.add("steel_east"); east.spawnDimension = home.spawnDimension; east.spawnPosition = home.spawnPosition;
        east.respawnChangedAt = home.respawnChangedAt; east.taskDay = 9; east.claimedMask = 5;
        CompoundTag encoded = world.save(new CompoundTag()); WorldState reloaded = WorldState.load(encoded.copy());
        check(reloaded.save(new CompoundTag()).equals(encoded), "All state survives NBT roundtrip including UUID, money, rep, alert, stock, bed, claim bits and starter");
        reloaded.players.get(id).alert[1] = 99;
        check(world.players.get(id).alert[1] == 10, "Reloaded arrays are independent");
        check(reloaded.stock(scrap, Direction.SELL) == 93 && reloaded.stock(scrap, Direction.BUY) == 96, "Stock directions persisted independently");
        reloaded.refresh(8 * DAY_TICKS); check(reloaded.stock(scrap, Direction.SELL) == 93, "No reset on same day");
        reloaded.refresh(9 * DAY_TICKS); check(reloaded.stock(scrap, Direction.SELL) == 96, "Daily global stock refresh");
        CompoundTag future = encoded.copy(); future.putInt("schema", 2);
        rejected(() -> WorldState.load(future), "Future save schema refuses silent reset");
        CompoundTag bad = WorldState.encode(east); bad.putIntArray("alert", new int[]{1});
        rejected(() -> WorldState.decode(bad), "Corrupt saved array length");
        CompoundTag negative = WorldState.encode(east); negative.putLong("balance", -1);
        rejected(() -> WorldState.decode(negative), "Negative saved money");
        System.out.println("GN_CORE_VERIFICATION_PASS checks=" + checks + " randomSeed=47120420 nbtRoundTrip=true worldStarted=false");
    }
}
