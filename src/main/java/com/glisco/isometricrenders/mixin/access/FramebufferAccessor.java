package com.glisco.isometricrenders.mixin.access;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderTarget.class)
public interface FramebufferAccessor {

    @Accessor("depthAttachment")
    void isometric$setDepthAttachment(int depthAttachment);

    @Accessor("fbo")
    void isometric$setFbo(int fbo);

    @Accessor("fbo")
    int isometric$getFbo();

}
