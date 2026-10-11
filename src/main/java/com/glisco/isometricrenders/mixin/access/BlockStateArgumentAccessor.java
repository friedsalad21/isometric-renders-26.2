package com.glisco.isometricrenders.mixin.access;

import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockInput.class)
public interface BlockStateArgumentAccessor {

    @Accessor("data")
    CompoundTag isometric$getData();

}
