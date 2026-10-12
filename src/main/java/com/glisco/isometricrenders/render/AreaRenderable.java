package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.property.Property;
import com.glisco.isometricrenders.screen.IsometricUI;
import com.glisco.isometricrenders.util.ExportPathSpec;
import com.glisco.isometricrenders.util.ParticleRestriction;
import com.glisco.isometricrenders.util.Translate;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.worldmesher.WorldMesh;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.HashMap;
import java.util.function.Predicate;

public class AreaRenderable extends DefaultRenderable<AreaRenderable.AreaPropertyBundle> {

    private final Minecraft client = Minecraft.getInstance();

    private final WorldMesh mesh;
    private final int ySize;
    private final int xSize;
    private final int zSize;

    public AreaRenderable(WorldMesh mesh) {
        this.mesh = mesh;

        final var dimensions = mesh.dimensions();
        this.xSize = (int) dimensions.getXsize();
        this.ySize = (int) dimensions.getYsize();
        this.zSize = (int) dimensions.getZsize();
    }

    public static AreaRenderable of(BlockPos origin, BlockPos end) {
        final WorldMesh.Builder builder = new WorldMesh.Builder(Minecraft.getInstance().level, origin, end);
        if (AreaPropertyBundle.INSTANCE.freezeEntities.get()) {
            builder.freezeEntities();
        }
        return new AreaRenderable(builder.build());
    }

    @Override
    public void emitVertices(PoseStack matrices, SubmitNodeCollector collector, float tickDelta) {
        if (!mesh.canRender()) {
            if (mesh.state() == WorldMesh.MeshState.CORRUPT) return;

            mesh.scheduleRebuild();
            return;
        }

        matrices.setIdentity();
        matrices.translate(-xSize / 2f, -ySize / 2f, -zSize / 2f);

        final var camera = new CameraRenderState();
        final var blockEntityDispatcher = client.getBlockEntityRenderDispatcher();
        mesh.renderInfo().blockEntities().forEach((blockPos, entity) -> {
            final var renderer = blockEntityDispatcher.getRenderer(entity);
            if (renderer == null) return;

            final var state = renderer.createRenderState();
            renderer.extractRenderState(entity, state, tickDelta, Vec3.ZERO, null);

            matrices.pushPose();
            matrices.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            renderer.submit(state, matrices, collector, camera);
            matrices.popPose();
        });

        final var start = Vec3.atLowerCornerOf(mesh.startPos());
        final var entityDispatcher = client.getEntityRenderDispatcher();
        final var frozenEntities = new HashMap<Entity, Vec3>();
        final Predicate<Entity> liveEntities;

        if (mesh.entitiesFrozen()) {
            mesh.renderInfo().entities().forEach((pos, entry) -> {
                if (!this.isShown(entry.entity())) return;

                // Frozen entities are drawn where they were when the mesh was built, their hitboxes should be too
                this.submitEntity(entry.entity(), pos, entry.light(), 0, matrices, collector, camera);
                frozenEntities.put(entry.entity(), start.add(pos));
            });
            liveEntities = entity -> false;
        } else {
            // Looked up every frame so new entities show up and dead ones go away
            liveEntities = this::isShown;
            for (var entity : client.level.getEntities(client.player, mesh.dimensions(), liveEntities)) {
                final float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(!entity.level().tickRateManager().isEntityFrozen(entity));
                final var pos = entity.getPosition(partialTicks).subtract(start);
                this.submitEntity(entity, pos, entityDispatcher.getPackedLightCoords(entity, partialTicks), partialTicks, matrices, collector, camera);
            }
        }

        if (this.properties().debugOverlays.get()) {
            this.renderDebugOverlays(matrices, start, mesh.dimensions(), this.properties().clipDebugOverlays.get(), liveEntities, frozenEntities, tickDelta);
        }

        var diff = Vec3.atLowerCornerOf(mesh.startPos()).subtract(client.player.position());
        matrices.translate(-diff.x, -diff.y + 1.65, -diff.z);

        this.renderParticles(matrices, tickDelta);
    }

    private boolean isShown(Entity entity) {
        // The local player is the one taking the render, they'd only be in the way
        if (entity == client.player) return false;
        return entity instanceof Player ? this.properties().showPlayers.get() : this.properties().showEntities.get();
    }

