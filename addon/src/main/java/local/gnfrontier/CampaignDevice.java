package local.gnfrontier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class CampaignDevice extends Item {
    private final String purpose;
    public CampaignDevice(String purpose){super(new Item.Properties().stacksTo(1));this.purpose=purpose;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer p && WorldState.get(p.serverLevel()).enabled){
            WorldState w=WorldState.get(p.serverLevel());var c=w.campaign(p.getUUID());
            if(purpose.equals("noah")){p.sendSystemMessage(Component.literal("火星またはGlacioの環境改変器へシードを装着し、電力を供給してください"));return InteractionResultHolder.success(stack);}
            p.sendSystemMessage(Component.literal(purpose.equals("satellite")?"衛星を打上げ設備へ装着し、エンジン・燃料2杯・電力を供給してください。通信には稼働中の地上中継器も必要です":"制御装置を戦略抑止基地へ装着し、電力を供給してください"));
        }return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
