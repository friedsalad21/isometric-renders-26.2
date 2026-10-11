package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.screen.IsometricUI;
import com.glisco.isometricrenders.util.ExportPathSpec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fStack;

public class TooltipRenderable extends DefaultRenderable<TooltipRenderable.TooltipPropertyBundle> {

    private final ItemStack stack;

    public TooltipRenderable(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public void emitVertices(PoseStack matrices, SubmitNodeCollector collector, float tickDelta) {
        final var client = Minecraft.getInstance();
        final var font = client.font;

        // 26.x tooltips only exist as GUI render state, so we rebuild
        // the classic tooltip look from submittable text and quads
        // ponytail: text lines only, tooltip images (bundles etc.) are not drawn
        final var lines = Screen.getTooltipFromItem(client, this.stack).stream().map(Component::getVisualOrderText).toList();
        if (lines.isEmpty()) return;

        final int width = lines.stream().mapToInt(font::width).max().orElse(0);
        final int height = lines.size() * 10 + (lines.size() > 1 ? 2 : 0) - 2;

        final int x = -width / 2, y = -height / 2;

        collector.submitCustomGeometry(matrices, RenderTypes.textBackground(), (pose, buffer) -> {
            final int background = 0xF0100010, borderStart = 0x505000FF, borderEnd = 0x5028007F;

            quad(pose, buffer, x - 3, y - 4, x + width + 3, y - 3, background, background);
            quad(pose, buffer, x - 3, y + height + 3, x + width + 3, y + height + 4, background, background);
            quad(pose, buffer, x - 3, y - 3, x + width + 3, y + height + 3, background, background);
            quad(pose, buffer, x - 4, y - 3, x - 3, y + height + 3, background, background);
            quad(pose, buffer, x + width + 3, y - 3, x + width + 4, y + height + 3, background, background);

            quad(pose, buffer, x - 3, y - 2, x - 2, y + height + 2, borderStart, borderEnd);
            quad(pose, buffer, x + width + 2, y - 2, x + width + 3, y + height + 2, borderStart, borderEnd);
            quad(pose, buffer, x - 3, y - 3, x + width + 3, y - 2, borderStart, borderStart);
            quad(pose, buffer, x - 3, y + height + 2, x + width + 3, y + height + 3, borderEnd, borderEnd);
        });

        int lineY = y;
        for (int i = 0; i < lines.size(); i++) {
            collector.submitText(matrices, x, lineY, lines.get(i), true, Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, -1, 0, 0);
            lineY += i == 0 ? 12 : 10;
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int x0, int y0, int x1, int y1, int topColor, int bottomColor) {
        buffer.addVertex(pose, x0, y0, 0).setColor(topColor).setLight(LightCoordsUtil.FULL_BRIGHT);
        buffer.addVertex(pose, x0, y1, 0).setColor(bottomColor).setLight(LightCoordsUtil.FULL_BRIGHT);
        buffer.addVertex(pose, x1, y1, 0).setColor(bottomColor).setLight(LightCoordsUtil.FULL_BRIGHT);
        buffer.addVertex(pose, x1, y0, 0).setColor(topColor).setLight(LightCoordsUtil.FULL_BRIGHT);
    }

    @Override
    public TooltipPropertyBundle properties() {
        return TooltipPropertyBundle.INSTANCE;
    }

    @Override
    public ExportPathSpec exportPath() {
        return ExportPathSpec.of("tooltip", BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }

    public static class TooltipPropertyBundle extends DefaultPropertyBundle {
        public static final TooltipPropertyBundle INSTANCE = new TooltipPropertyBundle();

        @Override
        public void buildGuiControls(Renderable<?> renderable, FlowLayout container) {
            IsometricUI.sectionHeader(container, "transform_options", false);
            IsometricUI.intControl(container, this.scale, "scale", 10);
        }

        @Override
        public void applyToViewMatrix(Matrix4fStack modelViewStack) {
            final float scale = this.scale.get() / 10000f;
            modelViewStack.scale(scale, scale, -scale);

            modelViewStack.translate(this.xOffset.get() / 260f, this.yOffset.get() / -260f, 0);
            modelViewStack.rotate(Axis.YP.rotationDegrees(180));
            modelViewStack.rotate(Axis.ZP.rotationDegrees(180));
        }
    }
}
