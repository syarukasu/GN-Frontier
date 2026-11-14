package local.gnfrontier.client;

import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.resources.ResourceLocation;

/** Gun Durability 2.2.2 expects the pre-1.2 TaCZ sound return type. */
public final class GunSoundCompat {
    public static ResourceLocation sound(GunDisplayInstance display, String key) {
        var sound = display.getSounds(key);
        return sound == null ? null : sound.getSound();
    }
    public static ResourceLocation location(Object sound) {
        return sound == null ? null : ((com.tacz.guns.client.resource.pojo.display.SoundData) sound).getSound();
    }
}
