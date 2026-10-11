package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.property.IntProperty;
import com.glisco.isometricrenders.screen.IsometricUI;
import com.glisco.isometricrenders.util.ExportPathSpec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wispforest.owo.ui.container.FlowLayout;
import org.joml.Matrix4fStack;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ItemAtlasRenderable extends DefaultRenderable<ItemAtlasRenderable.ItemAtlasPropertyBundle> {

    private final Minecraft client = Minecraft.getInstance();
    private final List<ItemStack> items;
    private final String atlasSource;

    public ItemAtlasRenderable(String atlasSource, List<ItemStack> items) {
        this.atlasSource = atlasSource;
        this.items = items;
    }

    @Override
    public void emitVertices(PoseStack matrices, SubmitNodeCollector collector, float tickDelta) {
        final int columns = this.properties().columns.get();
        final int rows = Mth.positiveCeilDiv(this.items.size(), columns);

        final float spacing = 1.25f;

        matrices.scale(.1f, .1f, .1f);
        matrices.translate((-columns / 2f) * spacing - spacing / 2, (rows / 2f) * spacing + spacing / 2, 0);

        for (int row = 0; row < rows; row++) {
            matrices.translate(0, -spacing, 0);
            matrices.pushPose();
            for (int column = 0; column < columns; column++) {
                matrices.translate(spacing, 0, 0);
                final var index = row * columns + column;
                if (index >= this.items.size()) continue;

                final var state = new ItemStackRenderState();
                this.client.getItemModelResolver().updateForTopItem(state, this.items.get(index), ItemDisplayContext.GUI, this.client.level, null, 0);
                state.submit(matrices, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            }
            matrices.popPose();
        }
    }

    @Override
    public ItemAtlasPropertyBundle properties() {
        return ItemAtlasPropertyBundle.INSTANCE;
    }

    @Override
    public ExportPathSpec exportPath() {
        return ExportPathSpec.of("atlases", this.atlasSource);
    }

    public static class ItemAtlasPropertyBundle extends DefaultPropertyBundle {

        private static final ItemAtlasPropertyBundle INSTANCE = new ItemAtlasPropertyBundle();

        private final IntProperty columns = IntProperty.of(20, 1, 500);

        @Override
        public void buildGuiControls(Renderable<?> renderable, FlowLayout container) {
            IsometricUI.sectionHeader(container, "transform_options", false);

            IsometricUI.intControl(container, this.scale, "scale", 10);
            IsometricUI.intControl(container, this.columns, "columns", 1);
        }

        @Override
        public void applyToViewMatrix(Matrix4fStack modelViewStack) {
            super.applyToViewMatrix(modelViewStack);
            modelViewStack.rotate(Axis.YP.rotationDegrees(-this.rotation.get()));
            modelViewStack.rotate(Axis.XP.rotationDegrees(-this.slant.get()));
        }

    }
}
