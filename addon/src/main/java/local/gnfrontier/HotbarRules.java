package local.gnfrontier;

import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Five usable quick slots. Existing items are retained while migrating out of the other four. */
@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class HotbarRules {
    private static final String PENDING="gnfrontier_hotbar_return";
    private static final java.util.Set<java.util.UUID> MOVING=new java.util.HashSet<>();
    public static boolean blocked(int slot){return slot>=5&&slot<9;}
    public static void evacuate(Player player){
        if(!(player instanceof ServerPlayer server))return;
        if(!MOVING.add(player.getUUID()))return;
        try{move(server);}finally{MOVING.remove(player.getUUID());}
    }
    private static void move(ServerPlayer server){
        Player player=server;
        var data=player.getPersistentData();ListTag pending=data.getList(PENDING,Tag.TAG_COMPOUND);
        for(int slot=5;slot<9;slot++){
            var item=player.getInventory().getItem(slot);if(item.isEmpty())continue;
            var id=ForgeRegistries.ITEMS.getKey(item.getItem());
            if(id!=null&&id.getNamespace().equals("tinyinv"))continue;
            pending.add(NpcEquipment.saveStack(item));player.getInventory().items.set(slot,ItemStack.EMPTY);
        }
        if(pending.isEmpty())return;
        // Save each pending stack before any insertion; no ground drops and no item reroll.
        data.put(PENDING,pending);
        while(!pending.isEmpty()){
            var item=NpcEquipment.loadStack(pending.getCompound(0));
            if(!TradeService.receivePreserved(server,item))break;
            pending.remove(0);
        }
        if(pending.isEmpty()){data.remove(PENDING);data.remove(PENDING+"_notice");}
        else if(!data.getBoolean(PENDING+"_notice")){
            data.putBoolean(PENDING+"_notice",true);
            player.sendSystemMessage(Component.literal("ホットバーを5枠に整理しました。収納に入りきらない品は、空きを作ると自動で返却されます。"));
        }
        player.getInventory().setChanged();
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer&&event.player.tickCount%20==0)evacuate(event.player);
    }
}
