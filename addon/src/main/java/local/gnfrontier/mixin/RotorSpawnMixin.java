package local.gnfrontier.mixin;

import java.util.UUID;
import local.gnfrontier.MilitarySites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restricts native NPC rotor encounters, while leaving player purchases/placement untouched. */
@Mixin(targets="com.neoalive.tacz_sewv.spawn.TankSpawner",remap=false)
public abstract class RotorSpawnMixin {
    @Inject(method="spawnRotorWithCrew(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lcom/neoalive/tacz_sewv/spawn/TankSpawner$TankFaction;Ljava/util/UUID;)Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;",at=@At("HEAD"),cancellable=true)
    private static void gn$nearBase(ServerLevel level,BlockPos position,@Coerce Object faction,UUID target,CallbackInfoReturnable<Object> cir) {
        if(!MilitarySites.allowRotor(level,position))cir.setReturnValue(null);
    }
    @Inject(method="spawnRotorWithCrew(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lcom/neoalive/tacz_sewv/spawn/TankSpawner$TankFaction;Ljava/util/UUID;Ljava/lang/String;)Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;",at=@At("HEAD"),cancellable=true)
    private static void gn$nearBaseNamed(ServerLevel level,BlockPos position,@Coerce Object faction,UUID target,String name,CallbackInfoReturnable<Object> cir) {
        if(!MilitarySites.allowRotor(level,position))cir.setReturnValue(null);
    }
}
