package com.glisco.isometricrenders.widget;

import com.glisco.isometricrenders.property.IntProperty;
import io.wispforest.owo.ui.core.Sizing;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class PropertyTextFieldComponent extends EditBox {

    private final IntProperty setting;
    private String content = "";

    public PropertyTextFieldComponent(Sizing horizontalSizing, IntProperty setting) {
        super(Minecraft.getInstance().font, 0, 0, 35, 20, Component.empty());
        this.setting = setting;

        this.horizontalSizing(horizontalSizing);

        this.setValue(String.valueOf(setting.get()));
        this.setFilter(makeMatcher());

        this.setting.listen((integerSetting, integer) -> {
            this.setValue(String.valueOf(integer));
        });

        this.setResponder(s -> {
            if (Objects.equals(s, content) || s.length() < 1 || s.equals("-")) {
                return;
            }

            this.content = s;
            this.setting.set(Integer.parseInt(s));
        });
    }

    private Predicate<String> makeMatcher() {
        final var builder = new StringBuilder();
        if (this.setting.min() < 0) builder.append("-?");

        builder.append("\\d{0,");
        builder.append(String.valueOf(Math.max(Math.abs(this.setting.min()), Math.abs(this.setting.max()))).length());
        builder.append("}");

        final var regex = builder.toString();
        return s -> s.matches(regex);
    }
}
