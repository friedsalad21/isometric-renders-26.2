package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.compat.CarpetShapes;
import com.glisco.isometricrenders.mixin.access.CameraInvoker;
import com.glisco.isometricrenders.mixin.access.EntityHitboxDebugRendererInvoker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Captures what the world renderer would draw as debug overlays (F3 hitboxes,
 * chunk borders and every other debug renderer, plus Scarpet shapes from Carpet)
 * so area renders can include them
 */
public final class DebugOverlays {

    private static final boolean CARPET_LOADED = FabricLoader.getInstance().isModLoaded("carpet");

    private static @Nullable AABB collectingArea = null;
    private static Map<Entity, Vec3> frozenEntities = Map.of();

    private DebugOverlays() {}

    /**
     * @return The area currently being collected for, while debug renderers run
     */
    public static @Nullable AABB collectingArea() {
        return collectingArea;
    }

    /**
     * @return Whether the given entity is drawn at a frozen position, while debug renderers run
     */
    public static boolean isFrozen(Entity entity) {
        return frozenEntities.containsKey(entity);
    }

    /**
     * Submits all debug overlays around {@code area}, positioned relative to {@code origin}
     *
     * @param yaw            The camera yaw text and billboards should face
     * @param pitch          The camera pitch text and billboards should face
     * @param clip           Whether to cut off everything outside of {@code area}
     * @param frozenEntities Entities drawn at a fixed position instead of their live one, their hitboxes follow
     */
    public static SubmitNodeStorage collect(Vec3 origin, AABB area, float yaw, float pitch, boolean clip, Map<Entity, Vec3> frozenEntities, float tickDelta) {
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

            submitDebugRenderers(client, levelState.cameraRenderState, origin, area, frozenEntities, tickDelta, storage);
            if (CARPET_LOADED) CarpetShapes.submit(storage, levelState, tickDelta);
        } finally {
            ((CameraInvoker) camera).isometric$setPosition(previousPosition);
            ((CameraInvoker) camera).isometric$setRotation(previousYaw, previousPitch);
        }

        return storage;
    }

    private static void submitDebugRenderers(Minecraft client, CameraRenderState cameraState, Vec3 origin, AABB area, Map<Entity, Vec3> frozen, float tickDelta, SubmitNodeStorage storage) {
        if (client.level == null || client.getConnection() == null) return;

        // Same everything-nearby frustum as particles, the area restriction happens later
        final var frustum = new Frustum(new Matrix4f(), new Matrix4f().setOrtho(-512, 512, -512, 512, -512, 512));
        frustum.prepare(origin.x, origin.y, origin.z);

        final var collector = new SimpleGizmoCollector();
        collectingArea = area;
        frozenEntities = frozen;
        try (var ignored = Gizmos.withCollector(collector)) {
            client.levelExtractor.debugRenderer.emitGizmos(frustum, origin.x, origin.y, origin.z, tickDelta);
            client.levelExtractor.gameTestBlockHighlightRenderer.emitGizmos();
        } finally {
            collectingArea = null;
            frozenEntities = Map.of();
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

        if (client.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
            emitFrozenHitboxes(client, frozen, standard, onTop, now);
        }

        standard.submit(storage, cameraState, false);
        onTop.submit(storage, cameraState, true);
    }

    /**
     * Runs vanilla's hitbox drawing for each frozen entity and moves
     * the result from the entity's live position to its frozen one
     */
    private static void emitFrozenHitboxes(Minecraft client, Map<Entity, Vec3> frozen, GizmoPrimitives standard, GizmoPrimitives onTop, long now) {
        final var hitboxes = (EntityHitboxDebugRendererInvoker) new EntityHitboxDebugRenderer(client);

        frozen.forEach((entity, frozenPos) -> {
            if (entity.isInvisible()) return;

            final float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(!entity.level().tickRateManager().isEntityFrozen(entity));
            final var collector = new SimpleGizmoCollector();
            try (var ignored = Gizmos.withCollector(collector)) {
                hitboxes.isometric$showHitboxes(entity, partialTicks, false);
            }

            final var offset = frozenPos.subtract(entity.getPosition(partialTicks));
            for (var instance : collector.drainGizmos()) {
                instance.gizmo().emit(new OffsetPrimitives(instance.isAlwaysOnTop() ? onTop : standard, offset), instance.getAlphaMultiplier(now));
            }
        });
    }

    private record OffsetPrimitives(GizmoPrimitives delegate, Vec3 offset) implements GizmoPrimitives {

        @Override
        public void addPoint(Vec3 pos, int color, float size) {
            this.delegate.addPoint(pos.add(this.offset), color, size);
        }

        @Override
        public void addLine(Vec3 start, Vec3 end, int color, float width) {
            this.delegate.addLine(start.add(this.offset), end.add(this.offset), color, width);
        }

        @Override
        public void addTriangleFan(Vec3[] points, int color) {
            final var moved = new Vec3[points.length];
            for (int i = 0; i < points.length; i++) moved[i] = points[i].add(this.offset);
            this.delegate.addTriangleFan(moved, color);
        }

        @Override
        public void addQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
            this.delegate.addQuad(a.add(this.offset), b.add(this.offset), c.add(this.offset), d.add(this.offset), color);
        }

        @Override
        public void addText(Vec3 pos, String text, TextGizmo.Style style) {
            this.delegate.addText(pos.add(this.offset), text, style);
        }
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
