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
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders a scene full of translucent blocks, plus every schematic in {@code $ISO_BUILDS},
 * with every shader pack in {@code $ISO_SHADERPACKS}. Packs use their lowest profile, or the one
 * named by {@code $ISO_SHADER_PROFILE}, and {@code $ISO_SHADER_FILTER} narrows down the packs.
 * Only runs with {@code -PwithShaders} (Iris + Sodium loaded) and the env variable set
 */
public class ShaderPackClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        final var source = System.getenv("ISO_SHADERPACKS");
        final var iris = FabricLoader.getInstance().isModLoaded("iris");
        if ((!iris || source == null) && System.getenv("ISO_BUILDS") == null) return;

        final List<String> packs = iris && source != null ? copyPacks(source) : List.of();

        try (var world = TestWorlds.emptyCreative(context)) {
            context.runOnClient(client -> client.options.renderDistance().set(6));
            context.waitTicks(40);

            final BlockPos origin = context.computeOnClient(client -> client.player.blockPosition()).offset(3, 0, 0);
            final var server = world.getServer();
            server.runCommand("tick freeze");
            buildScene(server, origin);

            final var scenes = new ArrayList<Scene>();
            scenes.add(new Scene("scene", origin, origin.offset(6, 4, 6)));
            scenes.addAll(pasteBuilds(server, origin.offset(16, 0, 0)));
            context.waitTicks(20);

            shootAll(context, server, "none", scenes);
            for (var pack : packs) {
                System.out.println("[isometric-shaders] loading " + pack);
                context.runOnClient(client -> loadPack(pack));
                context.waitTicks(40);
                shootAll(context, server, pack.replaceAll("[^A-Za-z0-9.]+", "_"), scenes);
            }

            if (!packs.isEmpty()) context.runOnClient(client -> {
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

    private static List<String> copyPacks(String source) {
        try (var files = Files.list(Path.of(source))) {
            final var filter = System.getenv().getOrDefault("ISO_SHADER_FILTER", "").toLowerCase(Locale.ROOT);
            return files.filter(path -> path.toString().endsWith(".zip"))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(filter))
                    .sorted().map(path -> {
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
    }

    private record Scene(String name, BlockPos min, BlockPos max) {}

    /**
     * Pastes the schematics from {@code $ISO_BUILDS} in a row along x
     */
    private static List<Scene> pasteBuilds(TestServerContext server, BlockPos start) {
        final var source = System.getenv("ISO_BUILDS");
        if (source == null) return List.of();

        final List<Path> files;
        try (var list = Files.list(Path.of(source))) {
            files = list.filter(path -> path.toString().endsWith(".litematic")).sorted().toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final var scenes = new ArrayList<Scene>();
        var origin = start;
        for (var file : files) {
            final var min = origin;
            final BlockPos size;
            try {
                size = server.computeOnServer(minecraftServer -> Litematic.paste(minecraftServer.overworld(), file, min));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            final var name = file.getFileName().toString().replace(".litematic", "");
            System.out.println("[isometric-shaders] pasted " + name + " size " + size.toShortString());
            scenes.add(new Scene(name, min, min.offset(size).offset(-1, -1, -1)));
            origin = origin.offset(size.getX() + 8, 0, 0);
        }
        return scenes;
    }

    private static void shootAll(ClientGameTestContext context, TestServerContext server, String name, List<Scene> scenes) {
        for (var scene : scenes) shoot(context, server, name + "_" + scene.name(), scene);
    }

    private static void fill(TestServerContext server, BlockPos o, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        server.runCommand("fill " + (o.getX() + x1) + " " + (o.getY() + y1) + " " + (o.getZ() + z1) + " "
                + (o.getX() + x2) + " " + (o.getY() + y2) + " " + (o.getZ() + z2) + " " + block);
    }

    private static void shoot(ClientGameTestContext context, TestServerContext server, String name, Scene scene) {
        // Stand off the build's corner, on a barrier so the void world doesn't drop us
        final var eye = scene.max().offset(4, 4, -6);
        final var center = Vec3.atCenterOf(scene.min()).add(Vec3.atCenterOf(scene.max())).scale(.5);
        server.runCommand("setblock " + eye.getX() + " " + (eye.getY() - 1) + " " + eye.getZ() + " minecraft:barrier");
        server.runCommand("tp @p " + (eye.getX() + .5) + " " + eye.getY() + " " + (eye.getZ() + .5) + " facing " + center.x + " " + center.y + " " + center.z);

        context.setScreen(() -> null);
        context.waitTicks(20);
        System.out.println("[isometric-shaders] screenshot " + context.takeScreenshot("shaders_world_" + name));

        final var export = new AtomicReference<File>();
        context.setScreen(() -> {
            var screen = new RenderScreen(AreaRenderable.of(scene.min(), scene.max()));
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

            // Lowest profile so software rendering keeps up, unless one is asked for
            final var wanted = System.getenv("ISO_SHADER_PROFILE");
            final var lowest = new AtomicReference<Profile>();
            Iris.getCurrentPack().ifPresent(shaderPack -> shaderPack.getMenuContainer().getProfiles().forEach((name, profile) -> {
                if (wanted != null) {
                    if (name.equalsIgnoreCase(wanted)) lowest.set(profile);
                } else if (lowest.get() == null || profile.precedence < lowest.get().precedence) {
                    lowest.set(profile);
                }
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
