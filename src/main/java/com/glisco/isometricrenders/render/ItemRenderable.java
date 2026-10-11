package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.mixin.access.ItemRenderStateAccessor;
import com.glisco.isometricrenders.mixin.access.LayerRenderStateAccessor;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.util.ExportPathSpec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

public class ItemRenderable extends DefaultRenderable<DefaultPropertyBundle> {

    private static final ItemStackRenderState RENDER_STATE = new ItemStackRenderState();
    private static ItemTransform guiTransform = ItemTransform.NO_TRANSFORM;

    private static final DefaultPropertyBundle PROPERTIES = new DefaultPropertyBundle() {
        @Override
        public void applyToViewMatrix(Matrix4fStack modelViewStack) {
            final float scale = (this.scale.get() / 100f) * (RENDER_STATE.usesBlockLight() ? 2f : 1.75f);
            modelViewStack.scale(scale, scale, scale);

            modelViewStack.translate(this.xOffset.get() / 26000f, this.yOffset.get() / -26000f, 0);

            modelViewStack.rotate(Axis.XP.rotationDegrees(this.slant.get()));

            // The GUI transform, minus its centering - the layers still center themselves
            final var t = guiTransform;
            modelViewStack.translate(t.translation().x(), t.translation().y(), t.translation().z());
            modelViewStack.rotate(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(t.rotation().x()),
                (float) Math.toRadians(t.rotation().y()),
                (float) Math.toRadians(t.rotation().z())
            ));
            modelViewStack.scale(t.scale().x(), t.scale().y(), t.scale().z());

            modelViewStack.rotate(Axis.YP.rotationDegrees(this.rotation.get()));

            this.updateAndApplyRotationOffset(modelViewStack);
        }
    };

    static {
        PROPERTIES.slant.setDefaultValue(0).setToDefault();
        PROPERTIES.rotation.setDefaultValue(0).setToDefault();
    }

    private final ItemStack stack;

    public ItemRenderable(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public void prepare() {
        final var client = Minecraft.getInstance();
        client.getItemModelResolver().updateForTopItem(RENDER_STATE, this.stack, ItemDisplayContext.GUI, client.level, null, 0);

        // Move the GUI transform from the layers into the view matrix
        // so that rotation happens around the item's own axes
        final var accessor = (ItemRenderStateAccessor) RENDER_STATE;
        guiTransform = ItemTransform.NO_TRANSFORM;
        for (int i = 0; i < accessor.isometric$getActiveLayerCount(); i++) {
            final var layer = accessor.isometric$getLayers()[i];
            if (i == 0) guiTransform = ((LayerRenderStateAccessor) layer).isometric$getItemTransform();
            layer.setItemTransform(ItemTransform.NO_TRANSFORM);
        }
    }

    @Override
    public void emitVertices(PoseStack matrices, SubmitNodeCollector collector, float tickDelta) {
        RENDER_STATE.submit(matrices, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
    }

    @Override
    public void cleanUp() {
        RENDER_STATE.clear();
    }

    @Override
    public DefaultPropertyBundle properties() {
        return PROPERTIES;
    }

    @Override
    public ExportPathSpec exportPath() {
        return ExportPathSpec.ofIdentified(
            BuiltInRegistries.ITEM.getKey(this.stack.getItem()),
            "item"
        );
    }
}
