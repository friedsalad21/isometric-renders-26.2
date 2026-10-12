package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.compat.CarpetShapes;
import com.glisco.isometricrenders.mixin.access.CameraInvoker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures what the world renderer would draw as debug overlays (F3 hitboxes,
 * chunk borders and every other debug renderer, plus Scarpet shapes from Carpet)
 * so area renders can include them
 */
public final class DebugOverlays {

    private static final boolean CARPET_LOADED = FabricLoader.getInstance().isModLoaded("carpet");

    private static @Nullable AABB collectingArea = null;

    private DebugOverlays() {}

    /**
     * @return The area currently being collected for, while debug renderers run
     */
    public static @Nullable AABB collectingArea() {
        return collectingArea;
    }

    /**
     * Submits all debug overlays around {@code area}, positioned relative to {@code origin}
     *
     * @param yaw   The camera yaw text and billboards should face
     * @param pitch The camera pitch text and billboards should face
     * @param clip  Whether to cut off everything outside of {@code area}
     */
    public static SubmitNodeStorage collect(Vec3 origin, AABB area, float yaw, float pitch, boolean clip, float tickDelta) {
        final var client = Minecraft.getInstance();
        final var camera = client.gameRenderer.mainCamera();
        final float previousYaw = camera.yRot(), previousPitch = camera.xRot();
        final var previousPosition = camera.position();

        // Everything (Carpet included) positions overlays relative to the main camera,
        // put it at the render's origin so camera-relative means area-relative
        ((CameraInvoker) camera).isometric$setRotation(yaw, pitch);
        ((CameraInvoker) camera).isometric$setPosition(origin);

        final var storage = clip ? new ClippingStorage(area.inflate(1e-3)) : new SubmitNodeStorage();
        try {
            final var levelState = new LevelRenderState();
            camera.extractRenderState(levelState.cameraRenderState, tickDelta);

            submitDebugRenderers(client, levelState.cameraRenderState, origin, area, tickDelta, storage);
            if (CARPET_LOADED) CarpetShapes.submit(storage, levelState, tickDelta);
        } finally {
            ((CameraInvoker) camera).isometric$setPosition(previousPosition);
            ((CameraInvoker) camera).isometric$setRotation(previousYaw, previousPitch);
        }

        return storage;
    }

    private static void submitDebugRenderers(Minecraft client, CameraRenderState cameraState, Vec3 origin, AABB area, float tickDelta, SubmitNodeStorage storage) {
        if (client.level == null || client.getConnection() == null) return;

        // Same everything-nearby frustum as particles, the area restriction happens later
        final var frustum = new Frustum(new Matrix4f(), new Matrix4f().setOrtho(-512, 512, -512, 512, -512, 512));
        frustum.prepare(origin.x, origin.y, origin.z);

        final var collector = new SimpleGizmoCollector();
        collectingArea = area;
        try (var ignored = Gizmos.withCollector(collector)) {
            client.levelExtractor.debugRenderer.emitGizmos(frustum, origin.x, origin.y, origin.z, tickDelta);
            client.levelExtractor.gameTestBlockHighlightRenderer.emitGizmos();
        } finally {
            collectingArea = null;
        }

        // Gizmos sent by the game itself (debug commands, game tests, ...)
        collector.addTemporaryGizmos(client.getPerTickGizmos());
        final var server = client.getSingleplayerServer();
        if (server != null) collector.addTemporaryGizmos(server.getPerTickGizmos());

        final var standard = new DrawableGizmoPrimitives();
        final var onTop = new DrawableGizmoPrimitives();
        final long now = Util.getMillis();
        for (var instance : collector.drainGizmos()) {
            instance.gizmo().emit(instance.isAlwaysOnTop() ? onTop : standard, instance.getAlphaMultiplier(now));
        }

        standard.submit(storage, cameraState, false);
        onTop.submit(storage, cameraState, true);
    }

    /**
     * Cuts all gizmo geometry down to the given box, so that chunk borders
     * and the like don't stretch across the entire render
     */
    private static class ClippingStorage extends SubmitNodeStorage {

        private final AABB bounds;

        private ClippingStorage(AABB bounds) {
            this.bounds = bounds;
        }

        @Override
        public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
            final var lines = new ArrayList<DrawableGizmoPrimitives.Line>();
            for (var line : group.lines()) {
                final var clipped = this.clip(line.start(), line.end());
                if (clipped != null) lines.add(new DrawableGizmoPrimitives.Line(clipped[0], clipped[1], line.color(), line.width()));
            }

            final var quads = new ArrayList<DrawableGizmoPrimitives.Quad>();
            for (var quad : group.quads()) {
                if (this.contains(quad.a(), quad.b(), quad.c(), quad.d())) quads.add(quad);
            }

            final var fans = new ArrayList<DrawableGizmoPrimitives.TriangleFan>();
            for (var fan : group.triangleFans()) {
                if (this.contains(fan.points())) fans.add(fan);
            }

            final var texts = new ArrayList<DrawableGizmoPrimitives.Text>();
            for (var text : group.texts()) {
                if (this.bounds.contains(text.pos())) texts.add(text);
            }

            final var points = new ArrayList<DrawableGizmoPrimitives.Point>();
            for (var point : group.points()) {
                if (this.bounds.contains(point.pos())) points.add(point);
            }

            super.submitGizmoPrimitives(new DrawableGizmoPrimitives.Group(group.opaque(), lines, quads, fans, texts, points), camera, onTop);
        }

        /**
         * Shapes are kept or dropped as a whole, based on their center
         */
        private boolean contains(Vec3... vertices) {
            if (vertices.length == 0) return false;

            double x = 0, y = 0, z = 0;
            for (var vertex : vertices) {
                x += vertex.x;
                y += vertex.y;
                z += vertex.z;
            }

            return this.bounds.contains(x / vertices.length, y / vertices.length, z / vertices.length);
        }

        /**
         * Liang-Barsky line clipping against the bounds
         *
         * @return The clipped start and end, or {@code null} if the line is entirely outside
         */
        private Vec3 @Nullable [] clip(Vec3 start, Vec3 end) {
            final var delta = end.subtract(start);
            final double[] p = {-delta.x, delta.x, -delta.y, delta.y, -delta.z, delta.z};
            final double[] q = {
                    start.x - this.bounds.minX, this.bounds.maxX - start.x,
                    start.y - this.bounds.minY, this.bounds.maxY - start.y,
                    start.z - this.bounds.minZ, this.bounds.maxZ - start.z
            };

            double from = 0, to = 1;
            for (int i = 0; i < 6; i++) {
                if (p[i] == 0) {
                    if (q[i] < 0) return null;
                } else {
                    final double t = q[i] / p[i];
                    if (p[i] < 0) {
                        from = Math.max(from, t);
                    } else {
                        to = Math.min(to, t);
                    }
                }
            }

            if (from > to) return null;
            return new Vec3[]{start.add(delta.scale(from)), start.add(delta.scale(to))};
        }
    }
}
