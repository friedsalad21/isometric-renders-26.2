package com.glisco.isometricrenders.test;

import com.glisco.isometricrenders.render.AreaRenderable;
import com.glisco.isometricrenders.screen.RenderScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.option.Profile;
import net.minecraft.core.BlockPos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders a scene full of translucent blocks with every shader pack in
 * {@code $ISO_SHADERPACKS}, on each pack's lowest profile. Only runs with
 * {@code -PwithShaders} (Iris + Sodium loaded) and the env variable set
 */
public class ShaderPackClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        final var source = System.getenv("ISO_SHADERPACKS");
        if (!FabricLoader.getInstance().isModLoaded("iris") || source == null) return;

        final List<String> packs;
        try (var files = Files.list(Path.of(source))) {
            packs = files.filter(path -> path.toString().endsWith(".zip")).sorted().map(path -> {
                try {
                    Files.copy(path, Iris.getShaderpacksDirectory().resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return path.getFileName().toString();
            }).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (var world = TestWorlds.emptyCreative(context)) {
            context.runOnClient(client -> client.options.renderDistance().set(4));
            context.waitTicks(40);

            final BlockPos origin = context.computeOnClient(client -> client.player.blockPosition()).offset(3, 0, 0);
            final var server = world.getServer();
            buildScene(server, origin);
            context.waitTicks(20);

            shoot(context, "none", origin);
            for (var pack : packs) {
                System.out.println("[isometric-shaders] loading " + pack);
                context.runOnClient(client -> loadPack(pack));
                context.waitTicks(40);
                shoot(context, pack.replaceAll("[^A-Za-z0-9.]+", "_"), origin);
            }

            context.runOnClient(client -> {
                try {
                    Iris.getIrisConfig().setShadersEnabled(false);
                    Iris.getIrisConfig().save();
                    Iris.reload();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static void buildScene(TestServerContext server, BlockPos o) {
        fill(server, o, 0, 0, 0, 6, 4, 6, "minecraft:air");
        fill(server, o, 0, 0, 0, 6, 0, 6, "minecraft:stone_bricks");
        fill(server, o, 0, 1, 0, 6, 1, 0, "minecraft:glass");
        fill(server, o, 0, 1, 2, 2, 2, 2, "minecraft:light_blue_stained_glass");
        fill(server, o, 4, 1, 2, 6, 2, 2, "minecraft:orange_stained_glass");
        fill(server, o, 0, 1, 4, 2, 1, 4, "minecraft:glass_pane");
        fill(server, o, 4, 1, 4, 4, 1, 4, "minecraft:ice");
        fill(server, o, 5, 1, 4, 5, 1, 4, "minecraft:slime_block");
        fill(server, o, 1, 1, 5, 3, 1, 6, "minecraft:water");
        fill(server, o, 6, 1, 6, 6, 3, 6, "minecraft:oak_leaves[persistent=true]");
        fill(server, o, 3, 1, 3, 3, 1, 3, "minecraft:chest");
        fill(server, o, 0, 1, 6, 0, 1, 6, "minecraft:redstone_block");
        server.runCommand("summon minecraft:pig " + (o.getX() + 1.5) + " " + (o.getY() + 1) + " " + (o.getZ() + 1.5) + " {NoAI:1b}");
        server.runCommand("tp @p " + (o.getX() + 3.5) + " " + (o.getY() + 6) + " " + (o.getZ() - 4) + " 0 45");
    }

    private static void fill(TestServerContext server, BlockPos o, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        server.runCommand("fill " + (o.getX() + x1) + " " + (o.getY() + y1) + " " + (o.getZ() + z1) + " "
                + (o.getX() + x2) + " " + (o.getY() + y2) + " " + (o.getZ() + z2) + " " + block);
    }

    private static void shoot(ClientGameTestContext context, String name, BlockPos origin) {
        context.setScreen(() -> null);
        context.waitTicks(10);
        System.out.println("[isometric-shaders] screenshot " + context.takeScreenshot("shaders_world_" + name));

        final var export = new AtomicReference<File>();
        context.setScreen(() -> {
            var screen = new RenderScreen(AreaRenderable.of(origin, origin.offset(6, 4, 6)));
            screen.setExportCallback(export::set);
            return screen;
        });
        context.waitTicks(30);
        System.out.println("[isometric-shaders] screenshot " + context.takeScreenshot("shaders_render_" + name));
        context.runOnClient(client -> ((RenderScreen) client.gui.screen()).scheduleCapture());
        context.waitFor(client -> export.get() != null, 600);
        System.out.println("[isometric-shaders] exported " + name + " " + export.get().getAbsolutePath());
    }

    private static void loadPack(String pack) {
        try {
            Iris.getIrisConfig().setShaderPackName(pack);
            Iris.getIrisConfig().setShadersEnabled(true);
            Iris.getIrisConfig().save();
            Iris.reload();

            // Lowest profile so software rendering keeps up
            final var lowest = new AtomicReference<Profile>();
            Iris.getCurrentPack().ifPresent(shaderPack -> shaderPack.getMenuContainer().getProfiles().forEach((name, profile) -> {
                if (lowest.get() == null || profile.precedence < lowest.get().precedence) lowest.set(profile);
            }));
            if (lowest.get() != null) {
                System.out.println("[isometric-shaders] profile " + lowest.get().name);
                Iris.queueShaderPackOptionsFromProfile(lowest.get());
                Iris.reload();
            }

            Iris.getStoredError().ifPresent(e -> System.out.println("[isometric-shaders] error in " + pack + ": " + e));
            System.out.println("[isometric-shaders] in use: " + Iris.isPackInUseQuick() + " (" + Iris.getCurrentPackName() + ")");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
