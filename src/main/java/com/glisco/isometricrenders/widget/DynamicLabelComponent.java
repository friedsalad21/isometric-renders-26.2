package com.glisco.isometricrenders.widget;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class DynamicLabelComponent extends BaseComponent {

    private final Font textRenderer = Minecraft.getInstance().font;

    private final Supplier<Component> content;
    private int color = 0xFFFFFF;
    private boolean shadow = true;

    public DynamicLabelComponent(Supplier<Component> content) {
        this.content = content;
    }

    public DynamicLabelComponent color(int color) {
        this.color = color;
        return this;
    }

    public DynamicLabelComponent shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return 100;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return this.textRenderer.lineHeight;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        context.drawString(this.textRenderer, this.content.get(), this.x, this.y, this.color, this.shadow);
    }
}
