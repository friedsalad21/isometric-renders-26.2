package com.glisco.isometricrenders.util;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

public class Translate {

    public static final Component PREFIX = generatePrefix("Isometric Renders", 190, 155);

    public static MutableComponent make(String key, Object... args) {
        return Component.translatable("message.isometric-renders." + key, args);
    }

    public static MutableComponent gui(String key, Object... args) {
        return Component.translatable("gui.isometric-renders." + key, args);
    }

    public static MutableComponent msg(String key, Object... args) {
        return prefixed(make(key, args).withStyle(ChatFormatting.GRAY));
    }

    public static void commandFeedback(CommandContext<FabricClientCommandSource> context, String key, Object... args) {
        context.getSource().sendFeedback(msg(key, args));
    }

    public static void commandError(CommandContext<FabricClientCommandSource> context, String key, Object... args) {
        context.getSource().sendError(msg(key, args));
    }

    public static MutableComponent prefixed(Component text) {
        return Component.empty()
                .append(PREFIX)
                .append(Component.literal(" > ").withStyle(ChatFormatting.DARK_GRAY))
                .append(text);
    }

    @SuppressWarnings("SameParameterValue")
    private static Component generatePrefix(String text, int startHue, int endHue) {
        int hueSpan = endHue - startHue;
        char[] chars = text.toCharArray();

        var prefixText = Component.empty();

        for (int i = 0; i < chars.length; i++) {
            float index = i;
            prefixText.append(Component.literal(String.valueOf(chars[i])).withStyle(style ->
                    style.withColor(Mth.hsvToRgb((startHue + (index / chars.length) * hueSpan) / 360, 1, 0.96f))
            ));
        }

        return prefixText;
    }

    public static void actionBar(String key, Object... args) {
        Minecraft.getInstance().player.sendOverlayMessage(make(key, args));
    }
}
