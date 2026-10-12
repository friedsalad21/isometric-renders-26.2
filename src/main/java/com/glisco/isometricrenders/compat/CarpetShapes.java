package com.glisco.isometricrenders.compat;

import carpet.network.CarpetClient;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;

/**
 * Scarpet {@code draw_shape} shapes, only touched when Carpet is installed
 */
public final class CarpetShapes {

    private CarpetShapes() {}

    public static void submit(SubmitNodeStorage storage, LevelRenderState levelState, float tickDelta) {
        if (CarpetClient.shapes == null) return;
        CarpetClient.shapes.render(storage, levelState, new Matrix4f(), tickDelta);
    }
}
