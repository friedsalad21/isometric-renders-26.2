package com.glisco.isometricrenders.widget;

import net.minecraft.client.input.InputWithModifiers;

import com.glisco.isometricrenders.property.Property;
import io.wispforest.owo.ui.component.CheckboxComponent;
import net.minecraft.network.chat.Component;

public class PropertyCheckboxComponent extends CheckboxComponent {

    private final Property<Boolean> property;

    public PropertyCheckboxComponent(Component message, Property<Boolean> property) {
        super(message);

        this.property = property;
        this.checked(this.property.get());
    }

    @Override
    public void onPress(InputWithModifiers input) {
        super.onPress(input);
        property.set(this.selected());
    }
}
