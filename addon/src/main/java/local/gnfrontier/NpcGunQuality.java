package local.gnfrontier;

import com.tacz.guns.api.item.IGun;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;

/** Resolve native gun quality while carried, so first pickup cannot reroll a soldier's weapon. */
public final class NpcGunQuality {
    public static final String INITIALIZED="gnfrontier_gun_quality_v1";
    private static final String ROOT="com.github.leopoko.tacz_attributes_addon.";
    private static final class Api {
        static final Api INSTANCE=new Api();
        final ForgeConfigSpec.BooleanValue enabled,sockets;
        final Method hasData,hasPreset,targetRarity,minRarity,removePreset,generate,constrained,seal,initialSockets,missingSockets;
        Api(){
            try{
                Class<?> config=Class.forName(ROOT+"config.CommonConfig"),data=Class.forName(ROOT+"data.GunAttributeData"),generator=Class.forName(ROOT+"random.RarityConstraintHelper"),compat=Class.forName(ROOT+"compat.apotheosis.ApotheosisCompat");
                enabled=(ForgeConfigSpec.BooleanValue)config.getField("ENABLE_RANDOM_ON_OBTAIN").get(null);
                sockets=(ForgeConfigSpec.BooleanValue)config.getField("ENABLE_APOTHEOSIS").get(null);
                hasData=data.getMethod("hasAddonData",ItemStack.class);hasPreset=data.getMethod("hasPreset",ItemStack.class);
                targetRarity=data.getMethod("getPresetTargetRarity",ItemStack.class);minRarity=data.getMethod("getPresetMinRarity",ItemStack.class);removePreset=data.getMethod("removePreset",ItemStack.class);
                generate=generator.getMethod("generateStandard",ItemStack.class,RandomSource.class);
                constrained=generator.getMethod("generateWithConstraint",ItemStack.class,RandomSource.class,int.class,int.class,int.class);
                seal=data.getMethod("setSealed",ItemStack.class,boolean.class);
                initialSockets=compat.getMethod("applyInitialSockets",ItemStack.class);missingSockets=compat.getMethod("applyInitialSocketsIfMissing",ItemStack.class);
            }catch(ReflectiveOperationException error){throw new IllegalStateException("Unsupported TaCZ Attributes Addon API",error);}
        }
        void initialize(ItemStack stack,RandomSource random)throws ReflectiveOperationException{
            if(IGun.getIGunOrNull(stack)==null)return;
            if((Boolean)hasData.invoke(null,stack)){
                if(sockets.get())missingSockets.invoke(null,stack);
                return;
            }
            if((Boolean)hasPreset.invoke(null,stack)){
                int target=(Integer)targetRarity.invoke(null,stack),minimum=(Integer)minRarity.invoke(null,stack);
                removePreset.invoke(null,stack);constrained.invoke(null,stack,random,target,minimum,-1);
            }else generate.invoke(null,stack,random);
            if(sockets.get())initialSockets.invoke(null,stack);
            seal.invoke(null,stack,true);
        }
    }
    public static void prepare(Mob mob){
        if(mob.getPersistentData().getBoolean(INITIALIZED))return;
        if(ModList.get().isLoaded("tacz_attributes_addon")){
            var api=Api.INSTANCE;
            if(api.enabled.get())try{
                var inventory=NpcEquipment.inventory(mob);
                for(int slot=0;slot<inventory.getSlots();slot++)api.initialize(inventory.getStackInSlot(slot),mob.getRandom());
                // Migration can find a unit temporarily holding a grenade/drone instead of its gun.
                var state=mob.getPersistentData();
                for(String key:List.of("sewv:grenade_stash_main","sewv:grenade_stash_off","sewv_drone_stash_main","sewv_drone_stash_off"))if(state.contains(key)){
                    var stack=NpcEquipment.loadStack(state.getCompound(key));api.initialize(stack,mob.getRandom());state.put(key,NpcEquipment.saveStack(stack));
                }
            }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot initialize carried gun quality",error);}
        }
        mob.getPersistentData().putBoolean(INITIALIZED,true);
    }
}
