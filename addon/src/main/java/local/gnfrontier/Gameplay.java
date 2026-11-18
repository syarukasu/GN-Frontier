package local.gnfrontier;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.logging.LogUtils;
import local.gnfrontier.core.Economy;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class Gameplay {
    private static final String MARKER = "gnfrontier_spawned";
    private final Map<Integer, BreakProgress> breaking = new HashMap<>();
    private record BreakProgress(BlockPos pos, int ticks) {}
    public static String key(Level level, BlockPos pos) { return level.dimension().location() + ":" + pos.asLong(); }
    private static boolean active(Level level) { return level instanceof ServerLevel server && WorldState.get(server).enabled; }
    private static long clock(ServerLevel level) { return level.getServer().overworld().getGameTime(); }
    private static boolean salvage(BlockState state) { return state.is(Frontier.SALVAGE_VEHICLES) || state.is(Frontier.SALVAGE_APPLIANCES) || state.is(Frontier.SALVAGE_METAL); }
    // Like the pickaxe block tag, tool eligibility is shared by client and server.
    // The wrench still uses normal block breaking, including protection and creative rules.
    @SubscribeEvent public void vehicleHarvest(PlayerEvent.HarvestCheck event) {
        if(event.getTargetBlock().is(Frontier.SALVAGE_VEHICLES)
                && (!(event.getEntity().level() instanceof ServerLevel level) || WorldState.get(level).enabled)) {
            event.setCanHarvest(SalvageLoot.canDismantle(event.getEntity().getMainHandItem()));
        }
    }
    public static boolean protectedAt(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        if (level.getBlockState(pos).getBlock() instanceof BedBlock) return false;
        return withinShelter(level, pos);
    }
    public static boolean withinShelter(ServerLevel level, BlockPos pos) {
        return RespawnRules.withinArea(level, Vec3.atBottomCenterOf(pos));
    }
    @SubscribeEvent public void started(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld(); WorldState data = WorldState.get(level);
        if (level.getGameTime() == 0) { data.enabled = true; data.setDirty(); }
        for (Economy.Offer o : Economy.OFFERS) if (!TradeService.exists(o.item())) LogUtils.getLogger().error("GN Frontier missing trade item: {}", o.item());
        LogUtils.getLogger().info("GN Frontier loaded. World enabled={}, deletion={}, gas={}", data.enabled, PrototypeConfig.DELETE_ON_DEATH.get(), PrototypeConfig.GAS.get());
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gn")
            .then(Commands.literal("help").executes(c->{
                for(String line:List.of("GN：MキーでPDAを呼び出して地図・売買・依頼を開きます。端末の携帯は不要です。", "廃車はツルハシかCreateのレンチで解体できます。レンチは回収量が増えます。窓蹴りは操作設定のGN Frontier（初期Vキー）。", "地下ではマスクを装備し、交換フィルターを右クリック。死亡すると携行品を失います。", "リグは設置後にCPU・GPUを右クリックで装着し、Forge Energyを供給。", "衛星装置はAd Astraの宇宙で使用。Noahは火星・Glacioの環境改変器に投入。"))
                    c.getSource().sendSuccess(()->Component.literal(line),false);return 1;}))
            .then(Commands.literal("pda").executes(c -> { ServerPlayer p = c.getSource().getPlayerOrException();
                if (p.isAlive() && !p.isSpectator() && !Hypoxia.down(p)) FrontierNetwork.send(p, ""); return 1; }))
            .then(Commands.literal("enable").requires(s -> s.hasPermission(2))
                .then(Commands.argument("value", BoolArgumentType.bool()).executes(c -> {
                    WorldState d = WorldState.get(c.getSource().getLevel()); d.enabled = BoolArgumentType.getBool(c, "value"); d.setDirty();
                    if(d.enabled)EnvironmentEvents.activate(c.getSource().getServer());AtmosphereOverrides.refresh(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("GN rules: " + d.enabled), true); return 1; })))
            .then(Commands.literal("status").executes(c -> { WorldState d = WorldState.get(c.getSource().getLevel());
                c.getSource().sendSuccess(() -> Component.literal("GN enabled=" + d.enabled + " / death deletion=" + PrototypeConfig.DELETE_ON_DEATH.get()), false); return 1; })));
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !active(p.level())) return;
        WorldState data = WorldState.get(p.serverLevel()); Economy.State account = data.account(p.getUUID());
        RespawnRules.loggedIn(p, !data.starterGiven.contains(p.getUUID()));
        // Retire only the obsolete terminal, keeping saved currency and quest progress intact.
        for (int slot=0;slot<p.getInventory().getContainerSize();slot++)
            if (p.getInventory().getItem(slot).is(Frontier.PDA.get())) p.getInventory().setItem(slot, ItemStack.EMPTY);
        if (!data.starterGiven.contains(p.getUUID())) {
            data.starterGiven.add(p.getUUID()); data.setDirty();
            p.sendSystemMessage(Component.literal("GN Frontier: MキーでPDAの地図と取引を開けます。/gn pda"));
        }
        DiplomacyBridge.sync(p, account);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { FrontierNetwork.forget(event.getEntity().getUUID()); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void deposit(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !active(player.level()) || !player.isAlive() || player.isSpectator()) return;
        if (!new ResourceLocation("survival_instinct:money").equals(ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem()))) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        int count = player.isShiftKeyDown() ? event.getItemStack().getCount() : 1;
        WorldState world = WorldState.get(player.serverLevel());
        try {
            Economy.State after = Economy.depositNotes(world.account(player.getUUID()), count);
            event.getItemStack().shrink(count); world.players.put(player.getUUID(), after); world.setDirty();
            player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
            player.sendSystemMessage(Component.literal("$" + count * 10 + " をPDA口座へ入金しました"));
        } catch (IllegalArgumentException ex) { player.sendSystemMessage(Component.literal(ex.getMessage())); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !active(p.level()) || event.getHand() != InteractionHand.MAIN_HAND || event.isCanceled()) return;
        ServerLevel level = p.serverLevel(); BlockPos pos = event.getPos(); BlockState state = level.getBlockState(pos);
        if (state.is(Frontier.UNHYGIENIC_BEDS)) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL);
            p.displayClientMessage(Component.translatable("message.gnfrontier.unhygienic_bed"), true);
            return;
        }
        if (state.getBlock() instanceof BedBlock) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
            // GN beds register a respawn point; registration must never enter the sleeping state.
            RespawnRules.registerBed(p, level, pos);
            return;
        }
        if ((state.getBlock() instanceof FurnaceBlock || state.getBlock() instanceof BlastFurnaceBlock) && !p.isCreative()) {
            event.setCanceled(true); p.sendSystemMessage(Component.translatable("message.gnfrontier.furnace_needs_repair")); return;
        }
        // A vehicle uses normal model mining and its real loot table; no right-click whole-car deletion.
        if (state.is(Frontier.SALVAGE_VEHICLES)) return;
        if (!event.getItemStack().is(Frontier.PRYBAR.get()) || !salvage(state) || p.isSpectator()) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        if (p.getCooldowns().isOnCooldown(Frontier.PRYBAR.get())) return;
        WorldState data = WorldState.get(level);
        if (data.placedBlocks.contains(key(level, pos))) { p.sendSystemMessage(Component.literal("設置した資材は解体回収の対象外です")); return; }
        // No container contents or block-entity inventories may be deleted by salvage.
        if (level.getBlockEntity(pos) != null || !level.mayInteract(p, pos) || !p.mayBuild()) return;
        if (net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(level, p.gameMode.getGameModeForPlayer(), p, pos) == -1) return;
        boolean vehicle = state.is(Frontier.SALVAGE_VEHICLES), appliance = state.is(Frontier.SALVAGE_APPLIANCES);
        if (vehicle || appliance) {
            for (String id : List.of("tfmg:copper_wire", "tfmg:rubber_sheet", "survival_instinct:batteries", "survival_instinct:electronic_scrap")) {
                if (!TradeService.exists(id)) { p.sendSystemMessage(Component.literal("解体に必要なMODがありません: " + id)); return; }
            }
        }
        if (!level.destroyBlock(pos, false, p)) return;
        drop(level, pos, new ItemStack(Frontier.METAL_SCRAP.get(), vehicle ? 8 : appliance ? 4 : 1));
        if (vehicle || appliance) {
            drop(level, pos, new ItemStack(Frontier.ALUMINUM_SCRAP.get(), vehicle ? 4 : 2));
            drop(level, pos, new ItemStack(Frontier.PLASTIC_FRAGMENT.get(), 1));
            drop(level, pos, new ItemStack(TradeService.item("tfmg:copper_wire"), 2));
            if (vehicle) drop(level, pos, new ItemStack(TradeService.item("tfmg:rubber_sheet"), 1));
            if (level.random.nextInt(4) == 0) drop(level, pos, new ItemStack(Frontier.WASTE_BATTERY.get(), 1));
            if (appliance) drop(level, pos, new ItemStack(TradeService.item("survival_instinct:electronic_scrap"), 1));
        }
        if (!p.isCreative()) event.getItemStack().hurtAndBreak(1, p, who -> who.broadcastBreakEvent(event.getHand()));
        p.getCooldowns().addCooldown(Frontier.PRYBAR.get(), 20);
    }
    private static void drop(ServerLevel level, BlockPos pos, ItemStack stack) { Block.popResource(level, pos, stack); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player) || !active(level)) return;
        BlockState state = event.getPlacedBlock(); if (!salvage(state) && !(state.getBlock() instanceof BedBlock)) return;
        WorldState data = WorldState.get(level); data.placedBlocks.add(key(level, event.getPos()));
        if (state.getBlock() instanceof BedBlock) {
            BlockPos other = event.getPos().relative(state.getValue(BedBlock.FACING), state.getValue(BedBlock.PART) == BedPart.FOOT ? 1 : -1);
            data.placedBlocks.add(key(level, other));
        }
        data.setDirty();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void sleep(PlayerSleepInBedEvent event) {
        if (active(event.getEntity().level())) event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void sleepTime(SleepFinishedTimeEvent event) {
        if (event.getLevel() instanceof ServerLevel level && active(level)) event.setTimeAddition(level.getDayTime());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void death(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level) || !active(level)) return;
        if (event.getEntity() instanceof ServerPlayer player && PrototypeConfig.DELETE_ON_DEATH.get()) player.getInventory().clearContent();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void drops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && active(p.level()) && PrototypeConfig.DELETE_ON_DEATH.get()) event.getDrops().clear();
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void grief(EntityMobGriefingEvent event) {
        if (RegisteredBedGoal.checkingBed(event.getEntity())) return;
        if (event.getEntity().level() instanceof ServerLevel level && active(level) && protectedAt(level, event.getEntity().blockPosition())) event.setResult(Event.Result.DENY);
    }
    @SubscribeEvent public void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && active(level)) event.getAffectedBlocks().removeIf(pos -> protectedAt(level, pos)||RespawnRules.explosionProtectedBed(level,pos));
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer(); ServerLevel level = server.overworld(); WorldState data = WorldState.get(level);
        if (!data.enabled) return;
        long tick = clock(level); data.refresh(level);
        if (tick % 1200 == 0) for (ServerPlayer p : server.getPlayerList().getPlayers()) DiplomacyBridge.sync(p, data.account(p.getUUID()));
        if (tick % 200 == 0 && PrototypeConfig.DAY_ZOMBIES.get() && level.isDay() && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
            && level.getDifficulty() != Difficulty.PEACEFUL) {
            // Bounded work: at most one candidate per player, cap local zombies and never load a new chunk.
            for (ServerPlayer p : level.players()) {
                if (p.isCreative() || p.isSpectator() || level.getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(48)).size() >= 10) continue;
                int x = p.getBlockX() + level.random.nextInt(65) - 32, z = p.getBlockZ() + level.random.nextInt(65) - 32;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (pos.distSqr(p.blockPosition()) < 24 * 24 || protectedAt(level, pos) || !level.getFluidState(pos).isEmpty()) continue;
                Zombie zombie = EntityType.ZOMBIE.create(level); if (zombie == null) continue;
                zombie.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
                if (!level.noCollision(zombie)) continue;
                if(!daySpawnAllowed(zombie,level))continue;
                zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
                level.addFreshEntity(zombie);
            }
        }
    }
    public static boolean daySpawnAllowed(Zombie zombie,ServerLevel level){
        // Deliberate daylight spawning skips vanilla's darkness condition, but not Forge/In Control vetoes.
        var event=new net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck(zombie,level,MobSpawnType.NATURAL,null);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        return event.getResult()!=Event.Result.DENY;
    }
    @SubscribeEvent public void join(EntityJoinLevelEvent event) {
        if(event.getLevel() instanceof ServerLevel server&&active(server)&&event.getEntity() instanceof Mob mob)RegisteredBedGoal.install(mob);
        if (!(event.getEntity() instanceof Zombie z) || !(event.getLevel() instanceof ServerLevel level) || !active(level)) return;
        if (z.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof RegisteredBedGoal))
            z.goalSelector.addGoal(1, new RegisteredBedGoal(z));
        if (!EnhancedAiCompat.present() && z.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof RodZombieGoal))
            z.goalSelector.addGoal(1, new RodZombieGoal(z));
        if (!z.getPersistentData().getBoolean(MARKER)) {
            z.getPersistentData().putBoolean(MARKER, true);
            if (event.loadedFromDisk()) return;
            NightZombies.initializeFresh(z,level);
            if (!EnhancedAiCompat.present() && level.random.nextInt(8) == 0) { z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE)); z.setDropChance(EquipmentSlot.MAINHAND, 0); }
            else if (!EnhancedAiCompat.present() && PrototypeConfig.ROD_ZOMBIES.get() && z.getMainHandItem().isEmpty() && level.random.nextInt(PrototypeConfig.ROD_CHANCE.get()) == 0) {
                z.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.FISHING_ROD));z.setDropChance(EquipmentSlot.MAINHAND,0);
            }
        }
    }
    @SubscribeEvent public void livingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Zombie z) || !(z.level() instanceof ServerLevel level) || !active(level)) return;
        if (EnhancedAiCompat.present()) return;
        if (z.tickCount % 10 != 0) return;
        if (z.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof RegisteredBedGoal)) { breaking.remove(z.getId()); return; }
        if (!PrototypeConfig.BREAK_BLOCKS.get() || !(z.getMainHandItem().getItem() instanceof PickaxeItem) || z.getTarget() == null) { breaking.remove(z.getId()); return; }
        Vec3 direction = z.getTarget().position().subtract(z.position()).normalize();
        BlockPos target = BlockPos.containing(z.getX() + direction.x * 1.2, z.getY() + 1, z.getZ() + direction.z * 1.2);
        BlockState block = level.getBlockState(target);
        if (!block.is(Frontier.BREAKABLE) || level.getBlockEntity(target) != null || protectedAt(level, target)
                || !net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level, z)) { breaking.remove(z.getId()); return; }
        BreakProgress old = breaking.get(z.getId()); int ticks = old != null && old.pos.equals(target) ? old.ticks + 10 : 10;
        if (ticks >= 100) {
            if (net.minecraftforge.event.ForgeEventFactory.onEntityDestroyBlock(z, target, block)) level.destroyBlock(target, false, z);
            breaking.remove(z.getId());
        } else breaking.put(z.getId(), new BreakProgress(target.immutable(), ticks));
    }
    @SubscribeEvent public void leave(EntityLeaveLevelEvent event) { breaking.remove(event.getEntity().getId()); }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { breaking.clear(); }
}
