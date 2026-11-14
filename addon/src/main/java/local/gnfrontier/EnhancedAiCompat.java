package local.gnfrontier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraftforge.fml.ModList;

/** Native Enhanced AI owns ordinary tool equipment, mining and fishing when present. */
public final class EnhancedAiCompat {
    public static ItemStack miningTool(Mob mob) {
        if(!RespawnRules.enabled(mob.level()))return mob.getOffhandItem();
        if(mob.getOffhandItem().getItem() instanceof PickaxeItem)return mob.getOffhandItem();
        return mob.getMainHandItem().getItem() instanceof PickaxeItem?mob.getMainHandItem():ItemStack.EMPTY;
    }
    public static boolean miningAllowed(Mob mob) {return !RespawnRules.enabled(mob.level())||!miningTool(mob).isEmpty();}
    private static final boolean PRESENT=ModList.get().isLoaded("enhancedai");
    private EnhancedAiCompat() {}
    public static boolean present(){return PRESENT;}
    public static boolean protectedTarget(Entity target){
        return target instanceof ServerPlayer player && RespawnRules.enabled(player.level())
                && (player.isCreative() || player.isSpectator() || RespawnRules.protectedFromPull(player));
    }
}
