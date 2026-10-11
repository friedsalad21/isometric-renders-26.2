package com.glisco.isometricrenders.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

/**
 * Draws the render screen's preview. In 26.x the GUI is extracted before
 * anything is rendered, so this is also where queued exports run - picture-in-picture
 * renderers are the only GUI hook that executes during the render phase
 */
public class PreviewRenderer extends PictureInPictureRenderer<PreviewRenderer.State> {

    private static final Vector4f CLEAR_COLOR = new Vector4f(0, 0, 0, 0);

    private @Nullable GpuTexture color, depth;
    private @Nullable GpuTextureView colorView, depthView;

    @Override
    public void prepare(State state, GuiRenderState guiRenderState, FeatureRenderDispatcher featureRenderDispatcher, int guiScale) {
        final int width = (state.x1 - state.x0) * guiScale;
        final int height = (state.y1 - state.y0) * guiScale;
        if (width <= 0 || height <= 0) return;

        this.ensureTextures(width, height);
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(this.color, CLEAR_COLOR, this.depth, 0);

        if (state.renderable != null) {
            RenderableDispatcher.drawIntoTarget(state.renderable, this.colorView, this.depthView, width / (float) height, state.tickDelta,
                    matrixStack -> matrixStack.translate(state.xOffset, 0, 0));
        }

        state.renderPhaseTasks.run();

        guiRenderState.addBlitToCurrentLayer(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(this.colorView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                state.pose(),
                state.x0, state.y0, state.x1, state.y1,
                0, 1, 1, 0,
                -1,
                null,
                null
        ));
    }

    private void ensureTextures(int width, int height) {
        if (this.color != null && this.color.getWidth(0) == width && this.color.getHeight(0) == height) return;
        this.close();

        final var device = RenderSystem.getDevice();
        this.color = device.createTexture(() -> "isometric-renders preview", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        this.depth = device.createTexture(() -> "isometric-renders preview depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.D32_FLOAT, width, height, 1, 1);
        this.colorView = device.createTextureView(this.color);
        this.depthView = device.createTextureView(this.depth);
    }

    @Override
    public void close() {
        if (this.colorView != null) this.colorView.close();
        if (this.depthView != null) this.depthView.close();
        if (this.color != null) this.color.close();
        if (this.depth != null) this.depth.close();
        this.color = this.depth = null;
        this.colorView = this.depthView = null;
        super.close();
    }

    @Override
    protected void renderToTexture(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {}

    @Override
    protected String getTextureLabel() {
        return "isometric-renders preview";
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    /**
     * @param renderable       The renderable to preview, or {@code null} to only run the tasks
     * @param xOffset          Horizontal offset in clip space, used when only one control column is shown
     * @param renderPhaseTasks Work that must run during the render phase, like exports
     */
    public record State(int x0, int y0, int x1, int y1, @Nullable Renderable<?> renderable, float tickDelta, float xOffset, Runnable renderPhaseTasks) implements PictureInPictureRenderState {
        @Override
        public float scale() {
            return 1;
        }

        @Override
        public @Nullable ScreenRectangle scissorArea() {
            return null;
        }

        @Override
        public @Nullable ScreenRectangle bounds() {
            return new ScreenRectangle(this.x0, this.y0, this.x1 - this.x0, this.y1 - this.y0);
        }
    }
}
