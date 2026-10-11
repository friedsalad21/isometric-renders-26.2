package com.glisco.isometricrenders.util;

import com.glisco.isometricrenders.IsometricRenders;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.phys.AABB;

public class ParticleRestriction<T> {

    public static final Allow<Supplier<Boolean>> ALLOW_DURING_TICK = new Allow<>();
    public static final Allow<Void> ALLOW_ALWAYS = new Allow<>();
    public static final Allow<Void> ALLOW_NEVER = new Allow<>();
    public static final Allow<Predicate<AABB>> ALLOW_IN_AREA = new Allow<>();

    private static final ParticleRestriction<Supplier<Boolean>> DURING_TICK = new ParticleRestriction<>(ALLOW_DURING_TICK, () -> IsometricRenders.inRenderableTick);
    private static final ParticleRestriction<Void> ALWAYS = new ParticleRestriction<>(ALLOW_ALWAYS, null);
    private static final ParticleRestriction<Void> NEVER = new ParticleRestriction<>(ALLOW_NEVER, null);

    private final Allow<T> allow;
    private final T condition;

    private ParticleRestriction(Allow<T> allow, T condition) {
        this.allow = allow;
        this.condition = condition;
    }

    public static ParticleRestriction<Supplier<Boolean>> duringTick() {
        return DURING_TICK;
    }

    public static ParticleRestriction<Void> always() {
        return ALWAYS;
    }

    public static ParticleRestriction<Void> never() {
        return NEVER;
    }

    public static ParticleRestriction<Predicate<AABB>> inArea(AABB area) {
        return new ParticleRestriction<>(ALLOW_IN_AREA, area::intersects);
    }

    public boolean is(Allow<?> allow) {
        return this.allow == allow;
    }

    @SuppressWarnings("unchecked")
    public <C> C conditionFor(Allow<C> allow) {
        return (C) this.condition;
    }

    public static class Allow<D> {
        private Allow() {}
    }
}
