package local.gnreview;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import local.gnfrontier.*;
import local.gnfrontier.client.KickClient;
import local.gnfrontier.client.PdaScreen;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

/** Exercises the normal input mappings and game state in the reserved review world. */
public final class VideoSpecChecks {
    private static final String BACKUP = "gn_visual_video_spec_0213";

    private static final BlockPos CAR = new BlockPos(4088, 179, 4038);
    private static final BlockPos REAR = VehicleAssemblies.rearPosition(CAR, Direction.NORTH);
    private static final BlockPos PAD_START = CAR.offset(-2, -1, -2);
    private static final BlockPos PAD_END = REAR.offset(2, -1, 2);
    private static final AABB DROP_ZONE = new AABB(PAD_START, PAD_END.offset(1, 4, 1));

    public static Map<String,Object> server(ServerPlayer player, String action, Properties settings) {
        var level = player.serverLevel();
        if (!player.getServer().getWorldData().getLevelName().equals("GN_Review_r39_creation")) throw new IllegalStateException("Owned review world required");
        var data = player.getPersistentData();
        if (action.equals("stage")) {
            if (data.contains(BACKUP)) throw new IllegalStateException("Restore previous probe first");
            var save = new CompoundTag();
            save.put("inventory", player.getInventory().save(new ListTag()));
            save.putInt("selected", player.getInventory().selected);
            save.put("food", new CompoundTag());
            player.getFoodData().addAdditionalSaveData(save.getCompound("food"));
            save.putFloat("health", player.getHealth());
            save.putBoolean("invulnerable", player.isInvulnerable());
            save.putInt("mode", player.gameMode.getGameModeForPlayer().getId());
            save.putDouble("x", player.getX()); save.putDouble("y", player.getY()); save.putDouble("z", player.getZ());
            save.putFloat("yaw", player.getYRot()); save.putFloat("pitch", player.getXRot());
            save.putBoolean("flying", player.getAbilities().flying);
            data.put(BACKUP, save);
            player.setGameMode(GameType.SURVIVAL);
            player.setInvulnerable(true);
            player.setHealth(8);player.getFoodData().setFoodLevel(10);player.getFoodData().setSaturation(0);
            player.getInventory().clearContent();
            var milk = new ItemStack(TradeService.item("lrtactical:consumable"));
            milk.getOrCreateTag().putString("ConsumableId", "lrtactical:condensed_milk");
            player.getInventory().setItem(0, new ItemStack(Frontier.ROUGH_PICKAXE.get()));
            player.getInventory().setItem(1, milk);
            player.getInventory().setItem(2, new ItemStack(Frontier.METAL_SCRAP.get(), 4));
            player.getInventory().setItem(3, new ItemStack(Frontier.ALUMINUM_SCRAP.get(), 4));
            player.getInventory().setItem(4, new ItemStack(Frontier.WASTE_BATTERY.get()));
            player.getInventory().setItem(9, new ItemStack(Frontier.ROUGH_IRON.get(), 2));
            player.getInventory().setItem(10, new ItemStack(Frontier.PLASTIC_FRAGMENT.get(), 3));
            player.getInventory().selected = 0;
            player.connection.send(new ClientboundSetCarriedItemPacket(0));
            int x = 4088, z = 4038;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            player.teleportTo(level, x+.5, y, z+.5, 180, 0);
            player.containerMenu.broadcastChanges();
            return Map.of("position", player.position().toString(), "health",8,"food",10);
        }
        if (!data.contains(BACKUP)) throw new IllegalStateException("Stage the review fixture");
        var save = data.getCompound(BACKUP);
        switch (action) {
            case "window" -> {
                BlockPos pos = player.blockPosition().north(2);
                if (save.contains("window") || level.getBlockEntity(pos) != null || level.getBlockEntity(pos.above()) != null
                        || Gameplay.protectedAt(level, pos)) throw new IllegalStateException("Unused, unprotected window site required");
                save.putLong("window", pos.asLong());
                save.putInt("bottom", Block.getId(level.getBlockState(pos)));
                save.putInt("top", Block.getId(level.getBlockState(pos.above())));
                level.setBlock(pos, Blocks.GLASS_PANE.defaultBlockState(), 3);
                level.setBlock(pos.above(), Blocks.GLASS_PANE.defaultBlockState(), 3);
                player.teleportTo(level, pos.getX()+.5,pos.getY(),pos.getZ()+2.1,180,0);
                return Map.of("pos",pos.toString(), "ready",true);
            }
            case "window-result" -> {
                var pos = BlockPos.of(save.getLong("window"));
                boolean clear = level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
                if (!clear) throw new AssertionError("Two window blocks must break through the normal kick request");
                return Map.of("bothWindowBlocksRemoved", true, "forwardDelta", pos.getZ()+2.1-player.getZ());
            }
            case "stance", "vitals" -> {
                return Map.of("pose", player.getPose().name(), "eyeHeight",player.getEyeHeight(),
                        "health",player.getHealth(),"food",player.getFoodData().getFoodLevel(),"saturation",player.getFoodData().getSaturationLevel());
            }
            case "car-progress" -> {
                var tool = player.getMainHandItem().copy();
                List<Map<String,Object>> states = new ArrayList<>();
                try {
                    for (var block : ForgeRegistries.BLOCKS.getValues()) {
                        var state = block.defaultBlockState();
                        if (!state.is(Frontier.SALVAGE_VEHICLES)) continue;
                        var part = ForgeRegistries.BLOCKS.getKey(block).toString();
                        int slot = player.getInventory().selected;
                        player.getInventory().setItem(slot, ItemStack.EMPTY);
                        float bare = state.getDestroyProgress(player, level, player.blockPosition());
                        player.getInventory().setItem(slot, new ItemStack(Frontier.ROUGH_PICKAXE.get()));
                        float pick = state.getDestroyProgress(player, level, player.blockPosition());
                        player.getInventory().setItem(slot, new ItemStack(TradeService.item("create:wrench")));
                        float wrench = state.getDestroyProgress(player, level, player.blockPosition());
                        if (bare != 0 || Math.abs(pick-1f/56)>0.0001 || Math.abs(wrench-1f/56)>0.0001)
                            throw new AssertionError("Unexpected mining progress for " + part + ": " + bare + "/" + pick + "/" + wrench);
                        var veto = new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, player.blockPosition(), state,player);
                        player.getInventory().setItem(slot, ItemStack.EMPTY);
                        if (!MinecraftForge.EVENT_BUS.post(veto)) throw new AssertionError("Bare hand not denied by server");
                        player.getInventory().setItem(slot, new ItemStack(Items.IRON_AXE));
                        if (state.getDestroyProgress(player, level, player.blockPosition()) != 0)
                            throw new AssertionError("Axe must be unsuitable for " + part);
                        states.add(Map.of("part",part,"bareHand",bare,"pickaxe",pick,"wrench",wrench,"bareHandBreakCancelled",true));
                    }
                } finally { player.getInventory().setItem(player.getInventory().selected, tool); }
                if (states.isEmpty()) throw new AssertionError("No tagged vehicle blocks");
                return Map.of("rows",states,"taggedBlocks",states.size(),"pairedModels",VehicleAssemblies.MODELS.size());
            }
            case "car-stage" -> {
                if (save.contains("car") || !level.hasChunkAt(CAR) || !level.getEntitiesOfClass(ItemEntity.class, DROP_ZONE).isEmpty())
                    throw new IllegalStateException("Unused vehicle test site required");
                for (BlockPos pos : BlockPos.betweenClosed(PAD_START, PAD_END.above(4))) {
                    if (!level.getBlockState(pos).isAir()) throw new IllegalStateException("Occupied vehicle test site: " + pos);
                }
                save.putBoolean("car", true);
                for (BlockPos pos : BlockPos.betweenClosed(PAD_START, PAD_END)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                var model = VehicleAssemblies.MODELS.stream().filter(row -> row.front().equals("green_jeep_1")).findFirst().orElseThrow();
                if (!VehicleAssemblies.place(level, CAR, Direction.NORTH, model)) throw new AssertionError("Unable to stage a complete jeep");
                player.teleportTo(level, CAR.getX()+.5, CAR.getY(), CAR.getZ()-1.5, 0, 13);
                var original = player.getMainHandItem().copy();
                try {
                    player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);
                    if (player.gameMode.destroyBlock(CAR) || level.getBlockState(CAR).isAir() || level.getBlockState(REAR).isAir())
                        throw new AssertionError("Native bare-hand break must keep both vehicle parts");
                } finally { player.getInventory().setItem(player.getInventory().selected, original); }
                return Map.of("front", CAR.toString(), "rear", REAR.toString(), "bareHandDenied", true);
            }
            case "car-break-pick", "car-break-wrench" -> {
                if (!save.contains("car")) throw new IllegalStateException("Stage the native vehicle first");
                boolean wrench = action.endsWith("wrench");
                var original = player.getMainHandItem().copy();
                var tool = wrench ? TradeService.item("create:wrench") : Frontier.ROUGH_PICKAXE.get();
                var drops = new CompoundTag();
                Consumer<EntityJoinLevelEvent> observe = event -> {
                    if (event.getLevel() != level || !(event.getEntity() instanceof ItemEntity item) || !DROP_ZONE.contains(item.position())) return;
                    var stack = item.getItem();
                    var key = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                    drops.putInt(key, drops.getInt(key) + stack.getCount());
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, observe);
                try {
                    player.getInventory().setItem(player.getInventory().selected, new ItemStack(tool));
                    if (!player.gameMode.destroyBlock(wrench ? REAR : CAR)) throw new AssertionError("Native salvage failed for " + tool);
                } finally {
                    player.getInventory().setItem(player.getInventory().selected, original);
                    MinecraftForge.EVENT_BUS.unregister(observe);
                }
                save.putBoolean("wrench", wrench);
                save.put("drops", drops);
                return Map.of("nativeBreak", true, "wrench",wrench,"itemsSpawned",drops.toString());
            }
            case "car-drops" -> {
                if (!save.contains("car") || !level.getBlockState(CAR).isAir() || !level.getBlockState(REAR).isAir())
                    throw new AssertionError("A single break must clear both parts");
                boolean wrench = save.getBoolean("wrench");
                var record = save.getCompound("drops");
                Map<String,Integer> drops = new TreeMap<>();
                for (String key : record.getAllKeys()) drops.put(key, record.getInt(key));
                if (drops.getOrDefault("gnfrontier:rough_iron_ingot", 0) != (wrench ? 3 : 2)
                        || drops.getOrDefault("gnfrontier:metal_scrap", 0) != (wrench ? 6 : 4)
                        || drops.getOrDefault("gnfrontier:aluminum_scrap", 0) != (wrench ? 6 : 4))
                    throw new AssertionError("Expected a single native salvage drop: " + drops);
                clearCar(player, save);
                return Map.of("bothPartsRemoved",true,"drops",drops);
            }
            case "faction" -> {
                var world = WorldState.get(level);
                var account = world.account(player.getUUID());
                var old = account.copy();
                var unit = (Mob)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("simpleenemymod:ruunit")).create(level);
                try {
                    account.reputation[0] = -20; account.alert[0] = 30;
                    DiplomacyBridge.sync(player, account); unit.setTarget(player);
                    boolean hostile = FactionRelations.hostileTo(unit,player) && unit.getTarget()==player;
                    account.reputation[0] = 30; account.alert[0] = 0;
                    DiplomacyBridge.sync(player, account);unit.setTarget(player);
                    boolean allied = !FactionRelations.hostileTo(unit,player) && unit.getTarget()==null;
                    if (!hostile || !allied) throw new AssertionError("Standing did not reach native targeting: "+hostile+"/"+allied);
                    return Map.of("enemyTargetAllowed",hostile,"allyTargetDenied",allied,"otherFactionUnchanged",account.reputation[1]==old.reputation[1]);
                } finally {
                    world.players.put(player.getUUID(), old);world.setDirty();DiplomacyBridge.sync(player, old);
                }
            }
            case "restore" -> {
                if (save.contains("car")) clearCar(player, save);
                if (save.contains("window")) {
                    BlockPos pos = BlockPos.of(save.getLong("window"));
                    level.setBlock(pos,Block.stateById(save.getInt("bottom")),3);
                    level.setBlock(pos.above(),Block.stateById(save.getInt("top")),3);
                }
                player.getInventory().load(save.getList("inventory", Tag.TAG_COMPOUND));
                player.getInventory().selected = save.getInt("selected");
                player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
                player.getFoodData().readAdditionalSaveData(save.getCompound("food"));
                player.setHealth(save.getFloat("health"));player.setInvulnerable(save.getBoolean("invulnerable"));
                player.setGameMode(GameType.byId(save.getInt("mode")));player.getAbilities().flying = save.getBoolean("flying");player.onUpdateAbilities();
                player.teleportTo(level,save.getDouble("x"),save.getDouble("y"),save.getDouble("z"),save.getFloat("yaw"),save.getFloat("pitch"));
                player.containerMenu.broadcastChanges();data.remove(BACKUP);
                return Map.of("restored",true);
            }
            default -> throw new IllegalArgumentException(action);
        }
    }

    private static void clearCar(ServerPlayer player, CompoundTag save) {
        var level = player.serverLevel();
        level.getEntitiesOfClass(ItemEntity.class, DROP_ZONE).forEach(ItemEntity::discard);
        for (BlockPos pos : BlockPos.betweenClosed(PAD_START, PAD_END.above(4))) level.removeBlock(pos, false);
        save.remove("car");
        save.remove("wrench");
        save.remove("drops");
    }

    public static Map<String,Object> client(String action, Properties settings) throws Exception {
        var mc = Minecraft.getInstance();
        if (mc.player == null) throw new IllegalStateException("Owned review player required");
        switch (action) {
            case "keys" -> {
                var pda = key("key.campchat.open_chat");
                var map = key("gui.xaero_open_map");
                if (pda.getKey().getValue()!=77 || !map.isUnbound()) throw new AssertionError("Old map routing still active: "+pda.getKey()+"/"+map.getKey());
                KeyMapping.click(pda.getKey());
                return Map.of("pda",pda.saveString(), "mapUnbound",true);
            }
            case "pda-state" -> {return Map.of("isNativePda",PdaScreen.isNative(mc.screen),"tab",PdaScreen.tab(mc.screen));}
            case "map-blocked" -> {
                Screen pda = mc.screen;
                if (!PdaScreen.isNative(pda)) throw new IllegalStateException("Native PDA required");
                var field = pda.getClass().getDeclaredField("embeddedMap");
                field.setAccessible(true);
                var map = (Screen) field.get(pda);
                if (map == null) throw new AssertionError("Embedded map unavailable");
                mc.setScreen(map);
                if (mc.screen != pda) throw new AssertionError("Standalone map was opened");
                return Map.of("standaloneMapBlocked", true, "embeddedMapAvailable", true);
            }
            case "inventory" -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            case "close" -> mc.setScreen(null);
            case "food" -> { mc.player.getInventory().selected=1; mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); }
            case "prone" -> {
                key("key.tacz.crawl.desc").setDown(true);
                Class.forName("com.tacz.guns.client.input.CrawlKey").getMethod("setControllerCrawlDown",boolean.class).invoke(null,true);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            case "prone-off" -> {
                key("key.tacz.crawl.desc").setDown(false);
                Class.forName("com.tacz.guns.client.input.CrawlKey").getMethod("setControllerCrawlDown",boolean.class).invoke(null,false);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            case "lean-left", "lean-right", "lean-off" -> {
                key("key.taczexpert.lean_left").setDown(action.equals("lean-left"));
                key("key.taczexpert.lean_right").setDown(action.equals("lean-right"));
            }
            case "lean-state" -> {
                Method amount = Class.forName("com.ori.taczplus.client.LeanClient").getMethod("amount", float.class);
                return Map.of("lean", amount.invoke(null,1f),"eyePosition",mc.gameRenderer.getMainCamera().getPosition().toString());
            }
            case "kick" -> {
                Path evidence = Path.of(settings.getProperty("path")).resolveSibling("kick-extended.png");
                new KickFrame(evidence).listen();
                KeyMapping.click(key("key.gnfrontier.kick").getKey());
            }
            default -> throw new IllegalArgumentException(action);
        }
        return Map.of("action",action,"keysThroughGameOnly",true);
    }

    private static KeyMapping key(String name) {
        return Arrays.stream(Minecraft.getInstance().options.keyMappings).filter(k->k.getName().equals(name)).findFirst().orElseThrow();
    }

    private static final class KickFrame {
        private final Path path;
        private final Consumer<TickEvent.RenderTickEvent> listener = this::render;
        private int frames;
        KickFrame(Path path) { this.path=path; }
        void listen() { MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TickEvent.RenderTickEvent.class, listener); }
        void render(TickEvent.RenderTickEvent event) {
            if(event.phase!=TickEvent.Phase.END)return;
            var mc=Minecraft.getInstance();
            if(mc.player==null||++frames>120){MinecraftForge.EVENT_BUS.unregister(listener);return;}
            if(KickClient.strength(mc.player.getId(),event.renderTickTime)<.75f)return;
            MinecraftForge.EVENT_BUS.unregister(listener);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                if(Files.exists(path))throw new IllegalStateException("Fresh image required");image.writeToFile(path);
            }catch(java.io.IOException error){throw new RuntimeException(error);}
        }
    }
}
