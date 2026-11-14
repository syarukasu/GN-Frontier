package local.gnfrontier;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.registries.ForgeRegistries;
public final class EnemyDrops {
    @SubscribeEvent(priority=EventPriority.LOWEST) public void drops(LivingDropsEvent e) {
        if(e.isCanceled() || !(e.getEntity().level() instanceof ServerLevel l) || !WorldState.get(l).enabled)return;
        var id=ForgeRegistries.ENTITY_TYPES.getKey(e.getEntity().getType());if(id==null)return;
        // ChaosZ Bandits spawns vanilla Pillagers; it registers no custom entity namespace.
        boolean soldier=id.getNamespace().equals("simpleenemymod") || e.getEntity() instanceof Pillager;
        if(!soldier && !(e.getEntity() instanceof Zombie))return;
        var params=new LootParams.Builder(l).withParameter(LootContextParams.THIS_ENTITY,e.getEntity())
            .withParameter(LootContextParams.ORIGIN,e.getEntity().position()).withParameter(LootContextParams.DAMAGE_SOURCE,e.getSource())
            .withOptionalParameter(LootContextParams.KILLER_ENTITY,e.getSource().getEntity())
            .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY,e.getSource().getDirectEntity()).create(LootContextParamSets.ENTITY);
        var table=l.getServer().getLootData().getLootTable(Frontier.id("entities/"+(soldier?"soldier":"zombie")));
        for(var stack:table.getRandomItems(params))e.getDrops().add(new ItemEntity(l,e.getEntity().getX(),e.getEntity().getY(),e.getEntity().getZ(),stack));
    }
}
