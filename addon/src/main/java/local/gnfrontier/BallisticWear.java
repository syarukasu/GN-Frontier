package local.gnfrontier;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.LogicalSide;

/** Bypassing vanilla armor must not make ballistic equipment indestructible. Expert plates manage their own wear. */
@Mod.EventBusSubscriber(modid=Frontier.ID)
public final class BallisticWear {
    public static final TagKey<Item> ARMOR=TagKey.create(Registries.ITEM,Frontier.id("ballistic_armor"));
    private record Hit(int entityId,EquipmentSlot slot,int wear){}
    private static final WeakHashMap<Entity,Hit> pending=new WeakHashMap<>();
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hit(EntityHurtByGunEvent.Pre event){
        if(event.getLogicalSide()!=LogicalSide.SERVER||!(event.getHurtEntity() instanceof LivingEntity living)||!RespawnRules.enabled(living.level()))return;
        pending.put(event.getBullet(),new Hit(living.getId(),event.isHeadShot()?EquipmentSlot.HEAD:EquipmentSlot.CHEST,Math.max(1,(int)(event.getBaseAmount()/4f))));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event){
        if(event.getEntity().level().isClientSide||event.getAmount()<=0)return;
        Entity bullet=event.getSource().getDirectEntity();Hit hit=pending.get(bullet);
        if(hit==null||hit.entityId()!=event.getEntity().getId())return;
        // Apply once for the split normal/AP hit, after mitigation but before death inventory capture.
        pending.remove(bullet);var living=event.getEntity();var slot=hit.slot();var armor=living.getItemBySlot(slot);
        if(!armor.is(ARMOR)||!armor.isDamageableItem())return;
        armor.hurtAndBreak(hit.wear(),living,entity->entity.broadcastBreakEvent(slot));
    }
}
