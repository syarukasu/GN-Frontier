package local.gnfrontier.mixin;

import local.gnfrontier.FactionHostility;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla contact damage otherwise only handles players and iron golems. */
@Mixin(value=Slime.class,remap=false)
public abstract class SlimeFactionDamageMixin {
    @Shadow protected abstract boolean m_7483_();
    @Shadow protected abstract void m_33637_(LivingEntity target);
    @Inject(method="m_7334_",at=@At("TAIL"))
    private void gn$factionContact(Entity other,CallbackInfo ci){
        Slime self=(Slime)(Object)this;
        if(!self.level().isClientSide&&other instanceof LivingEntity target&&FactionHostility.enemies(self,target)&&m_7483_())m_33637_(target);
    }
}
