package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.IsometricRenders;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;

import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.property.IntProperty;
import com.glisco.isometricrenders.screen.IsometricUI;
import com.glisco.isometricrenders.util.ExportPathSpec;
import com.glisco.isometricrenders.util.ParticleRestriction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wispforest.owo.ui.container.FlowLayout;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class EntityRenderable extends DefaultRenderable<DefaultPropertyBundle> implements TickingRenderable<DefaultPropertyBundle> {

    private final Minecraft client = Minecraft.getInstance();
    private final Entity entity;

    // Entities that never joined a level have no ID, which 26.x render state extraction requires
    private static int nextRenderId = -1000;

    public EntityRenderable(Entity entity) {
        this.entity = entity;
        applyToEntityAndPassengers(entity, e -> e.setId(nextRenderId--));
    }

    public static EntityRenderable of(EntityType<?> type, @Nullable CompoundTag nbt) {
        final var client = Minecraft.getInstance();

        if (nbt == null) {
            nbt = new CompoundTag();
        }

        final var entity = EntityType.loadEntityRecursive(type, nbt, client.level, EntitySpawnReason.LOAD, e -> e);
        entity.absSnapTo(client.player.getX(), client.player.getY(), client.player.getZ());

        return new EntityRenderable(entity);
    }

    public static EntityRenderable copyOf(Entity source) {
        final var client = Minecraft.getInstance();

        final var output = TagValueOutput.createWithContext(new ProblemReporter.ScopedCollector(IsometricRenders.LOGGER), client.level.registryAccess());
        source.saveWithoutId(output);

        final var entity = EntityType.loadEntityRecursive(source.getType(), output.buildResult(), client.level, EntitySpawnReason.LOAD, e -> e);
        applyToEntityAndPassengers(entity, Entity::tick);

        return new EntityRenderable(entity);
    }

    @Override
    public void emitVertices(PoseStack matrices, SubmitNodeCollector collector, float tickDelta) {
        matrices.pushPose();

        matrices.translate(0, -.5 * this.entity.getBbHeight(), 0);
        matrices.mulPose(Axis.YP.rotationDegrees(180));

        var properties = this.properties();
        this.entity.setYHeadRot(properties.yaw.get());
        if (entity instanceof LivingEntity living) living.yHeadRotO = properties.yaw.get();
        this.entity.yRotO = properties.yaw.get();

        this.entity.setXRot(properties.pitch.get());
        this.entity.xRotO = properties.pitch.get();

        final MutableObject<Vec3> offset = new MutableObject<>(Vec3.ZERO);
        final var dispatcher = client.getEntityRenderDispatcher();
        final var camera = new CameraRenderState();

        applyToEntityAndPassengers(this.entity, entity -> {
            entity.setPosRaw(client.player.getX(), client.player.getY(), client.player.getZ());
            if (entity.isPassenger()) {
                offset.setValue(offset.getValue().add(entity.getVehicle().getPassengerRidingPosition(entity).subtract(entity.position())));
            }

            final var state = dispatcher.extractEntity(entity, tickDelta);
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
            state.shadowPieces.clear();
            state.nameTag = null;

            var offsetPos = offset.getValue();
            matrices.pushPose();
            dispatcher.submit(state, camera, offsetPos.x(), offsetPos.y(), offsetPos.z(), matrices, collector);
            matrices.popPose();
        });

        matrices.mulPose(Axis.YP.rotationDegrees(-180));
        matrices.translate(0, 1.65, 0);

        this.renderParticles(matrices, tickDelta);

        matrices.popPose();
    }

    @Override
    public EntityPropertyBundle properties() {
        return EntityPropertyBundle.INSTANCE;
    }

    @Override
    public ParticleRestriction<?> particleRestriction() {
        return ParticleRestriction.duringTick();
    }

    @Override
    public ExportPathSpec exportPath() {
        return ExportPathSpec.ofIdentified(
                BuiltInRegistries.ENTITY_TYPE.getKey(this.entity.getType()),
                "entity"
        );
    }

    @Override
    public void tick() {
        applyToEntityAndPassengers(this.entity, entity -> {
            if (entity instanceof Player) return;
            client.level.tickNonPassenger(entity);
        });
    }

    private static void applyToEntityAndPassengers(Entity entity, Consumer<Entity> action) {
        action.accept(entity);
        if (entity.getPassengers().isEmpty()) return;
        for (Entity e : entity.getPassengers()) applyToEntityAndPassengers(e, action);
    }

    public static class EntityPropertyBundle extends DefaultPropertyBundle {

        public static final EntityPropertyBundle INSTANCE = new EntityPropertyBundle();

        public final IntProperty yaw = IntProperty.of(0, -180, 180).withRollover();
        public final IntProperty pitch = IntProperty.of(0, -90, 90).withRollover();

        private EntityPropertyBundle() {}

        @Override
        public void buildGuiControls(Renderable<?> renderable, FlowLayout container) {
            super.buildGuiControls(renderable, container);

            IsometricUI.sectionHeader(container, "entity_pose", true);

            IsometricUI.intControl(container, yaw, "entity_pose.yaw", 15);
            IsometricUI.intControl(container, pitch, "entity_pose.pitch", 5);
        }
    }
}
