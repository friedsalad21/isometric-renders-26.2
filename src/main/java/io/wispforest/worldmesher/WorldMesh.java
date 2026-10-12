package io.wispforest.worldmesher;

import com.google.common.collect.HashMultimap;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import org.joml.Vector3f;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.glisco.isometricrenders.compat.IrisCompat;
import io.wispforest.worldmesher.render.FluidVertexConsumer;
import io.wispforest.worldmesher.render.MeshRenderView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class WorldMesh {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldMesh.class);

    // Render setup data
    private final BlockAndTintGetter world;
    private final BlockPos from, to;
    private final AABB dimensions;

    private final boolean cull;

    private final Runnable renderStartAction;
    private final Runnable renderEndAction;

    private final TriFunction<Player, BlockPos, BlockPos, List<Entity>> entitySupplier;
    private DynamicRenderInfo renderInfo = DynamicRenderInfo.EMPTY;
    private boolean entitiesFrozen;
    private boolean freezeEntities;

    // Build process data
    private MeshState state = MeshState.NEW;

    private float buildProgress = 0;
    private @Nullable CompletableFuture<Void> buildFuture = null;

    // Vertex storage
    private final Map<ChunkSectionLayer, UploadedLayer> bufferStorage = new EnumMap<>(ChunkSectionLayer.class);

    // Translucent quads are re-sorted whenever the view direction changes,
    // the render's camera has nothing to do with the player's
    private @Nullable MeshData.SortState translucentSort;
    private @Nullable ByteBufferBuilder sortBuffer;
    private final Vector3f lastSortDirection = new Vector3f();

    private record UploadedLayer(GpuBuffer vertices, @Nullable GpuBuffer indices, IndexType indexType, int indexCount) {
        void close() {
            this.vertices.close();
            if (this.indices != null) this.indices.close();
        }
    }

    private WorldMesh(BlockAndTintGetter world, BlockPos from, BlockPos to, boolean cull, boolean useGlobalNeighbors, boolean freezeEntities, Runnable renderStartAction, Runnable renderEndAction, TriFunction<Player, BlockPos, BlockPos, List<Entity>> entitySupplier) {
        this.from = from;
        this.to = to;

        this.world = useGlobalNeighbors
                ? world
                : new MeshRenderView(world, from, to);

        this.cull = cull;
        this.freezeEntities = freezeEntities;
        this.dimensions = AABB.encapsulatingFullBlocks(this.from, this.to);
        this.entitySupplier = entitySupplier;

        this.renderStartAction = renderStartAction;
        this.renderEndAction = renderEndAction;

        this.scheduleRebuild();
    }

    /**
     * Renders this world mesh into the current output target, the given
     * matrix is applied on top of the current model view matrix
     *
     * @param transform The transform to apply to the entire mesh
     */
    public void render(Matrix4f transform) {
        if (!this.canRender()) {
            throw new IllegalStateException("World mesh not prepared!");
        }

        final var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(transform);

        // Enum order puts translucent last
        this.bufferStorage.forEach((layer, uploaded) -> {
            if (layer.translucent()) this.resortIfNeeded(modelView, uploaded);
            renderStartAction.run();

            final var indices = uploaded.indices != null
                    ? uploaded.indices
                    : RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(uploaded.indexCount);

            renderTypeFor(layer).prepare().drawFromBuffer(uploaded.vertices, indices, uploaded.indexType, 0, 0, uploaded.indexCount);

            renderEndAction.run();
        });

        modelView.popMatrix();
    }

    private void resortIfNeeded(Matrix4f modelView, UploadedLayer uploaded) {
        if (this.translucentSort == null || this.sortBuffer == null || uploaded.indices == null) return;

        // Orthographic view: sort by distance to a point far behind the viewer, in mesh space
        var direction = new Matrix4f(modelView).invert().transformDirection(new Vector3f(0, 0, 1)).normalize();
        if (direction.dot(this.lastSortDirection) > 0.99999f) return;
        this.lastSortDirection.set(direction);

        var center = new Vector3f((float) this.dimensions.getXsize(), (float) this.dimensions.getYsize(), (float) this.dimensions.getZsize()).mul(.5f);
        var eye = center.add(direction.mul(1000));

        try (var sorted = this.translucentSort.buildSortedIndexBuffer(this.sortBuffer, VertexSorting.byDistance(eye.x, eye.y, eye.z))) {
            if (sorted == null) return;
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uploaded.indices.slice(), sorted.byteBuffer());
        }
    }

    private static RenderType renderTypeFor(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
    }

    private void releaseSortState() {
        if (this.sortBuffer != null) this.sortBuffer.close();
        this.sortBuffer = null;
        this.translucentSort = null;
    }

    /**
     * Checks whether this mesh is ready for rendering
     */
    public boolean canRender() {
        return this.state.canRender;
    }

    /**
     * Returns the current state of this mesh, used to indicate building progress and rendering availability
     *
     * @return The current {@code MeshState} constant
     */
    public MeshState state() {
        return this.state;
    }

    /**
     * Renamed to {@link #state()}
     */
    @Deprecated(forRemoval = true)
    public MeshState getState() {
        return this.state();
    }

    /**
     * How much of this mesh is built
     *
     * @return The build progress of this mesh
     */
    public float buildProgress() {
        return this.buildProgress;
    }

    /**
     * Renamed to {@link #buildProgress()}
     */
    @Deprecated(forRemoval = true)
    public float getBuildProgress() {
        return this.buildProgress();
    }

    /**
     * @return An object describing the entities and block
     * entities in the area this mesh is covering, with positions
     * relative to the mesh
     */
    public DynamicRenderInfo renderInfo() {
        return this.renderInfo;
    }

    /**
     * Renamed to {@link #renderInfo()}
     */
    @Deprecated(forRemoval = true)
    public DynamicRenderInfo getRenderInfo() {
        return this.renderInfo();
    }

    /**
     * @return The origin position of this mesh's area
     */
    public BlockPos startPos() {
        return this.from;
    }

    /**
     * @return The end position of this mesh's area
     */
    public BlockPos endPos() {
        return this.to;
    }

    public boolean entitiesFrozen() {
        return this.entitiesFrozen;
    }

    public void setFreezeEntities(boolean freezeEntities) {
        this.freezeEntities = freezeEntities;
    }

    /**
     * @return The dimensions of this mesh's entire area
     */
    public AABB dimensions() {
        return dimensions;
    }

    /**
     * Reset this mesh to {@link MeshState#NEW}, releasing
     * all vertex buffers in the process
     */
    public void reset() {
        this.bufferStorage.values().forEach(UploadedLayer::close);
        this.bufferStorage.clear();
        this.releaseSortState();

        this.state = MeshState.NEW;
    }

    /**
     * Renamed to {@link #reset()}
     */
    @Deprecated(forRemoval = true)
    public void clear() {
        this.reset();
    }

    /**
     * Schedule a rebuild of this mesh on
     * the main worker executor
     */
    public synchronized void scheduleRebuild() {
        this.scheduleRebuild(Util.backgroundExecutor());
    }

    /**
     * Schedule a rebuild of this mesh,
     * on the supplied executor
     *
     * @return A future completing when the build process is finished,
     * or {@code null} if this mesh is already building
     */
    public synchronized CompletableFuture<Void> scheduleRebuild(Executor executor) {
        if (this.buildFuture != null) return this.buildFuture;

        this.buildProgress = 0;
        this.state = this.state != MeshState.NEW
                ? MeshState.REBUILDING
                : MeshState.BUILDING;

        this.buildFuture = CompletableFuture.runAsync(() -> IrisCompat.withoutVertexExtension(this::build), executor).whenComplete((unused, throwable) -> {
            this.buildFuture = null;

            if (throwable == null) {
                state = MeshState.READY;
            } else {
                LOGGER.warn("World mesh building failed", throwable);
                state = MeshState.CORRUPT;
            }
        });

        return this.buildFuture;
    }

    private void build() {
        var client = Minecraft.getInstance();
        var modelManager = client.getModelManager();

        var blockRenderer = new ModelBlockRenderer(client.options.ambientOcclusion().get(), this.cull, client.getBlockColors());
        var fluidRenderer = new FluidRenderer(modelManager.getFluidStateModelSet());

        var builders = new EnumMap<ChunkSectionLayer, BufferBuilder>(ChunkSectionLayer.class);
        var allocators = new EnumMap<ChunkSectionLayer, ByteBufferBuilder>(ChunkSectionLayer.class);
        Function<ChunkSectionLayer, BufferBuilder> builderFor = layer -> builders.computeIfAbsent(layer, $ -> {
            var allocator = allocators.computeIfAbsent(layer, $$ -> new ByteBufferBuilder(layer.bufferSize()));
            return new BufferBuilder(allocator, PrimitiveTopology.QUADS, layer.vertexFormat());
        });

        BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> builderFor.apply(quad.materialInfo().layer()).putBlockBakedQuad(x, y, z, quad, instance);
        BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> builderFor.apply(ChunkSectionLayer.SOLID).putBlockBakedQuad(x, y, z, quad, instance);
        boolean cutoutLeaves = client.options.cutoutLeaves().get();

        this.entitiesFrozen = this.freezeEntities;
        var entitiesFuture = new CompletableFuture<List<DynamicRenderInfo.EntityEntry>>();
        client.execute(() -> {
            entitiesFuture.complete(this.entitySupplier.apply(client.player, this.from, this.to.offset(1, 1, 1))
                    .stream()
                    .map(entity -> {
                        if (this.freezeEntities) {
                            entity = frozenCopy(client, entity);
                            if (entity == null) return null;
                        }

                        return new DynamicRenderInfo.EntityEntry(
                                entity,
                                client.getEntityRenderDispatcher().getPackedLightCoords(entity, 0)
                        );
                    }).filter(Objects::nonNull).toList());
        });

        var blockEntities = new HashMap<BlockPos, BlockEntity>();

        int currentBlockIndex = 0;
        int blocksToBuild = (this.to.getX() - this.from.getX() + 1)
                * (this.to.getY() - this.from.getY() + 1)
                * (this.to.getZ() - this.from.getZ() + 1);

        for (var pos : BlockPos.betweenClosed(this.from, this.to)) {
            currentBlockIndex++;
            this.buildProgress = currentBlockIndex / (float) blocksToBuild;

            var state = world.getBlockState(pos);
            if (state.isAir()) continue;

            var renderPos = pos.subtract(from);
            if (world.getBlockEntity(pos) != null) {
                blockEntities.put(renderPos.immutable(), world.getBlockEntity(pos));
            }

            var fluidState = state.getFluidState();
            if (!fluidState.isEmpty()) {
                // The fluid renderer emits section-relative positions
                var fluidTransform = new Matrix4f().translation(
                        renderPos.getX() - (pos.getX() & 15),
                        renderPos.getY() - (pos.getY() & 15),
                        renderPos.getZ() - (pos.getZ() & 15)
                );

                fluidRenderer.tesselate(this.world, pos, layer -> new FluidVertexConsumer(builderFor.apply(layer), fluidTransform), state, fluidState);
            }

            if (state.getRenderShape() == RenderShape.MODEL) {
                blockRenderer.tesselateBlock(
                        ModelBlockRenderer.forceOpaque(cutoutLeaves, state) ? opaqueQuadOutput : quadOutput,
                        renderPos.getX(), renderPos.getY(), renderPos.getZ(),
                        this.world,
                        pos,
                        state,
                        modelManager.getBlockStateModelSet().get(state),
                        state.getSeed(pos)
                );
            }
        }

        // GPU uploads have to happen on the render thread
        var future = new CompletableFuture<Void>();
        client.execute(() -> {
            try {
                this.bufferStorage.values().forEach(UploadedLayer::close);
                this.bufferStorage.clear();
                this.releaseSortState();

                var device = RenderSystem.getDevice();
                builders.forEach((layer, builder) -> {
                    try (var mesh = builder.build()) {
                        if (mesh == null) return;

                        if (layer.translucent()) {
                            // Initial order doesn't matter, the first render re-sorts for its view
                            this.translucentSort = mesh.sortQuads(allocators.get(layer), VertexSorting.byDistance(0, 0, 0));
                            this.sortBuffer = new ByteBufferBuilder(mesh.drawState().indexCount() * 4);
                            this.lastSortDirection.zero();
                        }

                        var vertices = device.createBuffer(() -> "WorldMesh " + layer.label() + " vertices", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                        var indexBuffer = mesh.indexBuffer();
                        var indices = indexBuffer != null
                                ? device.createBuffer(() -> "WorldMesh " + layer.label() + " indices", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, indexBuffer)
                                : null;

                        var drawState = mesh.drawState();
                        var indexType = indices != null
                                ? drawState.indexType()
                                : RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).type();

                        this.bufferStorage.put(layer, new UploadedLayer(vertices, indices, indexType, drawState.indexCount()));
                    }
                });

                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            } finally {
                allocators.values().forEach(ByteBufferBuilder::close);
            }
        });
        future.join();

        var entities = HashMultimap.<Vec3, DynamicRenderInfo.EntityEntry>create();
        for (var entityEntry : entitiesFuture.join()) {
            entities.put(
                    entityEntry.entity().position().subtract(this.from.getX(), this.from.getY(), this.from.getZ()),
                    entityEntry
            );
        }

        this.renderInfo = new DynamicRenderInfo(
                blockEntities, entities
        );
    }

    private static @Nullable Entity frozenCopy(Minecraft client, Entity original) {
        // Players can't be created from their type, they need a profile for name and skin
        if (original instanceof AbstractClientPlayer player) {
            final var copy = new RemotePlayer(client.level, player.getGameProfile());
            copy.restoreFrom(player);
            copy.setId(player.getId());
            copy.copyPosition(player);

            // Skin layers, main hand and pose aren't saved with the entity
            final var syncedData = player.getEntityData().getNonDefaultValues();
            if (syncedData != null) copy.getEntityData().assignValues(syncedData);

            copy.setYHeadRot(player.getYHeadRot());
            copy.setYBodyRot(player.yBodyRot);
            copy.setOldPosAndRot();
            return copy;
        }

        final var copy = original.getType().create(client.level, EntitySpawnReason.LOAD);
        if (copy == null) return null;

        copy.restoreFrom(original);
        copy.setId(original.getId());
        copy.copyPosition(original);
        copy.tick();
        return copy;
    }

    public static class Builder {

        private final BlockAndTintGetter world;
        private final TriFunction<Player, BlockPos, BlockPos, List<Entity>> entitySupplier;

        private final BlockPos origin;
        private final BlockPos end;
        private boolean cull = true;
        private boolean useGlobalNeighbors = false;
        private boolean freezeEntities = false;

        private Runnable startAction = () -> {
        };
        private Runnable endAction = () -> {
        };

        @Deprecated(forRemoval = true)
        public Builder(BlockAndTintGetter world, BlockPos origin, BlockPos end, Function<Player, List<Entity>> entitySupplier) {
            this.world = world;
            this.origin = origin;
            this.end = end;
            this.entitySupplier = (player, $, $$) -> entitySupplier.apply(player);
        }

        public Builder(BlockAndTintGetter world, BlockPos origin, BlockPos end, TriFunction<Player, BlockPos, BlockPos, List<Entity>> entitySupplier) {
            this.world = world;
            this.origin = origin;
            this.end = end;
            this.entitySupplier = entitySupplier;
        }

        public Builder(ClientLevel world, BlockPos origin, BlockPos end) {
            this(world, origin, end, (except, min, max) -> world.getEntities(except, AABB.encapsulatingFullBlocks(min, max), entity -> true));
        }


        public Builder disableCulling() {
            this.cull = false;
            return this;
        }

        public Builder useGlobalNeighbors() {
            this.useGlobalNeighbors = true;
            return this;
        }

        public Builder freezeEntities() {
            this.freezeEntities = true;
            return this;
        }

        public Builder renderActions(Runnable startAction, Runnable endAction) {
            this.startAction = startAction;
            this.endAction = endAction;
            return this;
        }

        public WorldMesh build() {
            BlockPos start = new BlockPos(Math.min(origin.getX(), end.getX()), Math.min(origin.getY(), end.getY()), Math.min(origin.getZ(), end.getZ()));
            BlockPos target = new BlockPos(Math.max(origin.getX(), end.getX()), Math.max(origin.getY(), end.getY()), Math.max(origin.getZ(), end.getZ()));

            return new WorldMesh(world, start, target, cull, useGlobalNeighbors, freezeEntities, startAction, endAction, entitySupplier);
        }
    }

    public enum MeshState {
        NEW(false, false),
        BUILDING(true, false),
        REBUILDING(true, true),
        READY(false, true),
        CORRUPT(false, false);

        public final boolean isBuildStage;
        public final boolean canRender;

        MeshState(boolean buildStage, boolean canRender) {
            this.isBuildStage = buildStage;
            this.canRender = canRender;
        }
    }

}
