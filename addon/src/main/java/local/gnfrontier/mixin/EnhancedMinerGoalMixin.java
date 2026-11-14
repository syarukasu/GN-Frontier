package local.gnfrontier.mixin;

import local.gnfrontier.EnhancedAiCompat;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="insane96mcp.enhancedai.modules.mobs.miner.MineTowardsTargetGoal",remap=false)
public abstract class EnhancedMinerGoalMixin {
    @Shadow @Final private Mob miner;
    @Inject(method={"m_8036_","m_8045_"},at=@At("HEAD"),cancellable=true)
    private void gn$requiresPickaxe(CallbackInfoReturnable<Boolean> result){if(!EnhancedAiCompat.miningAllowed(miner))result.setReturnValue(false);}
    @Inject(method="m_8037_",at=@At("HEAD"),cancellable=true)
    private void gn$lostPickaxe(CallbackInfo ci){if(!EnhancedAiCompat.miningAllowed(miner))ci.cancel();}
    @Redirect(method={"m_8036_","m_8037_","getDigSpeed","canBreakBlock","canHarvestBlock"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Mob;m_21206_()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack gn$heldPickaxe(Mob mob){return EnhancedAiCompat.miningTool(mob);}
    @Redirect(method="m_8037_",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Mob;m_6844_(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack gn$harvestPickaxe(Mob mob,EquipmentSlot slot){return EnhancedAiCompat.miningTool(mob);}
}
