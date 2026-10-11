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
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.worldmesher.WorldMesh;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

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
    public void emitVertices(PoseStack matrices, MultiBufferSource vertexConsumers, float tickDelta) {
        if (!mesh.canRender()) {
            if (mesh.state() == WorldMesh.MeshState.CORRUPT) return;

            mesh.scheduleRebuild();
            return;
        }

        matrices.setIdentity();
        matrices.translate(-xSize / 2f, -ySize / 2f, -zSize / 2f);

        final var blockEntities = mesh.renderInfo().blockEntities();
        blockEntities.forEach((blockPos, entity) -> {
            matrices.pushPose();
            matrices.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            client.getBlockEntityRenderDispatcher().render(entity, 0, matrices, vertexConsumers);
            matrices.popPose();
        });


        super.draw(RenderSystem.getModelViewMatrix());

        final var effectiveDelta = mesh.entitiesFrozen() ? 0 : client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        final var entities = mesh.renderInfo().entities();
        entities.forEach((vec3d, entry) -> {
            if (!mesh.entitiesFrozen()) {
                vec3d = entry.entity().getPosition(effectiveDelta).subtract(mesh.startPos().getX(), mesh.startPos().getY(), mesh.startPos().getZ());
            }

            client.getEntityRenderDispatcher().render(entry.entity(), vec3d.x, vec3d.y, vec3d.z, effectiveDelta, matrices, vertexConsumers, entry.light());
            super.draw(RenderSystem.getModelViewMatrix());
        });

        var diff = Vec3.atLowerCornerOf(mesh.startPos()).subtract(client.player.position());
        matrices.translate(-diff.x, -diff.y + 1.65, -diff.z);

        this.renderParticles(matrices.last().pose(), tickDelta);
    }

    @Override
    public void draw(Matrix4f modelViewMatrix) {
        if (!mesh.canRender()) return;

        super.draw(modelViewMatrix);

        final var meshStack = new PoseStack();
        meshStack.mulPose(modelViewMatrix);
        meshStack.translate(-xSize / 2f, -ySize / 2f, -zSize / 2f);
        this.mesh.render(meshStack);
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

        public final Property<Boolean> freezeEntities = Property.of(true);

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

            container.child(Components.button(Translate.gui("rebuild_mesh"), (ButtonComponent button) -> mesh.scheduleRebuild())
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
