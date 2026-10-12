package com.glisco.isometricrenders.mixin.access;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraInvoker {

    @Invoker("setRotation")
    void isometric$setRotation(float yaw, float pitch);

    @Invoker("setPosition")
    void isometric$setPosition(Vec3 position);

}
