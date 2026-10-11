package net.fabricmc.fabric.impl.client.indigo.renderer.render;

import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoLuminanceFix;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;

@SuppressWarnings("UnstableApiUsage")
public class WorldMesherRenderContext extends AbstractBlockRenderContext {

    private final BlockAndTintGetter blockView;
    private final Function<RenderType, VertexConsumer> bufferFunc;

    public WorldMesherRenderContext(BlockAndTintGetter blockView, Function<RenderType, VertexConsumer> bufferFunc) {
        this.blockView = blockView;
        this.bufferFunc = bufferFunc;

        this.blockInfo.prepareForWorld(blockView, true);
        this.blockInfo.random = RandomSource.create();
    }

    public void tessellateBlock(BlockAndTintGetter blockView, BlockState blockState, BlockPos blockPos, final BakedModel model, PoseStack matrixStack) {
        try {
            Vec3 offset = blockState.getOffset(blockPos);
            matrixStack.translate(offset.x, offset.y, offset.z);

            this.matrix = matrixStack.last().pose();
            this.normalMatrix = matrixStack.last().normal();

            blockInfo.recomputeSeed = true;

            aoCalc.clear();
            blockInfo.prepareForBlock(blockState, blockPos, model.useAmbientOcclusion());
            model.emitBlockQuads(getEmitter(), blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, blockInfo.randomSupplier, blockInfo::shouldCullSide);
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Tessellating block in WorldMesher mesh");
            CrashReportCategory crashReportSection = crashReport.addCategory("Block being tessellated");
            CrashReportCategory.populateBlockDetails(crashReportSection, blockView, blockPos, blockState);
            throw new ReportedException(crashReport);
        }
    }

    @Override
    protected AoCalculator createAoCalc(BlockRenderInfo blockInfo) {
        return new AoCalculator(blockInfo) {
            @Override
            public int light(BlockPos pos, BlockState state) {
                return LevelRenderer.getLightColor(WorldMesherRenderContext.this.blockView, state, pos);
            }

            @Override
            public float ao(BlockPos pos, BlockState state) {
                return AoLuminanceFix.INSTANCE.apply(WorldMesherRenderContext.this.blockView, pos, state);
            }
        };
    }

    @Override
    protected VertexConsumer getVertexConsumer(RenderType layer) {
        return this.bufferFunc.apply(layer);
    }
}
