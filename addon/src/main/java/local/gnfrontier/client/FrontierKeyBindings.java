package local.gnfrontier.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import local.gnfrontier.Frontier;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

/** Migrate only untouched V assignments, once per installation. Custom assignments stay intact. */
@Mod.EventBusSubscriber(modid=Frontier.ID,value=Dist.CLIENT)
public final class FrontierKeyBindings {
    private record Binding(int code,KeyModifier modifier){}
    private static final Map<String,Binding> REPLACEMENTS=Map.of(
            "key.tacz.melee.desc",new Binding(GLFW.GLFW_KEY_V,KeyModifier.CONTROL),
            "key.tacz.zoom.desc",new Binding(GLFW.GLFW_KEY_V,KeyModifier.ALT),
            "key.superbwarfare.melee",new Binding(GLFW.GLFW_KEY_V,KeyModifier.CONTROL),
            "tfmg.keyinfo.transmission_shift_up",new Binding(GLFW.GLFW_KEY_F10,KeyModifier.NONE),
            "key.ad_astra.toggle_suit_flight",new Binding(GLFW.GLFW_KEY_F10,KeyModifier.CONTROL),
            "key.hbm_m.power_armor_dash",new Binding(GLFW.GLFW_KEY_F10,KeyModifier.ALT),
            "key.anomalyzone.toggle_detector_mode",new Binding(GLFW.GLFW_KEY_F10,KeyModifier.SHIFT));
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e){Minecraft.getInstance().execute(FrontierKeyBindings::migrate);}
    private static void migrate(){
        bindPdaToMapKey();
        Path marker=FMLPaths.CONFIGDIR.get().resolve("gnfrontier-keybinds-v1.json");if(Files.exists(marker))return;
        var mc=Minecraft.getInstance();var changes=new JsonObject();
        for(KeyMapping key:mc.options.keyMappings){
            Binding replacement=REPLACEMENTS.get(key.getName());
            if(replacement==null||key.getKey().getValue()!=GLFW.GLFW_KEY_V||key.getKeyModifier()!=KeyModifier.NONE)continue;
            key.setKeyModifierAndCode(replacement.modifier,InputConstants.Type.KEYSYM.getOrCreate(replacement.code));
            changes.addProperty(key.getName(),key.saveString());
        }
        KeyMapping.resetMapping();if(changes.size()>0)mc.options.save();
        try{Files.writeString(marker,new GsonBuilder().setPrettyPrinting().create().toJson(changes)+"\n");}
        catch(java.io.IOException error){LogUtils.getLogger().warn("GN Frontier: could not record key migration",error);}
    }

    private static void bindPdaToMapKey() {
        var options = Minecraft.getInstance().options;
        KeyMapping map = null, pda = null;
        for (KeyMapping mapping : options.keyMappings) {
            if (mapping.getName().equals("gui.xaero_open_map")) map = mapping;
            if (mapping.getName().equals("key.campchat.open_chat")) pda = mapping;
        }
        if (pda == null) return;
        boolean oldPdaDefault = pda.getKey().getValue() == GLFW.GLFW_KEY_INSERT && pda.getKeyModifier() == KeyModifier.NONE;
        boolean mapAssigned = map != null && !map.isUnbound();
        if (!oldPdaDefault && !mapAssigned) return;
        if (oldPdaDefault || mapAssigned && pda.isDefault()) {
            var key = mapAssigned ? map.getKey() : InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_M);
            pda.setKeyModifierAndCode(mapAssigned ? map.getKeyModifier() : KeyModifier.NONE, key);
        }
        if (mapAssigned) map.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        options.save();
    }
}
