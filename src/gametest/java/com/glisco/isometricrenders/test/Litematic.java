package com.glisco.isometricrenders.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Pastes a Litematica schematic straight into a level, so tests can render real builds
 * without driving Litematica's UI. Blocks are placed without updates, so redstone stays
 * exactly as saved
 */
final class Litematic {

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

    private Litematic() {}

    /**
     * @return the size of the pasted build, whose minimum corner is {@code origin}
     */
    static BlockPos paste(ServerLevel level, Path file, BlockPos origin) throws IOException {
        final var root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        final var blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        final var regions = root.getCompoundOrEmpty("Regions");

        // Regions sit relative to the schematic's origin and can have negative sizes, so find the overall min first
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var name : regions.keySet()) {
            final var region = regions.getCompoundOrEmpty(name);
            final var min = regionMin(region);
            final var size = regionSize(region);
            minX = Math.min(minX, min.getX());
            minY = Math.min(minY, min.getY());
            minZ = Math.min(minZ, min.getZ());
            maxX = Math.max(maxX, min.getX() + size.getX());
            maxY = Math.max(maxY, min.getY() + size.getY());
            maxZ = Math.max(maxZ, min.getZ() + size.getZ());
        }

        for (var name : regions.keySet()) {
            final var region = regions.getCompoundOrEmpty(name);
            final var regionOrigin = origin.offset(regionMin(region).offset(-minX, -minY, -minZ));
            final var size = regionSize(region);

            final var paletteTag = region.getListOrEmpty("BlockStatePalette");
            final var palette = new BlockState[paletteTag.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = NbtUtils.readBlockState(blocks, paletteTag.getCompoundOrEmpty(i));
            }

            // Litematica packs indices back to back, so an entry can span two longs
            final long[] states = region.getLongArray("BlockStates").orElse(new long[0]);
            final int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.length - 1));
            final long mask = (1L << bits) - 1;

            int index = 0;
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    for (int x = 0; x < size.getX(); x++, index++) {
                        final long bit = (long) index * bits;
                        final int word = (int) (bit >>> 6);
                        final int offset = (int) (bit & 63);
                        long value = states[word] >>> offset;
                        if (offset + bits > 64) value |= states[word + 1] << (64 - offset);

                        final var state = palette[(int) (value & mask)];
                        if (!state.isAir()) level.setBlock(regionOrigin.offset(x, y, z), state, FLAGS);
                    }
                }
            }

            final var blockEntities = region.getListOrEmpty("TileEntities");
            for (int i = 0; i < blockEntities.size(); i++) {
                final var tag = blockEntities.getCompoundOrEmpty(i);
                final var pos = regionOrigin.offset(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
                final var blockEntity = BlockEntity.loadStatic(pos, level.getBlockState(pos), withPos(tag, pos), level.registryAccess());
                if (blockEntity != null) level.setBlockEntity(blockEntity);
            }
        }

        return new BlockPos(maxX - minX, maxY - minY, maxZ - minZ);
    }

    private static CompoundTag withPos(CompoundTag tag, BlockPos pos) {
        final var copy = tag.copy();
        copy.putInt("x", pos.getX());
        copy.putInt("y", pos.getY());
        copy.putInt("z", pos.getZ());
        return copy;
    }

    private static BlockPos regionMin(CompoundTag region) {
        final var pos = region.getCompoundOrEmpty("Position");
        final var size = region.getCompoundOrEmpty("Size");
        return new BlockPos(
                min(pos.getIntOr("x", 0), size.getIntOr("x", 0)),
                min(pos.getIntOr("y", 0), size.getIntOr("y", 0)),
                min(pos.getIntOr("z", 0), size.getIntOr("z", 0))
        );
    }

    private static int min(int position, int size) {
        return size < 0 ? position + size + 1 : position;
    }

    private static BlockPos regionSize(CompoundTag region) {
        final var size = region.getCompoundOrEmpty("Size");
        return new BlockPos(Math.abs(size.getIntOr("x", 0)), Math.abs(size.getIntOr("y", 0)), Math.abs(size.getIntOr("z", 0)));
    }
}
