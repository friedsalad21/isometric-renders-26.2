package com.glisco.isometricrenders.mixin.access;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface ItemRenderStateAccessor {

    @Accessor("modelTransformationMode")
    void isometric$setTransformationMode(ItemDisplayContext mode);
}
