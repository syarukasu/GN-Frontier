package local.gnfrontier;

import local.gnfrontier.core.Economy;
import local.gnfrontier.core.GridTradePlanner;
import local.gnfrontier.core.Campaign;
import local.gnfrontier.core.MerchantQuests;
import com.sighs.petiteinventory.init.Area;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class TradeService {
    public static boolean exists(String id) { return ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id)); }
    public static Item item(String id) {
        if (!exists(id)) throw new IllegalArgumentException("未導入の商品です: " + id);
        return Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));
    }
    public static boolean hasPda(net.minecraft.world.entity.player.Player player) {
        // PDA access is a permanent hotkey feature from the beginning, including after death.
        return player!=null;
    }
    static boolean receivePreserved(ServerPlayer player,ItemStack stack){
        try{mutateInventory(player,stack,0,stack.getCount());return true;}
        catch(IllegalArgumentException full){return false;}
    }
    // Grid placeholders are reserved cells, not empty slots. Armor/offhand/disabled hotbar stay outside trades.
    private static boolean matches(ItemStack stack, ItemStack template) { return ItemStack.isSameItemSameTags(stack,template); }
    private static List<GridTradePlanner.Slot> describe(List<ItemStack> items, ItemStack template) {
        return items.stream().map(stack -> new GridTradePlanner.Slot(stack.getCount(), stack.getMaxStackSize(),
            matches(stack, template), Area.of(stack))).toList();
    }
    public static int count(ServerPlayer player, String id) {
        return exists(id) ? GridTradePlanner.count(describe(player.getInventory().items, new ItemStack(item(id)))) : 0;
    }
    private static int capacity(ServerPlayer player, ItemStack template) {
        return GridTradePlanner.capacity(describe(player.getInventory().items, template), Area.of(template), template.getMaxStackSize());
    }
    private static WorldState active(ServerPlayer player) {
        WorldState data = WorldState.get(player.serverLevel());
        if (!data.enabled) throw new IllegalArgumentException("このワールドではGNルールが無効です");
        if (!player.isAlive() || player.isSpectator() || Hypoxia.down(player)) throw new IllegalArgumentException("PDAを操作できません");
        data.refresh(player.serverLevel());
        if(Campaign.signalLost(data.campaign(player.getUUID()),data.account(player.getUUID()),player.getServer().overworld().getGameTime()))
            throw new IllegalArgumentException("SIGNAL LOST");
        return data;
    }
    public static void trade(ServerPlayer player, String id, Economy.Direction direction, int units) {
        WorldState world = active(player); Economy.Offer offer = Economy.offer(id); ItemStack template=VehicleMarket.stack(id,offer.item());
        if(direction==Economy.Direction.BUY && !IndustrialProgress.canBuy(player,id))
            throw new IllegalArgumentException("STEEL REQUIRED");
        if(Campaign.militaryRank(world.account(player.getUUID()))<VehicleMarket.rank(id))throw new IllegalArgumentException("必要な軍需Rankに達していません");
        Economy.Trade plan = Economy.prepareTrade(world.account(player.getUUID()), offer, direction, units,
                world.stock(offer, direction), GridTradePlanner.count(describe(player.getInventory().items,template)), direction == Economy.Direction.BUY ? capacity(player, template) : 0,
                Campaign.satelliteOnline(world.campaign(player.getUUID()),player.getServer().overworld().getGameTime()));
        mutateInventory(player, template, plan.remove(), plan.add());
        world.players.put(player.getUUID(), plan.after()); world.consumeStock(offer, direction, units); world.setDirty();
        DiplomacyBridge.sync(player, plan.after());
    }
    public static void claim(ServerPlayer player, int slot, String expectedToken) {
        if(!player.getServer().isSameThread())throw new IllegalStateException("Daily claim must run on server thread");
        WorldState world=active(player);Campaign.State campaign=world.refreshCampaign(player.getUUID(),player.serverLevel());
        Campaign.State next=campaign.copy();Campaign.Mission mission=Campaign.mission(next,slot);
        Economy.State beforeAccount=world.account(player.getUUID());
        Economy.State after=Campaign.claim(next,beforeAccount,slot,expectedToken,mission.requirements().stream().map(r->requirementCount(player,r)).toList());
        List<ItemStack> old=player.getInventory().items.stream().map(ItemStack::copy).toList();
        List<ItemStack> planned=new ArrayList<>();old.forEach(s->planned.add(s.copy()));
        for(var required:mission.requirements()){
            int left=required.count();
            for(int i=0;i<36&&left>0;i++)if(GridTradePlanner.usable(i)&&missionMatches(planned.get(i),required)){
                int take=Math.min(left,planned.get(i).getCount());planned.get(i).shrink(take);left-=take;
            }
            if(left!=0)throw new IllegalArgumentException("必要な物資が不足しています");
        }
        try{
            for(int i=0;i<36;i++)if(GridTradePlanner.usable(i))player.getInventory().items.set(i,planned.get(i));
            world.campaigns.put(player.getUUID(),next);world.players.put(player.getUUID(),after);world.setDirty();
        }catch(RuntimeException failure){
            for(int i=0;i<36;i++)if(GridTradePlanner.usable(i))player.getInventory().items.set(i,old.get(i));
            world.campaigns.put(player.getUUID(),campaign);world.players.put(player.getUUID(),beforeAccount);throw failure;
        }
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
        if(player.containerMenu!=player.inventoryMenu)player.containerMenu.broadcastChanges();
        DiplomacyBridge.sync(player,after);
    }
    public static void accept(ServerPlayer player,int slot,String token) {
        WorldState world=active(player);Campaign.State c=world.refreshCampaign(player.getUUID(),player.serverLevel());
        Campaign.accept(c,slot,token);world.setDirty();
    }
    public static void acceptSpecial(ServerPlayer player,String token) {
        if(!player.getServer().isSameThread())throw new IllegalStateException("Quest must run on server thread");
        WorldState world=active(player);var quest=MerchantQuests.resolve(MerchantQuestData.catalog(),token);
        world.campaigns.put(player.getUUID(),MerchantQuests.accept(world.campaign(player.getUUID()),world.account(player.getUUID()),quest));world.setDirty();
    }
    public static void claimSpecial(ServerPlayer player,String token) {
        if(!player.getServer().isSameThread())throw new IllegalStateException("Quest must run on server thread");
        WorldState world=active(player);var quest=MerchantQuests.resolve(MerchantQuestData.catalog(),token);
        Map<String,Integer> available=new HashMap<>();for(var required:quest.items())available.put(required.item(),count(player,required.item()));
        var beforeCampaign=world.campaign(player.getUUID());var beforeAccount=world.account(player.getUUID());
        var claim=MerchantQuests.claim(beforeCampaign,beforeAccount,quest,available);
        List<ItemStack> old=player.getInventory().items.stream().map(ItemStack::copy).toList();
        List<ItemStack> next=new ArrayList<>();old.forEach(s->next.add(s.copy()));
        // Plan all materials before committing any slot, money or completion flag.
        for(var required:quest.items()) {
            ItemStack template=new ItemStack(item(required.item()));
            int[] counts=GridTradePlanner.plan(describe(next,template),Area.of(template),template.getMaxStackSize(),required.count(),0);
            for(int i=0;i<36;i++)if(GridTradePlanner.usable(i))next.get(i).setCount(counts[i]);
        }
        try {
            for(int i=0;i<36;i++)if(GridTradePlanner.usable(i))player.getInventory().items.set(i,next.get(i));
            world.campaigns.put(player.getUUID(),claim.campaign());world.players.put(player.getUUID(),claim.account());world.setDirty();
        } catch(RuntimeException failure) {
            for(int i=0;i<36;i++)if(GridTradePlanner.usable(i))player.getInventory().items.set(i,old.get(i));
            world.campaigns.put(player.getUUID(),beforeCampaign);world.players.put(player.getUUID(),beforeAccount);throw failure;
        }
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
        if(player.containerMenu!=player.inventoryMenu)player.containerMenu.broadcastChanges();
        DiplomacyBridge.sync(player,claim.account());
    }
    private static boolean missionMatches(ItemStack stack,Campaign.DeliveryItem required) {
        if(!stack.is(item(required.item())))return false;
        return required.gunId().isEmpty()?!stack.hasTag():stack.hasTag() && required.gunId().equals(stack.getTag().getString("GunId"));
    }
    public static int requirementCount(ServerPlayer p,Campaign.DeliveryItem required){
        int total=0;for(int i=0;i<36;i++)if(GridTradePlanner.usable(i)&&missionMatches(p.getInventory().items.get(i),required))total+=p.getInventory().items.get(i).getCount();return total;
    }
    public static int missionCount(ServerPlayer p,Campaign.Mission m) {
        if(m.kind()!=Campaign.Kind.DELIVERY)return 0;
        // Keep legacy single-item counts while preventing excess A from satisfying missing B.
        if(m.requirements().size()==1)return requirementCount(p,m.requirements().get(0));
        return m.requirements().stream().mapToInt(r->Math.min(r.count(),requirementCount(p,r))).sum();
    }
    public static void removeMatching(ServerPlayer p,java.util.function.Predicate<ItemStack> predicate,int count) {
        int total=0;for(int i=0;i<36;i++)if(GridTradePlanner.usable(i) && predicate.test(p.getInventory().items.get(i)))total+=p.getInventory().items.get(i).getCount();
        if(count<1 || total<count)throw new IllegalArgumentException("必要な物資が不足しています");
        for(int i=0;i<36 && count>0;i++)if(GridTradePlanner.usable(i)) {
            ItemStack s=p.getInventory().items.get(i);if(!predicate.test(s))continue;
            int take=Math.min(count,s.getCount());s.shrink(take);count-=take;
        }
        p.getInventory().setChanged();p.containerMenu.broadcastChanges();
    }
    private static void mutateInventory(ServerPlayer player, ItemStack template, int remove, int add) {
        if (!player.getServer().isSameThread()) throw new IllegalStateException("Trade must run on server thread");
        List<ItemStack> old = player.getInventory().items.stream().map(ItemStack::copy).toList();
        int[] counts = GridTradePlanner.plan(describe(old, template), Area.of(template), template.getMaxStackSize(), remove, add);
        List<ItemStack> next = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = old.get(i).copy();
            if (stack.isEmpty() && counts[i] > 0) {stack = template.copy();stack.setCount(counts[i]);}
            else stack.setCount(counts[i]);
            next.add(stack);
        }
        try {
            // Apply the already validated whole layout on the server thread. Per-slot hooks would see an intermediate layout.
            for (int i = 0; i < 36; i++) if (GridTradePlanner.usable(i)) player.getInventory().items.set(i, next.get(i));
        } catch (RuntimeException ex) {
            for (int i = 0; i < 36; i++) if (GridTradePlanner.usable(i)) player.getInventory().items.set(i, old.get(i));
            throw ex;
        }
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }
}
