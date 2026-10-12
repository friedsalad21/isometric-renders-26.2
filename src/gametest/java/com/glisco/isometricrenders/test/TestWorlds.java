package com.glisco.isometricrenders.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;

final class TestWorlds {

    private TestWorlds() {}

    /**
     * The owner's own settings (90 FOV, GUI scale 2), so screenshots look like what players see
     */
    static void playerOptions(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.fov().set(90);
            client.options.guiScale().set(2);
            client.resizeGui();
        });
    }

    /**
     * A creative void world with nothing in it but a barrier block
     * under the player, so test builds have no terrain around them
     */
    static TestSingleplayerContext emptyCreative(ClientGameTestContext context) {
        final var world = context.worldBuilder().adjustSettings(creator -> {
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.updateDimensions((registries, dimensions) -> {
                final var voidPreset = registries.lookupOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET).getOrThrow(FlatLevelGeneratorPresets.THE_VOID);
                return dimensions.replaceOverworldGenerator(registries, new FlatLevelSource(voidPreset.value().settings()));
            });
        }).create();

        // The void preset still puts a stone platform at spawn, keep one invisible block to stand on
        world.getServer().runCommand("execute at @p run fill ~-20 ~-4 ~-20 ~20 ~-1 ~20 minecraft:air");
        world.getServer().runCommand("execute at @p run setblock ~ ~-1 ~ minecraft:barrier");
        world.getServer().runCommand("time set noon");
        return world;
    }
}
