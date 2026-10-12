package com.glisco.isometricrenders.mixin;

import com.glisco.isometricrenders.render.DebugOverlays;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {

    // Debug renderers only draw entities the player can currently see,
    // area renders want everything inside the area regardless
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void showEntitiesInRenderedArea(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        final var area = DebugOverlays.collectingArea();
        if (area == null) return;

        // Hidden entities get no overlays, frozen ones get their hitboxes drawn separately at their frozen position
        cir.setReturnValue(DebugOverlays.isLiveEntityShown(entity) && area.intersects(entity.getBoundingBox()));
    }
}
