package com.glisco.isometricrenders.compat;

import com.glisco.isometricrenders.IsometricRenders;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Shapes, ghost blocks and text from gizmolib (a Scarpet extension). It isn't published
 * anywhere to compile against, so its client stores are called by reflection, and any
 * mismatch just turns this off
 */
public final class GizmoLibShapes {

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("gizmolib");

    private static @Nullable Handles handles = null;
    private static boolean failed = false;

    private GizmoLibShapes() {}

    /**
     * Emits gizmolib's shapes as gizmos into the active gizmo collector
     */
    public static void emitShapes(ClientLevel level, Vec3 origin, float partialTick) {
        final var handles = handles();
        if (handles == null) return;

        try {
            handles.emitShapes.invokeExact(level, origin, partialTick);
        } catch (Throwable t) {
            disable(t);
        }
    }

    /**
     * Submits gizmolib's ghost blocks, solid fills and text, positioned relative to {@code origin}.
     * Solid fills are only queued by {@link #emitShapes}, so call that first
     */
    public static void submit(SubmitNodeCollector collector, CameraRenderState camera, ClientLevel level, Vec3 origin, float partialTick) {
        final var handles = handles();
        if (handles == null) return;

        try {
            handles.submitGhostBlocks.invokeExact(new PoseStack(), collector, origin);
            handles.submitSolidFills.invokeExact(new PoseStack(), collector, origin);
            handles.submitText.invokeExact(new PoseStack(), collector, camera, level, origin, partialTick);
        } catch (Throwable t) {
            disable(t);
        }
    }

    private static @Nullable Handles handles() {
        if (!LOADED || failed) return null;
        if (handles != null) return handles;

        try {
            final var lookup = MethodHandles.publicLookup();
            final var submitType = MethodType.methodType(void.class, PoseStack.class, SubmitNodeCollector.class, Vec3.class);
            handles = new Handles(
                    lookup.findStatic(Class.forName("com.gizmolib.client.ShapeStore"), "emit", MethodType.methodType(void.class, ClientLevel.class, Vec3.class, float.class)),
                    lookup.findStatic(Class.forName("com.gizmolib.client.GhostBlocks"), "submit", submitType),
                    lookup.findStatic(Class.forName("com.gizmolib.client.SolidFills"), "submit", submitType),
                    lookup.findStatic(Class.forName("com.gizmolib.client.TextDisplays"), "submit", MethodType.methodType(void.class, PoseStack.class, SubmitNodeCollector.class, CameraRenderState.class, ClientLevel.class, Vec3.class, float.class))
            );
            return handles;
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }

    private static void disable(Throwable t) {
        failed = true;
        IsometricRenders.LOGGER.warn("gizmolib shapes won't show up in renders, its API changed", t);
    }

    private record Handles(MethodHandle emitShapes, MethodHandle submitGhostBlocks, MethodHandle submitSolidFills, MethodHandle submitText) {}
}
