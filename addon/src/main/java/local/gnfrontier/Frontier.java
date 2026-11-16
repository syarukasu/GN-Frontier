package local.gnfrontier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import net.minecraft.server.level.ServerPlayer;

@Mod(Frontier.ID)
public final class Frontier {
    public static final String ID = "gnfrontier";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,ID);
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<CorpseMenu>> CORPSE_MENU=MENUS.register("corpse",()->net.minecraftforge.common.extensions.IForgeMenuType.create(CorpseMenu::new));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<WeaponRepairMenu>> WEAPON_REPAIR_MENU=MENUS.register("weapon_repair",()->net.minecraftforge.common.extensions.IForgeMenuType.create(WeaponRepairMenu::new));
    public static final RegistryObject<EntityType<FallenSoldier>> FALLEN_SOLDIER = ENTITIES.register("fallen_soldier", () ->
            EntityType.Builder.<FallenSoldier>of(FallenSoldier::new,MobCategory.MISC).sized(1.8f,.45f).clientTrackingRange(8).updateInterval(20).build("gnfrontier:fallen_soldier"));
    public static final RegistryObject<EntityType<ScavengerHook>> SCAVENGER_HOOK = ENTITIES.register("scavenger_hook", () ->
            EntityType.Builder.<ScavengerHook>of(ScavengerHook::new,MobCategory.MISC).sized(.25f,.25f).clientTrackingRange(32).updateInterval(1).build("gnfrontier:scavenger_hook"));
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ID);
    public static final RegistryObject<RecipeSerializer<TorchLightingRecipe>> TORCH_LIGHTING = RECIPES.register("torch_lighting", TorchLightingRecipe.Serializer::new);
    public static final RegistryObject<Item> METAL_SCRAP = item("metal_scrap");
    public static final RegistryObject<Item> ROUGH_IRON = item("rough_iron_ingot");
    public static final RegistryObject<Item> ALUMINUM_SCRAP = item("aluminum_scrap");
    public static final RegistryObject<Item> ALUMINUM_CHUNK = item("aluminum_chunk");
    public static final RegistryObject<Item> PLASTIC_FRAGMENT = item("plastic_fragment");
    public static final RegistryObject<Item> WASTE_BATTERY = item("waste_battery");
    public static final RegistryObject<Item> COMPOST = ITEMS.register("compost", () -> new BoneMealItem(new Item.Properties()));
    public static final RegistryObject<Item> FLUXED_IRON = item("fluxed_iron");
    public static final RegistryObject<Item> SCRAP_BUNDLE = item("scrap_bundle");
    public static final RegistryObject<Item> UNLIT_TORCH = item("unlit_torch");
    public static final RegistryObject<Item> TOUGH_CLOTH = item("tough_cloth");
    public static final RegistryObject<Item> FILTER = item("gas_filter");
    public static final RegistryObject<Item> CPU_I3 = item("cpu_i3_6100");
    public static final RegistryObject<Item> CPU_PENTIUM = item("cpu_pentium_g4560");
    public static final RegistryObject<Item> CPU_I5 = item("cpu_i5_2500k");
    public static final RegistryObject<Item> GPU_8800 = item("gpu_8800_gtx");
    public static final RegistryObject<Item> GPU_480 = item("gpu_gtx_480");
    public static final RegistryObject<Item> CHROMIUM = item("chromium_concentrate");
    public static final RegistryObject<Item> CHROMOLY = item("chromoly_steel");
    public static final RegistryObject<Item> RECEIVER = item("modern_receiver");
    public static final RegistryObject<Item> RAW_RECEIVER = item("unfinished_receiver");
    public static final RegistryObject<Item> PERMIT_I = item("military_permit_i");
    public static final RegistryObject<Item> PERMIT_II = item("military_permit_ii");
    public static final RegistryObject<Item> PERMIT_III = item("military_permit_iii");
    public static final RegistryObject<Item> SATELLITE = ITEMS.register("satellite_uplink", () -> new CampaignDevice("satellite"));
    public static final RegistryObject<Item> DETERRENCE = ITEMS.register("deterrence_controller", () -> new CampaignDevice("deterrence"));
    public static final RegistryObject<Item> NOAH = ITEMS.register("noah_seed", () -> new CampaignDevice("noah"));
    public static final RegistryObject<Item> PRYBAR = ITEMS.register("prybar", () -> new Item(new Item.Properties().durability(192)));
    public static final RegistryObject<Item> ROUGH_PICKAXE = ITEMS.register("rough_iron_pickaxe", () -> new PickaxeItem(Tiers.STONE, 1, -2.8f, new Item.Properties()));
    public static final RegistryObject<Item> ROUGH_AXE = ITEMS.register("rough_iron_axe", () -> new AxeItem(Tiers.STONE, 5, -3.2f, new Item.Properties()));
    public static final RegistryObject<Item> PDA = ITEMS.register("pda", () -> new Item(new Item.Properties().stacksTo(1)) {
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) FrontierNetwork.send(serverPlayer, "");
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
    });
    public static final TagKey<Block> SALVAGE_VEHICLES = blockTag("salvage_vehicles");
    public static final TagKey<Block> SALVAGE_APPLIANCES = blockTag("salvage_appliances");
    public static final TagKey<Block> SALVAGE_METAL = blockTag("salvage_metal");
    public static final TagKey<Block> BREAKABLE = blockTag("zombie_breakable");
    public static final TagKey<Block> LOOT_FURNITURE = blockTag("loot_furniture");
    public static final TagKey<Block> UNHYGIENIC_BEDS = blockTag("unhygienic_beds");
    public static final TagKey<Item> GAS_MASKS = TagKey.create(Registries.ITEM, id("gas_masks"));
    private static RegistryObject<Item> item(String name) { return ITEMS.register(name, () -> new Item(new Item.Properties())); }
    private static TagKey<Block> blockTag(String name) { return TagKey.create(Registries.BLOCK, id(name)); }
    public static ResourceLocation id(String path) { return new ResourceLocation(ID, path); }
    public Frontier() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> local.gnfrontier.client.FallenSoldierRenderer.bind(bus));
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        RECIPES.register(bus);
        Infrastructure.BLOCKS.register(bus);
        Infrastructure.BLOCK_ENTITIES.register(bus);
        BedLoot.SERIALIZERS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PrototypeConfig.SPEC);
        FrontierNetwork.register();
        MinecraftForge.EVENT_BUS.register(new Gameplay());
        MinecraftForge.EVENT_BUS.register(new NaturalLoot());
        MinecraftForge.EVENT_BUS.register(new PersonalFurnitureLoot());
        MinecraftForge.EVENT_BUS.register(new DeathRules());
        MinecraftForge.EVENT_BUS.register(new RespawnRules());
        MinecraftForge.EVENT_BUS.register(new EnvironmentEvents());
        MinecraftForge.EVENT_BUS.register(new CampaignEvents());
        MinecraftForge.EVENT_BUS.register(new WindowKick());
        MinecraftForge.EVENT_BUS.register(new VehicleStreets());
        MinecraftForge.EVENT_BUS.register(new VehicleAssemblies());
        MinecraftForge.EVENT_BUS.register(new EnemyDrops());
        MinecraftForge.EVENT_BUS.register(new Hypoxia());
        MinecraftForge.EVENT_BUS.register(new StrategicEvents());
        MinecraftForge.EVENT_BUS.register(new FactionHostility());
        MinecraftForge.EVENT_BUS.register(new WeaponRepairKit());
    }
}
