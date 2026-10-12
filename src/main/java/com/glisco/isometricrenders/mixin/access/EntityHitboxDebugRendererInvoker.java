package com.glisco.isometricrenders.mixin.access;

import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityHitboxDebugRenderer.class)
public interface EntityHitboxDebugRendererInvoker {

    @Invoker("showHitboxes")
    void isometric$showHitboxes(Entity entity, float partialTicks, boolean isServerEntity);

}
