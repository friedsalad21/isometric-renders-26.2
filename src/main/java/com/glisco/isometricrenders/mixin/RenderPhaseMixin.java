package com.glisco.isometricrenders.mixin;

import com.glisco.isometricrenders.IsometricRenders;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderStateShard.class)
public class RenderPhaseMixin {

    @ModifyExpressionValue(method = {"method_62272", "method_34555", "method_29377"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getFramebuffer()Lnet/minecraft/client/gl/Framebuffer;"))
    private static RenderTarget injectProperRenderTarget(RenderTarget original) {
        if (IsometricRenders.mainTargetOverride != null) {
            return IsometricRenders.mainTargetOverride;
        }

        return original;
    }

}