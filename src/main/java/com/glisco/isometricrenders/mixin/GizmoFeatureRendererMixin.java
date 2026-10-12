package com.glisco.isometricrenders.mixin;

import com.glisco.isometricrenders.IsometricRenders;
import net.minecraft.client.renderer.feature.GizmoFeatureRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GizmoFeatureRenderer.class)
public class GizmoFeatureRendererMixin {

    private static final Matrix4fc ISOMETRIC$IN_FRONT = new Matrix4f().translation(0, 0, -1e7f);

    // Lines get clipped against the camera's near plane, which makes no sense
    // for the orthographic render projection and cuts half of every overlay off
    @ModifyArg(method = "buildGroup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/GizmoFeatureRenderer;buildLines(Ljava/util/List;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Z)V"), index = 2)
    private Matrix4fc dontClipLinesInRenders(Matrix4fc modelViewMatrix) {
        return IsometricRenders.inRenderableDraw ? ISOMETRIC$IN_FRONT : modelViewMatrix;
    }
}
