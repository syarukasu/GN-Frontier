package local.gnfrontier.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import java.io.IOException;
import local.gnfrontier.Frontier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

@Mod.EventBusSubscriber(modid = Frontier.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ScopeEyeRelief {
    public enum Mask { STENCIL, DEPTH, CIRCLE }

    private static ShaderInstance shader;
    private static int copyTexture;
    private static int copyWidth;
    private static int copyHeight;

    private ScopeEyeRelief() {}

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                Frontier.id("scope_eye_relief"), DefaultVertexFormat.POSITION), loaded -> {
            shader = loaded;
            if (copyTexture != 0) {
                TextureUtil.releaseTextureId(copyTexture);
                copyTexture = 0;
            }
        });
    }

    public static void render(float centerX, float centerY, float radiusX, float radiusY,
                              int width, int height, Mask mask) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (shader == null || player == null || !minecraft.options.getCameraType().isFirstPerson()) return;

        ItemStack held = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(held);
        if (gun == null || gun.getAimingZoom(held) <= 1.05f) return;

        float partialTick = minecraft.getFrameTime();
        float aim = IClientPlayerGunOperator.fromLocalPlayer(player).getClientAimingProgress(partialTick);
        float stride = Mth.clamp((player.walkAnimation.speed(partialTick) - 0.04f) / 0.44f, 0f, 1f);
        if (aim >= 0.985f && stride == 0f) return;
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(radiusX)
                || !Float.isFinite(radiusY) || radiusX < 1f || radiusY < 1f) return;

        int left = Math.max(0, Mth.floor(centerX - radiusX));
        int bottom = Math.max(0, Mth.floor(centerY - radiusY));
        int right = Math.min(width, Mth.ceil(centerX + radiusX));
        int top = Math.min(height, Mth.ceil(centerY + radiusY));
        if (right <= left || top <= bottom) return;

        float phase = player.walkAnimation.position(partialTick) * 0.7f;
        shader.safeGetUniform("LensBounds").set(centerX, centerY, radiusX, radiusY);
        shader.safeGetUniform("EyeMotion").set(aim, stride, Mth.sin(phase), Mth.cos(phase * 2f));
        shader.safeGetUniform("CircleMask").set(mask == Mask.CIRCLE ? 1 : 0);

        // Copy only the lens rectangle, from the framebuffer the scope has just composited into.
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int shaderTexture = RenderSystem.getShaderTexture(0);
        ShaderInstance previousShader = RenderSystem.getShader();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int depthFunction = mask == Mask.DEPTH ? GL11.glGetInteger(GL11.GL_DEPTH_FUNC) : GL11.GL_LEQUAL;
        try {
            if (aim > 0.60f) {
                copyRegion(left, bottom, right - left, top - bottom, readFramebuffer, drawFramebuffer);
            }
            shader.safeGetUniform("CopyRegion").set((float) left, (float) bottom,
                    (float) (right - left), (float) (top - bottom));
            RenderSystem.setShaderTexture(0, copyTexture);
            RenderSystem.setShader(() -> shader);
            RenderSystem.depthMask(false);
            if (mask == Mask.DEPTH) {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            } else {
                RenderSystem.disableDepthTest();
            }
            drawRectangle(left, bottom, right, top, width, height);
        } finally {
            RenderSystem.depthMask(depthWrite);
            if (mask == Mask.DEPTH) RenderSystem.depthFunc(depthFunction);
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.setShaderTexture(0, shaderTexture);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(boundTexture);
            RenderSystem.activeTexture(activeTexture);
        }
    }

    private static void copyRegion(int x, int y, int width, int height, int readFramebuffer, int drawFramebuffer) {
        if (copyTexture == 0) {
            copyTexture = TextureUtil.generateTextureId();
            copyWidth = copyHeight = 0;
            GlStateManager._bindTexture(copyTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } else {
            GlStateManager._bindTexture(copyTexture);
        }
        if (width > copyWidth || height > copyHeight) {
            // Round up so the ADS animation does not reallocate the texture at each new lens size.
            copyWidth = Math.max(copyWidth, (width + 63) / 64 * 64);
            copyHeight = Math.max(copyHeight, (height + 63) / 64 * 64);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, copyWidth, copyHeight, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        }
        if (readFramebuffer != drawFramebuffer) GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, drawFramebuffer);
        try {
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, width, height);
        } finally {
            if (readFramebuffer != drawFramebuffer) GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        }
    }

    private static void drawRectangle(int left, int bottom, int right, int top, int width, int height) {
        float x0 = 2f * left / width - 1f;
        float y0 = 2f * bottom / height - 1f;
        float x1 = 2f * right / width - 1f;
        float y1 = 2f * top / height - 1f;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.vertex(x0, y0, 0f).endVertex();
        buffer.vertex(x1, y0, 0f).endVertex();
        buffer.vertex(x1, y1, 0f).endVertex();
        buffer.vertex(x0, y1, 0f).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }
}
