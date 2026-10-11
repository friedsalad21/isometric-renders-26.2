package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.mixin.access.CameraInvoker;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

public abstract class DefaultRenderable<P extends DefaultPropertyBundle> implements Renderable<P> {

    @Override
    public void setupLighting(Matrix4f modelViewMatrix) {
        // Apply inverse transform to lighting to keep it consistent
        final var lightDirection = getLightDirection();
        final var lightTransform = new Matrix4f(modelViewMatrix);
        lightTransform.invert();
        lightDirection.mul(lightTransform);

        final var transformedLightDirection = new Vector3f(lightDirection.x, lightDirection.y, lightDirection.z);
        RenderSystem.setShaderLights(transformedLightDirection, transformedLightDirection);
    }

    @Override
    public void draw(Matrix4f modelViewMatrix) {
        // Draw all buffers
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    protected void renderParticles(Matrix4f transform, float tickDelta) {
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(transform);

        var client = Minecraft.getInstance();
        this.withParticleCamera(camera -> {
            client.particleEngine.render(
                camera,
                tickDelta,
                Minecraft.getInstance().renderBuffers().bufferSource()
            );
        });

        modelView.popMatrix();
    }

    protected void withParticleCamera(Consumer<Camera> action) {
        Camera camera = Minecraft.getInstance().getEntityRenderDispatcher().camera;
        float previousYaw = camera.getYRot(), previousPitch = camera.getXRot();

        ((CameraInvoker) camera).isometric$setRotation(this.properties().rotation.get() + 180 + this.properties().rotationOffset(), this.properties().slant.get());
        action.accept(camera);

        ((CameraInvoker) camera).isometric$setRotation(previousYaw, previousPitch);
    }

    protected Vector4f getLightDirection() {
        return new Vector4f(this.properties().lightAngle.get() / 90f, .35f, 1, 0);
    }
}
