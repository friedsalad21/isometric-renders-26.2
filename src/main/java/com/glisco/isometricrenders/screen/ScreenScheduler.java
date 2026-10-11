package com.glisco.isometricrenders.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class ScreenScheduler {

    private static Screen SCHEDULED_SCREEN = null;

    public static void schedule(Screen screen) {
        if (Minecraft.getInstance().screen == null) {
            Minecraft.getInstance().setScreen(screen);
        } else {
            SCHEDULED_SCREEN = screen;
        }
    }

    public static boolean hasScheduled() {
        return SCHEDULED_SCREEN != null;
    }

    public static void open() {
        Minecraft.getInstance().setScreen(SCHEDULED_SCREEN);
        SCHEDULED_SCREEN = null;
    }

}
