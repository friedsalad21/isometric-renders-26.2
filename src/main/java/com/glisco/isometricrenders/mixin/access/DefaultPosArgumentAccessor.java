package com.glisco.isometricrenders.mixin.access;

import net.minecraft.commands.arguments.coordinates.WorldCoordinate;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldCoordinates.class)
public interface DefaultPosArgumentAccessor {

    @Accessor("x")
    WorldCoordinate isometric$getX();

    @Accessor("y")
    WorldCoordinate isometric$getY();

    @Accessor("z")
    WorldCoordinate isometric$getZ();

}
