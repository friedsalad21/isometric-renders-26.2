package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.mixin.access.ItemRenderStateAccessor;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.util.ExportPathSpec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fStack;

public class ItemRenderable extends DefaultRenderable<DefaultPropertyBundle> {

    private static final ItemStackRenderState RENDER_STATE = new ItemStackRenderState();
    private static final DefaultPropertyBundle PROPERTIES = new DefaultPropertyBundle() {
        @Override
        public void applyToViewMatrix(Matrix4fStack modelViewStack) {
            final float scale = (this.scale.get() / 100f) * (RENDER_STATE.isGui3d() ? 2f : 1.75f);
            modelViewStack.scale(scale, scale, scale);

            modelViewStack.translate(this.xOffset.get() / 26000f, this.yOffset.get() / -26000f, 0);

            modelViewStack.rotate(Axis.XP.rotationDegrees(this.slant.get()));
            var bruhMatrices = new PoseStack();
            RENDER_STATE.transform().apply(false, bruhMatrices);
            modelViewStack.mul(bruhMatrices.last().pose());
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
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(
            RENDER_STATE,
            this.stack,
            ItemDisplayContext.GUI,
            false,
            Minecraft.getInstance().level,
            null,
            0
        );
    }

    @Override
    public void emitVertices(PoseStack matrices, MultiBufferSource vertexConsumers, float tickDelta) {
        ((ItemRenderStateAccessor) RENDER_STATE).isometric$setTransformationMode(ItemDisplayContext.NONE);
        RENDER_STATE.render(matrices, vertexConsumers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
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

//    private static class TransformlessBakedModel extends ForwardingBakedModel {
//        public TransformlessBakedModel(BakedModel inner) {
//            this.wrapped = inner;
//        }
//
//        @Override
//        public ModelTransformation getTransformation() {
//            return ModelTransformation.NONE;
//        }
//    }
}
