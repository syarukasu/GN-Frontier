package local.gnfrontier;

import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Source-video goods retain their native gun/melee identifiers throughout a transaction. */
public final class MarketGoods {
    private MarketGoods(){}
    public static ItemStack stack(String offer,String item){
        if(offer.equals("market_milk")){
            var stack=new ItemStack(TradeService.item(item));stack.getOrCreateTag().putString("ConsumableId","lrtactical:condensed_milk");return stack;
        }
        if(offer.equals("market_makarov"))return GunItemBuilder.create().setId(new ResourceLocation("hamster:makarov"))
                .setCount(1).setAmmoCount(0).setFireMode(FireMode.SEMI).build();
        if(offer.equals("market_dagger")||offer.equals("market_french_nail")){
            var stack=new ItemStack(TradeService.item(item));
            stack.getOrCreateTag().putString("MeleeWeaponId",offer.equals("market_dagger")?"lrtactical:dagger":"gwg:french_nail");
            return stack;
        }
        return VehicleMarket.stack(offer,item);
    }
}
