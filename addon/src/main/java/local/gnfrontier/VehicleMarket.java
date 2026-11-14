package local.gnfrontier;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Use the manufacturer's real factory containers, with exact NBT matching for sales. */
public final class VehicleMarket {
    public record Vehicle(String entity,String title,int rank){}
    public static final Map<String,Vehicle> CATALOG=Map.of(
        "vehicle_lav150",new Vehicle("superbwarfare:lav_150","LAV-150 装輪装甲車",1),
        "vehicle_bmp",new Vehicle("superbwarfare:bmp_2","BMP-2 歩兵戦闘車",2),
        "vehicle_m1a2",new Vehicle("superbwarfare:m_1a_2","M1A2 戦車",3),
        "vehicle_ah6",new Vehicle("superbwarfare:ah_6","AH-6 ヘリコプター",2));
    public static ItemStack stack(String offer,String item) {
        Vehicle v=CATALOG.get(offer);if(v==null)return new ItemStack(TradeService.item(item));
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(v.entity()));
        if(type==null)throw new IllegalArgumentException("車両が未登録です: "+v.entity());
        try{return ((ItemStack)Class.forName("com.atsuishio.superbwarfare.item.container.ContainerBlockItem").getMethod("createInstance",EntityType.class).invoke(null,type)).copy();}
        catch(ReflectiveOperationException error){throw new IllegalStateException("SBW factory-container API changed",error);}
    }
    public static int rank(String id){if(id.equals("permit_ii"))return 2;if(id.equals("permit_iii"))return 3;Vehicle v=CATALOG.get(id);return v==null?1:v.rank();}
}
