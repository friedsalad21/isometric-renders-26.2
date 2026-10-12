package com.glisco.isometricrenders.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.vertices.ImmediateState;

public final class IrisCompat {

    private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");

    private IrisCompat() {}

    /**
     * Runs {@code action} with Iris' vertex format extension turned off on this thread.
     * While a shader pack is active and the game is extracting a frame, Iris widens every
     * new block and entity buffer to its own format, from any thread. Area meshes are built
     * in the background, so their buffers would randomly end up in a format vanilla's
     * pipelines can't read
     */
    public static void withoutVertexExtension(Runnable action) {
        if (!IRIS_LOADED) {
            action.run();
            return;
        }

        final boolean previous = ImmediateState.skipExtension.get();
        ImmediateState.skipExtension.set(true);
        try {
            action.run();
        } finally {
            ImmediateState.skipExtension.set(previous);
        }
    }
}
