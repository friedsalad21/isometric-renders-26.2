package com.glisco.isometricrenders.test;

import com.glisco.isometricrenders.render.*;
import com.glisco.isometricrenders.screen.RenderScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Opens a render screen for each renderable type and screenshots it, plus one real export
 */
public class IsometricClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(40);

            final BlockPos origin = context.computeOnClient(client -> client.player.blockPosition()).offset(3, 0, 0);
            final var server = world.getServer();
            final var o = origin.getX() + " " + origin.getY() + " " + origin.getZ();
            final var e = (origin.getX() + 4) + " " + (origin.getY() + 3) + " " + (origin.getZ() + 4);
            server.runCommand("fill " + o + " " + e + " minecraft:air");
            server.runCommand("fill " + o + " " + (origin.getX() + 4) + " " + origin.getY() + " " + (origin.getZ() + 4) + " minecraft:stone_bricks");
            server.runCommand("setblock " + (origin.getX() + 1) + " " + (origin.getY() + 1) + " " + (origin.getZ() + 1) + " minecraft:chest");
            server.runCommand("setblock " + (origin.getX() + 3) + " " + (origin.getY() + 1) + " " + (origin.getZ() + 1) + " minecraft:glass");
            server.runCommand("setblock " + (origin.getX() + 2) + " " + (origin.getY() + 1) + " " + (origin.getZ() + 3) + " minecraft:water");
            server.runCommand("setblock " + (origin.getX() + 3) + " " + (origin.getY() + 1) + " " + (origin.getZ() + 3) + " minecraft:oak_leaves");
            server.runCommand("summon minecraft:pig " + (origin.getX() + 1.5) + " " + (origin.getY() + 1) + " " + (origin.getZ() + 3.5) + " {NoAI:1b}");
            context.waitTicks(20);

            shot(context, "item_sword", () -> new ItemRenderable(new ItemStack(Items.DIAMOND_SWORD)));
            shot(context, "item_grass", () -> new ItemRenderable(new ItemStack(Items.GRASS_BLOCK)));
            shot(context, "block_furnace", () -> BlockStateRenderable.of(Blocks.FURNACE));
            shot(context, "block_chest", () -> BlockStateRenderable.of(Blocks.CHEST));
            shot(context, "entity_zombie", () -> EntityRenderable.of(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("zombie")), null));
            shot(context, "tooltip_sword", () -> new TooltipRenderable(new ItemStack(Items.DIAMOND_SWORD)));
            shot(context, "area", () -> AreaRenderable.of(origin, origin.offset(4, 3, 4)));
            // Layers of stacked translucent blocks to check sorting
            final var glass = origin.offset(0, 0, 8);
            server.runCommand("fill " + glass.getX() + " " + glass.getY() + " " + glass.getZ() + " " + (glass.getX() + 6) + " " + (glass.getY() + 3) + " " + (glass.getZ() + 3) + " minecraft:air");
            server.runCommand("fill " + glass.getX() + " " + glass.getY() + " " + glass.getZ() + " " + (glass.getX() + 6) + " " + glass.getY() + " " + (glass.getZ() + 3) + " minecraft:stone_bricks");
            server.runCommand("fill " + glass.getX() + " " + (glass.getY() + 1) + " " + glass.getZ() + " " + (glass.getX() + 6) + " " + (glass.getY() + 1) + " " + glass.getZ() + " minecraft:light_blue_stained_glass");
            server.runCommand("fill " + glass.getX() + " " + (glass.getY() + 1) + " " + (glass.getZ() + 2) + " " + (glass.getX() + 6) + " " + (glass.getY() + 2) + " " + (glass.getZ() + 2) + " minecraft:orange_stained_glass");
            server.runCommand("fill " + glass.getX() + " " + (glass.getY() + 1) + " " + (glass.getZ() + 3) + " " + (glass.getX() + 6) + " " + (glass.getY() + 1) + " " + (glass.getZ() + 3) + " minecraft:redstone_block");
            server.runCommand("fill " + (glass.getX() + 2) + " " + (glass.getY() + 1) + " " + (glass.getZ() + 1) + " " + (glass.getX() + 4) + " " + (glass.getY() + 1) + " " + (glass.getZ() + 1) + " minecraft:water");
            context.waitTicks(10);

            final var glassExport = new AtomicReference<File>();
            context.setScreen(() -> {
                var screen = new RenderScreen(AreaRenderable.of(glass, glass.offset(6, 3, 3)));
                screen.setExportCallback(glassExport::set);
                return screen;
            });
            context.waitTicks(40);
            context.runOnClient(client -> {
                com.glisco.isometricrenders.property.GlobalProperties.exportResolution = 4096;
                ((RenderScreen) client.gui.screen()).scheduleCapture();
            });
            context.waitFor(client -> glassExport.get() != null, 200);
            System.out.println("[isometric-gametest] exported " + glassExport.get().getAbsolutePath());
            context.runOnClient(client -> com.glisco.isometricrenders.property.GlobalProperties.exportResolution = 1000);

            shot(context, "atlas", () -> new ItemAtlasRenderable("test", java.util.List.of(new ItemStack(Items.APPLE), new ItemStack(Items.STONE), new ItemStack(Items.BOW), new ItemStack(Items.CHEST), new ItemStack(Items.SHIELD))));

            context.setScreen(() -> null);
            context.runOnClient(client -> {
                com.glisco.isometricrenders.util.AreaSelectionHelper.pos1 = origin;
                com.glisco.isometricrenders.util.AreaSelectionHelper.pos2 = origin.offset(4, 3, 4);
            });
            context.waitTicks(10);
            System.out.println("[isometric-gametest] screenshot " + context.takeScreenshot("selection_box"));

            // A real export through the async readback path
            final var exported = new AtomicReference<File>();
            context.setScreen(() -> {
                var screen = new RenderScreen(BlockStateRenderable.of(Blocks.CRAFTING_TABLE));
                screen.setExportCallback(exported::set);
                screen.scheduleCapture();
                return screen;
            });
            context.waitFor(client -> exported.get() != null, 200);
            System.out.println("[isometric-gametest] exported " + exported.get().getAbsolutePath() + " size " + exported.get().length());

            context.setScreen(() -> null);
        }
    }

    private static void shot(ClientGameTestContext context, String name, Supplier<Renderable<?>> renderable) {
        context.setScreen(() -> new RenderScreen(renderable.get()));
        context.waitTicks(30);
        System.out.println("[isometric-gametest] screenshot " + context.takeScreenshot(name));
    }
}
