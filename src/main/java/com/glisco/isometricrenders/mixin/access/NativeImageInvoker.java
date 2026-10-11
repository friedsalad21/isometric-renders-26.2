package com.glisco.isometricrenders.mixin.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.channels.WritableByteChannel;

@Mixin(NativeImage.class)
public interface NativeImageInvoker {

    @Invoker("write")
    boolean isometric$write(WritableByteChannel channel);

}
