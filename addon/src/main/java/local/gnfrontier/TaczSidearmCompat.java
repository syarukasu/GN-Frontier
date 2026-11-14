package local.gnfrontier;

import com.tacz.guns.api.TimelessAPI;
import net.minecraft.resources.ResourceLocation;

/** Combined Arms 0.1.7 still names the pistol replaced in TaCZ 1.2. */
public final class TaczSidearmCompat {
    private static final ResourceLocation OLD = new ResourceLocation("tacz", "m9a1");
    private static final ResourceLocation CURRENT = new ResourceLocation("tacz", "m9a4");
    private TaczSidearmCompat() {}

    public static ResourceLocation resolve(ResourceLocation id) {
        return OLD.equals(id) && TimelessAPI.getCommonGunIndex(OLD).isEmpty()
            && TimelessAPI.getCommonGunIndex(CURRENT).isPresent() ? CURRENT : id;
    }
}
