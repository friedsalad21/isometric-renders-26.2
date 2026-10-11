package io.wispforest.worldmesher.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

public class MeshRenderView implements BlockAndTintGetter {

    private final BlockAndTintGetter delegate;
    private final BlockPos from, to;

    public MeshRenderView(BlockAndTintGetter delegate, BlockPos from, BlockPos to) {
        this.delegate = delegate;
        this.from = from;
        this.to = to;
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return this.delegate.cardinalLighting();
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.delegate.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return this.delegate.getBlockTint(pos, colorResolver);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return this.contains(pos)
                ? this.delegate.getBlockEntity(pos)
                : null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.contains(pos)
                ? this.delegate.getBlockState(pos)
                : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.contains(pos)
                ? this.delegate.getFluidState(pos)
                : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public int getBrightness(LightLayer type, BlockPos pos) {
        return this.contains(pos)
                ? BlockAndTintGetter.super.getBrightness(type, pos)
                : type == LightLayer.SKY ? 15 : 0;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        return this.contains(pos)
                ? BlockAndTintGetter.super.getRawBrightness(pos, ambientDarkness)
                : 15;
    }

    @Override
    public int getHeight() {
        return this.delegate.getHeight();
    }

    @Override
    public int getMinY() {
        return this.delegate.getMinY();
    }

    public boolean contains(BlockPos pos) {
        return this.from.getX() <= pos.getX() && this.from.getY() <= pos.getY() && this.from.getZ() <= pos.getZ()
                && this.to.getX() >= pos.getX() && this.to.getY() >= pos.getY() && this.to.getZ() >= pos.getZ();
    }
}
