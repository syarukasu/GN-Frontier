package local.gnfrontier;

import net.minecraftforge.common.ForgeConfigSpec;

public final class PrototypeConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DELETE_ON_DEATH, GAS, DAY_ZOMBIES, BREAK_BLOCKS, HYPOXIA, RAIDS;
    public static final ForgeConfigSpec.IntValue GAS_BELOW_Y;
    public static final ForgeConfigSpec.IntValue RAID_INTERVAL, RAID_CAP, FILTER_TICKS;
    public static final ForgeConfigSpec.IntValue STREET_VEHICLE_PERCENT;
    public static final ForgeConfigSpec.IntValue RESPAWN_GRACE, SHELTER_RADIUS, SHELTER_HEIGHT, BED_ATTACK_RANGE, BED_BREAK_TICKS;
    public static final ForgeConfigSpec.BooleanValue ROD_ZOMBIES;
    public static final ForgeConfigSpec.BooleanValue HUMAN_BED_ATTACKS, BED_APPROACH_BREAKING;
    public static final ForgeConfigSpec.IntValue ROD_RANGE, ROD_COOLDOWN, ROD_CHANCE;
    public static final ForgeConfigSpec.DoubleValue ROD_PULL;
    public static final ForgeConfigSpec.IntValue ROTOR_BASE_RADIUS;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Prototype balance, NOT original-series settings. Applies only to activated GN worlds.").push("rules");
        DELETE_ON_DEATH = b.comment("GN worlds only: carried inventory, crafting/cursor, Curios including cosmetics and entity item-handler. New-world default; enabled-world death is destructive.")
                .define("deleteInventoryOnDeath", true);
        GAS = b.define("lowAltitudeGas", true);
        GAS_BELOW_Y = b.defineInRange("gasBelowY", 40, -64, 320);
        DAY_ZOMBIES = b.define("dayZombies", true);
        BREAK_BLOCKS = b.comment("Allow only pickaxe zombies and tagged weak blocks; always obeys safe-area and Forge protection events.").define("pickaxeZombieBreaking", true);
        HYPOXIA=b.define("underwaterHypoxia",true);
        RAIDS=b.define("factionReinforcements",true);
        RAID_INTERVAL=b.defineInRange("raidIntervalTicks",12000,1200,1200000);
        RAID_CAP=b.defineInRange("nearbyRaidEntityCap",16,1,64);
        ROTOR_BASE_RADIUS=b.comment("NPC helicopters require a tagged military site within this horizontal distance; original distance unknown.").defineInRange("rotorBaseRadius",96,16,256);
        FILTER_TICKS=b.defineInRange("filterProtectionTicks",12000,200,1200000);
        STREET_VEHICLE_PERCENT=b.comment("One real-model car at most per eligible NEW street chunk, before clearance checks.").defineInRange("streetVehiclePercent",65,0,100);
        b.pop();
        b.comment("Independent reproduction settings; 10-second grace and bed AI timings are provisional.").push("respawn");
        RESPAWN_GRACE=b.defineInRange("graceTicks",200,0,12000);
        SHELTER_RADIUS=b.comment("Horizontal radius around the initial spawn or a registered, intact bed.").defineInRange("shelterRadius",5,0,32);
        SHELTER_HEIGHT=b.comment("Vertical half-height; the original protection shape is not confirmed.").defineInRange("shelterHalfHeight",5,0,32);
        BED_ATTACK_RANGE=b.defineInRange("pickaxeZombieBedSearchRadius",24,1,64);
        BED_BREAK_TICKS=b.defineInRange("pickaxeZombieBedBreakTicks",100,20,1200);
        HUMAN_BED_ATTACKS=b.comment("Armed factions and marked ChaosZ bandits approach registered beds and break them by hand. Uses the existing bed search radius and break duration.").define("humanBedAttacks",true);
        BED_APPROACH_BREAKING=b.comment("Allow only the selected route to a registered bed. Unbreakable blocks, fluids and other beds remain excluded; Forge vetoes and mobGriefing still apply.").define("bedApproachBlockBreaking",true);
        b.pop();
        b.comment("Fishing-rod enemy behavior is observed; spawn frequency, range and timing are reproduction settings.").push("rodZombie");
        ROD_ZOMBIES=b.define("enabled",true);
        ROD_RANGE=b.defineInRange("range",16,4,32);
        ROD_COOLDOWN=b.defineInRange("cooldownTicks",100,40,1200);
        ROD_CHANCE=b.defineInRange("spawnOneIn",16,1,256);
        ROD_PULL=b.defineInRange("pullStrength",.9,.1,2.0);
        b.pop(); SPEC = b.build();
    }
}
