package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.property.PropertyBundle;
import com.glisco.isometricrenders.util.ExportPathSpec;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

public class EmptyRenderable implements Renderable<PropertyBundle> {

    private static final PropertyBundle EMPTY_BUNDLE = new PropertyBundle() {
        @Override
        public void buildGuiControls(Renderable<?> renderable, FlowLayout container) {}

        @Override
        public void applyToViewMatrix(Matrix4fStack modelViewStack) {}
    };

    @Override
    public void emitVertices(PoseStack matrices, SubmitNodeCollector collector, float tickDelta) {}

    @Override
    public PropertyBundle properties() {
        return EMPTY_BUNDLE;
    }

    @Override
    public ExportPathSpec exportPath() {
        return ExportPathSpec.of("", "empty");
    }
}
