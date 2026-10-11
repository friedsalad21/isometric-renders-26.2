package com.glisco.isometricrenders.mixin.access;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Queue;

@Mixin(ParticleEngine.class)
public interface ParticleManagerAccessor {

    @Accessor("particles")
    Map<ParticleRenderType, Queue<Particle>> isometric$getParticles();

}