    private void submitEntity(Entity entity, Vec3 pos, int light, float partialTicks, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
        final var entityDispatcher = client.getEntityRenderDispatcher();
        final var state = entityDispatcher.extractEntity(entity, partialTicks);
        state.lightCoords = light;
        state.nameTag = null;

        entityDispatcher.submit(state, camera, pos.x, pos.y, pos.z, matrices, collector);
    }

    @Override
    public void draw(Matrix4f modelViewMatrix) {
        if (!mesh.canRender()) return;

        this.mesh.render(new Matrix4f().translation(-xSize / 2f, -ySize / 2f, -zSize / 2f));
        super.draw(modelViewMatrix);
    }

    @Override
    public AreaPropertyBundle properties() {
        return AreaPropertyBundle.INSTANCE;
    }

    @Override
    public ParticleRestriction<?> particleRestriction() {
        final var dimensions = this.mesh.dimensions();
        return ParticleRestriction.inArea(new AABB(
                dimensions.minX,
                dimensions.minY,
                dimensions.minZ,
                dimensions.maxX + 1,
                dimensions.maxY + 1,
                dimensions.maxZ + 1
        ));
    }

    @Override
    public ExportPathSpec exportPath() {
        return ExportPathSpec.of("area_renders", "area_render");
    }

    public static class AreaPropertyBundle extends DefaultPropertyBundle {

        private static final AreaPropertyBundle INSTANCE = new AreaPropertyBundle();

        public static AreaPropertyBundle instance() {
            return INSTANCE;
        }

        public final Property<Boolean> freezeEntities = Property.of(true);
        public final Property<Boolean> showEntities = Property.of(true);
        public final Property<Boolean> showPlayers = Property.of(true);
        public final Property<Boolean> debugOverlays = Property.of(true);
        public final Property<Boolean> clipDebugOverlays = Property.of(true);

        @Override
        public void buildGuiControls(Renderable<?> renderable, FlowLayout container) {
            super.buildGuiControls(renderable, container);
            final var mesh = ((AreaRenderable) renderable).mesh;

            IsometricUI.sectionHeader(container, "mesh_controls", true);

            IsometricUI.dynamicLabel(container, () -> {
                var meshStatus = Translate.gui("mesh_status");
                if (!mesh.state().isBuildStage) {
                    meshStatus.append(Translate.gui("mesh_ready").withStyle(ChatFormatting.GREEN));
                } else {
                    meshStatus.append(Translate.gui(
                            switch (mesh.state()) {
                                case BUILDING -> "mesh_building";
                                case CORRUPT -> "mesh_corrupt";
                                default -> "mesh_rebuilding";
                            },
                            (int) (mesh.buildProgress() * 100)
                    ).withStyle(ChatFormatting.RED));
                }
                return meshStatus;
            });

            IsometricUI.booleanControl(container, this.freezeEntities, "freeze_entities");
            this.freezeEntities.listen((booleanProperty, aBoolean) -> {
                mesh.setFreezeEntities(aBoolean);
            });

            IsometricUI.booleanControl(container, this.showEntities, "show_entities");
            IsometricUI.booleanControl(container, this.showPlayers, "show_players");
            IsometricUI.booleanControl(container, this.debugOverlays, "debug_overlays");
            IsometricUI.booleanControl(container, this.clipDebugOverlays, "clip_debug_overlays");

            container.child(UIComponents.button(Translate.gui("rebuild_mesh"), (ButtonComponent button) -> mesh.scheduleRebuild())
                    .horizontalSizing(Sizing.fixed(80))
                    .margins(Insets.top(5)));
        }

        @Override
        public void applyToViewMatrix(Matrix4fStack modelViewStack) {
            final float scale = this.scale.get() / 1000f;
            modelViewStack.scale(scale, scale, scale);

            modelViewStack.translate(this.xOffset.get() / 2600f, this.yOffset.get() / -2600f, 0);

            modelViewStack.rotate(Axis.XP.rotationDegrees(this.slant.get()));
            modelViewStack.rotate(Axis.YP.rotationDegrees(this.rotation.get()));

            this.updateAndApplyRotationOffset(modelViewStack);
        }
    }
}
