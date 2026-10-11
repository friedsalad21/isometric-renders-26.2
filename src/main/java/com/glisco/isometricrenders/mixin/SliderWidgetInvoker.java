package com.glisco.isometricrenders.mixin;

import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSliderButton.class)
public interface SliderWidgetInvoker {
    @Invoker("setValue")
    void isometric$setValue(double value);
}
