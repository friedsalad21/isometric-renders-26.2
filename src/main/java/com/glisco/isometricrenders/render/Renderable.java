package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.property.PropertyBundle;
import com.glisco.isometricrenders.util.ExportPathSpec;
import com.glisco.isometricrenders.util.ParticleRestriction;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;

public interface Renderable<P extends PropertyBundle> {

    Renderable<PropertyBundle> EMPTY = new EmptyRenderable();

    default void prepare() {}

    default void setupLighting(Matrix4f modelViewMatrix) {}

    /**
     * Submit all geometry, it is drawn with the current model view matrix right after this returns
     */
    void emitVertices(PoseStack matrices, SubmitNodeCollector collector, float tickDelta);

    /**
     * Draw anything that can't go through the submit system, called after all submitted geometry was drawn
     */
    default void draw(Matrix4f modelViewMatrix) {}

    default void cleanUp() {}

    default void dispose() {}

    default ParticleRestriction<?> particleRestriction() {
        return ParticleRestriction.never();
    }

    P properties();

    ExportPathSpec exportPath();
}
