package local.gnfrontier.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = {"com.anomalyzone.quest.OpenQuestDialogPacket",
        "com.anomalyzone.quest.OpenContractDialogPacket"}, remap = false)
public abstract class AnomalyDialogServerMixin {}
