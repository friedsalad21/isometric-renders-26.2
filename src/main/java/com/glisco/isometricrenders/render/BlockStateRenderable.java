package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.mixin.access.BlockEntityAccessor;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.util.ExportPathSpec;
import com.glisco.isometricrenders.util.ParticleRestriction;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BlockStateRenderable extends DefaultRenderable<DefaultPropertyBundle> implements TickingRenderable<DefaultPropertyBundle> {

    private final Minecraft client = Minecraft.getInstance();

    private final BlockState state;
    private final @Nullable BlockEntity entity;

    public BlockStateRenderable(BlockState state, @Nullable BlockEntity entity) {
        this.state = state;
        this.entity = entity;
    }

    public static BlockStateRenderable of(Block block) {
        return of(block.defaultBlockState(), null);
    }

    public static BlockStateRenderable of(BlockState state, @Nullable CompoundTag nbt) {
        final var client = Minecraft.getInstance();

        BlockEntity blockEntity = null;

        if (state.getBlock() instanceof EntityBlock provider) {
            blockEntity = provider.newBlockEntity(client.player.blockPosition(), state);
            prepareBlockEntity(state, blockEntity, nbt);
        }

        return new BlockStateRenderable(state, blockEntity);
    }

    public static BlockStateRenderable copyOf(Level world, BlockPos pos) {
        final var state = world.getBlockState(pos);
        final var data = world.getBlockEntity(pos) != null
                ? world.getBlockEntity(pos).saveWithoutMetadata(world.registryAccess())
                : null;

        return of(state, data);
    }

    @Override
    public void emitVertices(PoseStack matrices, MultiBufferSource vertexConsumers, float tickDelta) {
        matrices.pushPose();
        matrices.translate(-0.5, -0.5, -0.5);

        if (this.state.getRenderShape() != RenderShape.INVISIBLE) {
            this.client.getBlockRenderer().renderSingleBlock(this.state, matrices, vertexConsumers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }

        if (this.entity != null && this.client.getBlockEntityRenderDispatcher().getRenderer(this.entity) != null) {
            this.client.getBlockEntityRenderDispatcher().getRenderer(this.entity).render(entity, tickDelta, matrices, vertexConsumers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }

        if (vertexConsumers instanceof MultiBufferSource.BufferSource immediate) {
            immediate.endBatch();
        }

        double xOffset = this.client.player.getX() % 1d;
        double zOffset = this.client.player.getZ() % 1d;

        if (xOffset < 0) xOffset += 1;
        if (zOffset < 0) zOffset += 1;

        matrices.translate(xOffset, 1.65 + this.client.player.getY() % 1d, zOffset);
        this.renderParticles(matrices.last().pose(), tickDelta);

        matrices.popPose();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void tick() {
        if (this.entity != null && this.state.getTicker(client.level, this.entity.getType()) != null) {
            final var ticker = this.state.getTicker(client.level, (BlockEntityType<BlockEntity>) this.entity.getType());
            if (ticker == null) return;

            ticker.tick(client.level, client.player.blockPosition(), this.state, this.entity);
        }

        if (client.level.random.nextDouble() < 0.150) {
            this.state.getBlock().animateTick(this.state, client.level, client.player.blockPosition(), client.level.random);
        }
    }

    @Override
    public DefaultPropertyBundle properties() {
        return DefaultPropertyBundle.get();
    }

    @Override
    public ParticleRestriction<?> particleRestriction() {
        return ParticleRestriction.duringTick();
    }

    @Override
    public ExportPathSpec exportPath() {
        return ExportPathSpec.ofIdentified(
                BuiltInRegistries.BLOCK.getKey(this.state.getBlock()),
                "block"
        );
    }

    private static void prepareBlockEntity(BlockState state, BlockEntity blockEntity, @Nullable CompoundTag nbt) {
        if (blockEntity == null) return;

        ((BlockEntityAccessor) blockEntity).isometric$setCachedState(state);
        blockEntity.setLevel(Minecraft.getInstance().level);

        if (nbt == null) return;

        final var nbtCopy = nbt.copy();

        nbtCopy.putInt("x", 0);
        nbtCopy.putInt("y", 0);
        nbtCopy.putInt("z", 0);

        blockEntity.loadWithComponents(nbtCopy, Minecraft.getInstance().level.registryAccess());
    }
}
