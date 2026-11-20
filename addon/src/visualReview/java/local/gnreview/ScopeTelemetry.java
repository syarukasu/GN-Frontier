package local.gnreview;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.input.AimKey;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import local.gnfrontier.client.ScopeEyeRelief;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

/** Runtime diagnostics for the owned r39 world. Collects state and uniforms only. */
public final class ScopeTelemetry {
    private static final String WORLD = "GN_Review_r39_creation";
    private static final String BACKUP = "gn_review_scope_telemetry";
    private static final ResourceLocation GUN = new ResourceLocation("tacz:m95");
    private static final ResourceLocation SCOPE = new ResourceLocation("tacz:scope_acog_ta31");

    public static Map<String, Object> server(ServerPlayer player, String action) {
        if (!player.getServer().getWorldData().getLevelName().equals(WORLD)) {
            throw new IllegalStateException("Owned review world required");
        }
        ServerLevel level = player.serverLevel();
        var inventory = player.getInventory();
        if (action.equals("restore")) {
            if (!player.getPersistentData().contains(BACKUP)) return Map.of("restored", false);
            CompoundTag saved = player.getPersistentData().getCompound(BACKUP);
            if (!level.dimension().location().toString().equals(saved.getString("dimension"))) {
                throw new IllegalStateException("Original review dimension required");
            }
            player.teleportTo(level, saved.getDouble("x"), saved.getDouble("y"), saved.getDouble("z"),
                    saved.getFloat("yaw"), saved.getFloat("pitch"));
            player.setGameMode(GameType.byId(saved.getInt("gameMode")));
            player.setInvulnerable(saved.getBoolean("invulnerable"));
            inventory.setItem(0, ItemStack.of(saved.getCompound("slot0")));
            inventory.selected = saved.getInt("selected");
            player.connection.send(new ClientboundSetCarriedItemPacket(inventory.selected));
            player.containerMenu.broadcastChanges();
            BlockPos floor = BlockPos.of(saved.getLong("floor"));
            for (BlockPos pos : BlockPos.betweenClosed(floor, floor.offset(4, 0, 12))) {
                if (level.getBlockState(pos).is(Blocks.SMOOTH_STONE)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            player.getPersistentData().remove(BACKUP);
            return Map.of("restored", true);
        }
        if (!action.equals("stage") || player.getPersistentData().contains(BACKUP)) {
            throw new IllegalStateException("Unexpected scope staging state");
        }
        BlockPos floor = new BlockPos(player.chunkPosition().getMinBlockX() + 3,
                level.getMaxBuildHeight() - 12, player.chunkPosition().getMinBlockZ() + 1);
        for (BlockPos pos : BlockPos.betweenClosed(floor, floor.offset(4, 2, 12))) {
            if (!level.getBlockState(pos).is(Blocks.AIR)) throw new IllegalStateException("Review lane must be empty");
        }
        ItemStack gun = GunItemBuilder.create().setId(GUN).putAttachment(AttachmentType.SCOPE, SCOPE).build();
        IGun operator = IGun.getIGunOrNull(gun);
        if (operator == null || !SCOPE.equals(operator.getAttachmentId(gun, AttachmentType.SCOPE))) {
            throw new IllegalStateException("Review scope could not be installed");
        }
        CompoundTag saved = new CompoundTag();
        saved.putString("dimension", level.dimension().location().toString());
        saved.putDouble("x", player.getX());
        saved.putDouble("y", player.getY());
        saved.putDouble("z", player.getZ());
        saved.putFloat("yaw", player.getYRot());
        saved.putFloat("pitch", player.getXRot());
        saved.putInt("gameMode", player.gameMode.getGameModeForPlayer().getId());
        saved.putBoolean("invulnerable", player.isInvulnerable());
        saved.put("slot0", inventory.getItem(0).save(new CompoundTag()));
        saved.putInt("selected", inventory.selected);
        saved.putLong("floor", floor.asLong());
        player.getPersistentData().put(BACKUP, saved);
        for (BlockPos pos : BlockPos.betweenClosed(floor, floor.offset(4, 0, 12))) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        player.setInvulnerable(true);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, floor.getX() + 2.5, floor.getY() + 1, floor.getZ() + 1.5, 0, 8);
        inventory.setItem(0, gun);
        inventory.selected = 0;
        player.connection.send(new ClientboundSetCarriedItemPacket(0));
        player.containerMenu.broadcastChanges();
        return Map.of("gun", GUN.toString(), "scope", SCOPE.toString(), "floor", floor.toShortString());
    }

    public static CompletableFuture<Map<String, Object>> client() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null || !mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD)
                || mc.player == null || mc.getOverlay() != null) throw new IllegalStateException("Owned loaded client required");
        IGun gun = IGun.getIGunOrNull(mc.player.getMainHandItem());
        if (gun == null || !SCOPE.equals(gun.getAttachmentId(mc.player.getMainHandItem(), AttachmentType.SCOPE))) {
            throw new IllegalStateException("Wait for the scoped gun to synchronize");
        }
        Session session = new Session(mc);
        session.begin();
        return session.result;
    }

    private static final class Session {
        final Minecraft mc;
        final CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
        final List<Map<String, Object>> frames = new ArrayList<>();
        final Consumer<TickEvent.ClientTickEvent> tick = this::tick;
        final Consumer<TickEvent.RenderTickEvent> render = this::render;
        final IClientPlayerGunOperator aim;
        final CameraType camera;
        final int renderDistance;
        final java.lang.reflect.Method packActive;
        final java.lang.reflect.Method pipReady;
        final Object iris;
        final int program;
        final int eye;
        final int lens;
        final float zoom;
        int ticks = -20;
        int attempts;
        int activePackFrames;
        int readyLensFrames;
        int closedFrames;
        int openingFrames;
        int stillFrames;
        int movingFrames;
        int stoppedFrames;
        double minHorizontal = Double.POSITIVE_INFINITY;
        double maxHorizontal = Double.NEGATIVE_INFINITY;
        double maxBlur;
        long aimedAt;
        long firstClosedMs = -1;
        long firstOpeningMs = -1;
        long firstSettledMs = -1;
        Vec3 walkStart;
        Vec3 walkEnd;

        Session(Minecraft mc) throws Exception {
            this.mc = mc;
            aim = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
            camera = mc.options.getCameraType();
            renderDistance = mc.options.renderDistance().get();
            ItemStack item = mc.player.getMainHandItem();
            zoom = IGun.getIGunOrNull(item).getAimingZoom(item);
            var loader = ScopeEyeRelief.class.getClassLoader();
            Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi", true, loader);
            iris = irisApi.getMethod("getInstance").invoke(null);
            packActive = irisApi.getMethod("isShaderPackInUse");
            pipReady = Class.forName("com.ori.taczplus.client.pip.ScopePip", true, loader).getMethod("shaderLensReady");
            if (!(boolean) packActive.invoke(iris)) throw new IllegalStateException("Shader pack is not running");
            var field = ScopeEyeRelief.class.getDeclaredField("shader");
            field.setAccessible(true);
            ShaderInstance shader = (ShaderInstance) field.get(null);
            if (shader == null) throw new IllegalStateException("Scope program was not registered");
            program = shader.getId();
            eye = GL20.glGetUniformLocation(program, "EyeMotion");
            lens = GL20.glGetUniformLocation(program, "LensBounds");
            if (eye < 0 || lens < 0) throw new IllegalStateException("Scope uniforms missing in linked program");
        }

        void begin() {
            mc.setScreen(null);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.renderDistance().set(Math.min(renderDistance, 6));
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TickEvent.ClientTickEvent.class, tick);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TickEvent.RenderTickEvent.class, render);
        }

        void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || result.isDone()) return;
            try {
                ticks++;
                if (ticks == 8) {
                    attempts++;
                    aimedAt = System.nanoTime();
                    AimKey.AIM_KEY.setDown(true);
                    aim.aim(true);
                }
                if (ticks == 25) {
                    if ((closedFrames == 0 || openingFrames == 0 || stillFrames == 0) && attempts < 5) {
                        // A slow frame can span the whole opening transition. Repeat the normal ADS input.
                        AimKey.AIM_KEY.setDown(false);
                        aim.aim(false);
                        ticks = -10;
                        closedFrames = openingFrames = stillFrames = activePackFrames = readyLensFrames = 0;
                        firstClosedMs = firstOpeningMs = firstSettledMs = -1;
                        frames.clear();
                        return;
                    }
                    walkStart = mc.player.position();
                    mc.options.keyUp.setDown(true);
                }
                if (ticks == 60) {
                    walkEnd = mc.player.position();
                    mc.options.keyUp.setDown(false);
                }
                if (ticks >= 75) finish(null);
            } catch (Exception exception) {
                finish(exception);
            }
        }

        void render(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.END || result.isDone() || ticks < 8) return;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                boolean running = (boolean) packActive.invoke(iris);
                boolean pip = (boolean) pipReady.invoke(null);
                if (running) activePackFrames++;
                if (running && pip) readyLensFrames++;
                float currentAim = aim.getClientAimingProgress(mc.getFrameTime());
                FloatBuffer values = stack.mallocFloat(4);
                GL20.glGetUniformfv(program, eye, values);
                float appliedAim = values.get(0);
                float stride = values.get(1);
                float phaseX = values.get(2);
                float phaseY = values.get(3);
                GL20.glGetUniformfv(program, lens, values);
                float radius = Math.min(values.get(2), values.get(3));
                long elapsed = (System.nanoTime() - aimedAt) / 1_000_000;
                boolean uploadedThisPhase = Math.abs(appliedAim - currentAim) < 0.075f && radius >= 1f;
                if (running && uploadedThisPhase && ticks < 25 && appliedAim <= .60f) {
                    closedFrames++;
                    if (firstClosedMs < 0) firstClosedMs = elapsed;
                }
                if (running && uploadedThisPhase && ticks < 25 && appliedAim > .60f && appliedAim < .985f) {
                    openingFrames++;
                    if (firstOpeningMs < 0) firstOpeningMs = elapsed;
                }
                if (running && pip && currentAim >= .985f && ticks < 25) {
                    stillFrames++;
                    if (firstSettledMs < 0) firstSettledMs = elapsed;
                }
                double shift = 0.38 * phaseX * stride;
                double stepSize = Math.max(0.8, Math.min(5, radius * 0.019)) * stride;
                double blur = stepSize * Math.hypot(0.6 + 0.4 * Math.abs(phaseX), 0.45 * phaseY);
                if (running && pip && ticks > 27 && ticks < 60 && uploadedThisPhase && stride > 0.02f) {
                    movingFrames++;
                    minHorizontal = Math.min(minHorizontal, shift);
                    maxHorizontal = Math.max(maxHorizontal, shift);
                    maxBlur = Math.max(maxBlur, blur);
                }
                if (running && pip && currentAim >= .985f && ticks >= 68
                        && mc.player.walkAnimation.speed(mc.getFrameTime()) <= .04f) stoppedFrames++;
                frames.add(Map.of("tick", ticks, "ms", elapsed, "aim", currentAim, "gpuAim", appliedAim,
                        "stride", stride, "pip", pip, "shaderPack", running, "lensRadius", radius,
                        "horizontalShiftRadii", shift, "blurSamplePx", blur));
            } catch (Exception exception) {
                finish(exception);
            }
        }

        void finish(Exception exception) {
            if (result.isDone()) return;
            AimKey.AIM_KEY.setDown(false);
            mc.options.keyUp.setDown(false);
            aim.aim(false);
            mc.options.setCameraType(camera);
            mc.options.renderDistance().set(renderDistance);
            MinecraftForge.EVENT_BUS.unregister(tick);
            MinecraftForge.EVENT_BUS.unregister(render);
            if (exception != null) {
                result.completeExceptionally(exception);
                return;
            }
            double distance = walkStart != null && walkEnd != null ? walkStart.distanceTo(walkEnd) : 0d;
            Map<String, Object> measurements = new LinkedHashMap<>();
            measurements.put("gun", GUN.toString());
            measurements.put("scope", SCOPE.toString());
            measurements.put("magnification", zoom);
            measurements.put("aimAttempts", attempts);
            measurements.put("renderDistance", Math.min(renderDistance, 6));
            measurements.put("shaderPackFrames", activePackFrames);
            measurements.put("expertShaderLensFrames", readyLensFrames);
            measurements.put("closedFrames", closedFrames);
            measurements.put("openingFrames", openingFrames);
            measurements.put("stationaryOpenFrames", stillFrames);
            measurements.put("walkFrames", movingFrames);
            measurements.put("stoppedFrames", stoppedFrames);
            measurements.put("firstClosedMs", firstClosedMs);
            measurements.put("firstOpeningMs", firstOpeningMs);
            measurements.put("firstSettledMs", firstSettledMs);
            measurements.put("walkDistance", distance);
            measurements.put("horizontalShiftRange", movingFrames == 0 ? List.of() : List.of(minHorizontal, maxHorizontal));
            measurements.put("maxBlurTapPixels", maxBlur);
            measurements.put("pass", closedFrames > 0 && openingFrames > 0 && stillFrames > 0 && stoppedFrames > 0
                    && movingFrames > 1 && distance > .3 && maxHorizontal - minHorizontal > .04 && maxBlur > .15);
            measurements.put("frames", frames);
            result.complete(measurements);
        }
    }
}
