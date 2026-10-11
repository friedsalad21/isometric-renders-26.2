package com.glisco.isometricrenders.mixin;

import com.glisco.isometricrenders.util.ClientRenderCallback;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void onRenderStart(boolean advanceGameTime, CallbackInfo ci) {
        ClientRenderCallback.EVENT.invoker().onRenderStart((Minecraft) (Object) this);
    }
}
