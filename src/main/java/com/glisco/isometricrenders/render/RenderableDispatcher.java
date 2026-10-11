package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.IsometricRenders;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RenderableDispatcher {

    private static final Vector4f CLEAR_COLOR = new Vector4f(0, 0, 0, 0);
    // One buffer per nesting level, batch renders draw into images while the preview is drawing
    private static final List<ProjectionMatrixBuffer> PROJECTION_BUFFERS = new ArrayList<>();
    private static int nesting = 0;

    /**
     * Renders the given renderable into the given color and depth textures,
     * with the projection matrix adjusted to compensate for the target's
     * aspect ratio. Must be called during the render phase
     *
     * @param renderable  The renderable to draw
     * @param aspectRatio The aspect ratio of the target
     * @param tickDelta   The tick delta to use
     */
    public static void drawIntoTarget(Renderable<?> renderable, GpuTextureView color, GpuTextureView depth, float aspectRatio, float tickDelta, Consumer<Matrix4fStack> transformer) {
        if (PROJECTION_BUFFERS.size() <= nesting) PROJECTION_BUFFERS.add(new ProjectionMatrixBuffer("isometric-renders " + nesting));
        final var projectionBuffer = PROJECTION_BUFFERS.get(nesting++);

        final var previousColor = RenderSystem.outputColorTextureOverride;
        final var previousDepth = RenderSystem.outputDepthTextureOverride;
        final var previousLights = RenderSystem.getShaderLights();
        RenderSystem.outputColorTextureOverride = color;
        RenderSystem.outputDepthTextureOverride = depth;

        renderable.prepare();

        // Prepare model view matrix
        final var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();

        transformer.accept(modelViewStack);
        renderable.properties().applyToViewMatrix(modelViewStack);

        // 26.x uses reverse-z, hence near and far are swapped
        RenderSystem.backupProjectionMatrix();
        final var zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        final var projectionMatrix = new Matrix4f().setOrtho(-aspectRatio, aspectRatio, -1, 1, 3000, -1000, zZeroToOne);
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projectionMatrix), ProjectionType.ORTHOGRAPHIC);

        IsometricRenders.beginRenderableDraw();

        renderable.setupLighting(modelViewStack);

        // Submit untransformed geometry, then draw it
        final var submits = new SubmitNodeStorage();
        renderable.emitVertices(new PoseStack(), submits, tickDelta);
        Minecraft.getInstance().gameRenderer.featureRenderDispatcher().renderAllFeatures(submits);

        renderable.draw(modelViewStack);

        IsometricRenders.endRenderableDraw();

        modelViewStack.popMatrix();
        renderable.cleanUp();
        RenderSystem.restoreProjectionMatrix();

        RenderSystem.outputColorTextureOverride = previousColor;
        RenderSystem.outputDepthTextureOverride = previousDepth;
        if (previousLights != null) RenderSystem.setShaderLights(previousLights);
        nesting--;
    }

    /**
     * Draws the given renderable into a new texture and asynchronously
     * copies the result into a {@link NativeImage}. Must be called during the render phase
     *
     * @param renderable The renderable to draw
     * @param size       The resolution to render at
     * @return A future completing with the created image
     */
    public static CompletableFuture<NativeImage> drawIntoImage(Renderable<?> renderable, float tickDelta, int size) {
        final var device = RenderSystem.getDevice();

        final var color = device.createTexture(() -> "isometric-renders color", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, size, size, 1, 1);
        final var depth = device.createTexture(() -> "isometric-renders depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.D32_FLOAT, size, size, 1, 1);
        final var colorView = device.createTextureView(color);
        final var depthView = device.createTextureView(depth);

        device.createCommandEncoder().clearColorAndDepthTextures(color, CLEAR_COLOR, depth, 0);
        drawIntoTarget(renderable, colorView, depthView, 1, tickDelta, matrixStack -> {});

        final var future = new CompletableFuture<NativeImage>();
        final int blockSize = color.getFormat().blockSize();
        final var buffer = device.createBuffer(() -> "isometric-renders readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) size * size * blockSize);

        device.createCommandEncoder().copyTextureToBuffer(color, buffer, 0, () -> {
            try (var read = buffer.map(true, false)) {
                final var image = new NativeImage(size, size, false);
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        image.setPixelABGR(x, size - y - 1, read.data().getInt((x + y * size) * blockSize));
                    }
                }
                future.complete(image);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            } finally {
                buffer.close();
                colorView.close();
                depthView.close();
                color.close();
                depth.close();
            }
        }, 0);

        return future;
    }
}
