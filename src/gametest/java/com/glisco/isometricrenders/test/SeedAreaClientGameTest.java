package com.glisco.isometricrenders.test;

import com.glisco.isometricrenders.render.AreaRenderable;
import com.glisco.isometricrenders.screen.RenderScreen;
import io.wispforest.worldmesher.WorldMesh;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders an area of a generated world, {@code $ISO_SEED} + {@code $ISO_AREA} ("x1 y1 z1 x2 y2 z2"),
 * once plain and once per shader pack (see {@link ShaderPackClientGameTest} for the pack variables).
 * Every render also rebuilds the mesh a few times, since big meshes used to break on rebuild
 */
public class SeedAreaClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        final var seed = System.getenv("ISO_SEED");
        final var area = System.getenv("ISO_AREA");
        if (seed == null || area == null) return;

        final var coords = area.trim().split("\\s+");
        final var from = new BlockPos(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]), Integer.parseInt(coords[2]));
        final var to = new BlockPos(Integer.parseInt(coords[3]), Integer.parseInt(coords[4]), Integer.parseInt(coords[5]));
        final var min = BlockPos.min(from, to);
        final var max = BlockPos.max(from, to);

        final var shaderSource = System.getenv("ISO_SHADERPACKS");
        final List<String> packs = FabricLoader.getInstance().isModLoaded("iris") && shaderSource != null
                ? ShaderPackClientGameTest.copyPacks(shaderSource)
                : List.of();

        TestWorlds.playerOptions(context);
        try (var world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(creator -> {
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setSeed(seed);
        }).create()) {
            context.runOnClient(client -> client.options.renderDistance().set(6));
            final var server = world.getServer();
            server.runCommand("tick freeze");
            server.runCommand("time set noon");
            server.runCommand("forceload add " + min.getX() + " " + min.getZ() + " " + max.getX() + " " + max.getZ());

            // Look at the area from above one corner, like a player picking it
            final var center = Vec3.atCenterOf(min).add(Vec3.atCenterOf(max)).scale(.5);
            server.runCommand("tp @p " + (max.getX() + 8) + " " + (max.getY() + 12) + " " + (max.getZ() + 8)
                    + " facing " + center.x + " " + center.y + " " + center.z);
            server.runCommand("effect give @p minecraft:levitation infinite 255 true");
            // World gen is slow on the test machine, so only wait for the area's own chunks
            context.waitFor(client -> {
                for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
                    for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                        if (!client.level.hasChunk(x, z)) return false;
                    }
                }
                return true;
            }, 12000);
            context.waitTicks(100);

            final var names = new ArrayList<String>();
            names.add("none");
            names.addAll(packs);
            for (var pack : names) {
                if (!pack.equals("none")) {
                    System.out.println("[isometric-seed] loading " + pack);
                    context.runOnClient(client -> ShaderPackClientGameTest.loadPack(pack));
                    context.waitTicks(40);
                }
                shoot(context, pack.replaceAll("[^A-Za-z0-9.]+", "_"), min, max);
            }
        }
    }

    private static void shoot(ClientGameTestContext context, String name, BlockPos min, BlockPos max) {
        context.setScreen(() -> null);
        context.waitTicks(10);
        System.out.println("[isometric-seed] screenshot " + context.takeScreenshot("seed_world_" + name));

        final var export = new AtomicReference<File>();
        final var renderable = new AtomicReference<AreaRenderable>();
        context.setScreen(() -> {
            renderable.set(AreaRenderable.of(min, max));
            var screen = new RenderScreen(renderable.get());
            screen.setExportCallback(export::set);
            return screen;
        });
        context.waitFor(client -> renderable.get().mesh().state() == WorldMesh.MeshState.READY, 2400);
        context.waitTicks(1);
        System.out.println("[isometric-seed] screenshot " + context.takeScreenshot("seed_render_" + name + "_first"));

        final int rebuilds = Integer.parseInt(System.getenv().getOrDefault("ISO_REBUILDS", "3"));
        for (int i = 1; i <= rebuilds; i++) {
            context.runOnClient(client -> renderable.get().mesh().scheduleRebuild());
            // The old mesh keeps drawing while the new one builds, then the new one is swapped in
            context.waitTicks(1);
            System.out.println("[isometric-seed] screenshot " + context.takeScreenshot("seed_render_" + name + "_rebuilding" + i));
            context.waitFor(client -> renderable.get().mesh().state() == WorldMesh.MeshState.READY, 2400);
            context.waitTicks(1);
            System.out.println("[isometric-seed] screenshot " + context.takeScreenshot("seed_render_" + name + "_rebuild" + i));
        }

        // Like a player spamming the rebuild button: a rebuild request every tick, screenshots in between
        final int spamTicks = Integer.parseInt(System.getenv().getOrDefault("ISO_SPAM_TICKS", "0"));
        for (int tick = 1; tick <= spamTicks; tick++) {
            context.runOnClient(client -> renderable.get().mesh().scheduleRebuild());
            context.waitTick();
            if (tick % 4 == 0) {
                System.out.println("[isometric-seed] screenshot " + context.takeScreenshot("seed_render_" + name + "_spam" + tick));
            }
        }
        if (spamTicks > 0) context.waitFor(client -> renderable.get().mesh().state() == WorldMesh.MeshState.READY, 2400);

        context.runOnClient(client -> ((RenderScreen) client.gui.screen()).scheduleCapture());
        context.waitFor(client -> export.get() != null, 1200);
        System.out.println("[isometric-seed] exported " + name + " " + export.get().getAbsolutePath());
    }
}
