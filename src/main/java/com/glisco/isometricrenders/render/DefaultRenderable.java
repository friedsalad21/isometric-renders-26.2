package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.mixin.access.CameraInvoker;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public abstract class DefaultRenderable<P extends DefaultPropertyBundle> implements Renderable<P> {

    private static GpuBuffer lightBuffer;

    private @Nullable SubmitNodeStorage pendingParticles;
    private final Matrix4f particleTransform = new Matrix4f();

    private @Nullable SubmitNodeStorage pendingDebugOverlays;
    private final Matrix4f debugOverlayTransform = new Matrix4f();

    @Override
    public void setupLighting(Matrix4f modelViewMatrix) {
        // Apply inverse transform to lighting to keep it consistent
        final var lightDirection = getLightDirection();
        final var lightTransform = new Matrix4f(modelViewMatrix);
        lightTransform.invert();
        lightDirection.mul(lightTransform);

        final var transformedLightDirection = new Vector3f(lightDirection.x, lightDirection.y, lightDirection.z);

        if (lightBuffer == null) {
            lightBuffer = RenderSystem.getDevice().createBuffer(() -> "isometric-renders lighting", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, Lighting.UBO_SIZE);
        }

        try (var stack = MemoryStack.stackPush()) {
            final var data = Std140Builder.onStack(stack, Lighting.UBO_SIZE).putVec3(transformedLightDirection).putVec3(transformedLightDirection).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(lightBuffer.slice(), data);
        }

        RenderSystem.setShaderLights(lightBuffer.slice());
    }

    @Override
    public void draw(Matrix4f modelViewMatrix) {
        // Particles and debug overlays are positioned relative to the
        // camera, so they need their own model view matrix
        if (this.pendingParticles != null) {
            drawWithTransform(this.pendingParticles, this.particleTransform);
            this.pendingParticles = null;
        }

        if (this.pendingDebugOverlays != null) {
            drawWithTransform(this.pendingDebugOverlays, this.debugOverlayTransform);
            this.pendingDebugOverlays = null;
        }
    }

    private static void drawWithTransform(SubmitNodeStorage submits, Matrix4f transform) {
        final var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(transform);

        Minecraft.getInstance().gameRenderer.featureRenderDispatcher().renderAllFeatures(submits);

        modelView.popMatrix();
    }

    /**
     * Captures the world's debug overlays inside {@code area}, drawn with {@code origin} at the current pose
     */
    protected void renderDebugOverlays(PoseStack matrices, Vec3 origin, AABB area, boolean clip, float tickDelta) {
        this.pendingDebugOverlays = DebugOverlays.collect(origin, area, this.cameraYaw(), this.properties().slant.get(), clip, tickDelta);
        this.debugOverlayTransform.set(matrices.last().pose());
    }

    private float cameraYaw() {
        return this.properties().rotation.get() + 180 + this.properties().rotationOffset();
    }

    protected void renderParticles(PoseStack matrices, float tickDelta) {
        final var client = Minecraft.getInstance();
        final var camera = client.gameRenderer.mainCamera();
        final float previousYaw = camera.yRot(), previousPitch = camera.xRot();

        ((CameraInvoker) camera).isometric$setRotation(this.cameraYaw(), this.properties().slant.get());

        final var cameraState = new CameraRenderState();
        camera.extractRenderState(cameraState, tickDelta);

        // ponytail: cull-everything-far-away frustum, particles are restricted to the render's surroundings anyway
        final var frustum = new Frustum(new Matrix4f(), new Matrix4f().setOrtho(-512, 512, -512, 512, -512, 512));
        frustum.prepare(camera.position().x, camera.position().y, camera.position().z);

        final var particles = new ParticlesRenderState();
        client.particleEngine.extract(particles, frustum, camera, tickDelta);

        this.pendingParticles = new SubmitNodeStorage();
        particles.submit(this.pendingParticles, cameraState);
        this.particleTransform.set(matrices.last().pose());

        ((CameraInvoker) camera).isometric$setRotation(previousYaw, previousPitch);
    }

    protected Vector4f getLightDirection() {
        return new Vector4f(this.properties().lightAngle.get() / 90f, .35f, 1, 0);
    }
}
