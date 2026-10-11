package com.glisco.isometricrenders;

import com.glisco.isometricrenders.command.IsorenderCommand;
import com.glisco.isometricrenders.util.AreaSelectionHelper;
import com.glisco.isometricrenders.util.ImageIO;
import com.glisco.isometricrenders.util.ParticleRestriction;
import com.glisco.isometricrenders.widget.AreaSelectionComponent;
import com.glisco.isometricrenders.widget.IOStateComponent;
import com.mojang.blaze3d.pipeline.RenderTarget;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.hud.Hud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class IsometricRenders implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("isometric-renders");
    public static final String VERSION = FabricLoader.getInstance().getModContainer("isometric-renders").get().getMetadata().getVersion().getFriendlyString();

    public static ParticleRestriction<?> particleRestriction = ParticleRestriction.always();

    public static boolean inRenderableDraw = false;
    public static boolean inRenderableTick = false;
    public static boolean skipWorldRender = false;
    public static boolean centerNextTooltip = false;

    public static RenderTarget mainTargetOverride = null;

    public static final KeyMapping SELECT = new KeyMapping("key.isometric-renders.area_select", GLFW.GLFW_KEY_C, KeyMapping.CATEGORY_MISC);

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(IsorenderCommand::register);

        KeyMappingHelper.registerKeyBinding(SELECT);

        final var ioStateId = "io-state";
        final var areaSelectionHintId = "area-selection-hint";

        var hudId = Identifier.fromNamespaceAndPath("isometric-renders", "hud");
        Hud.add(hudId, () -> UIContainers.verticalFlow(Sizing.content(), Sizing.content()).positioning(Positioning.absolute(20, 20)));

        HudRenderCallback.EVENT.register((matrixStack, tickDelta) -> {
            var client = Minecraft.getInstance();
            var isometricHud = (FlowLayout) Hud.getComponent(hudId);

            final var ioState = isometricHud.childById(IOStateComponent.class, ioStateId);
            if ((ioState == null) == (ImageIO.taskCount() > 0 && client.screen == null)) {
                if (ImageIO.taskCount() > 0 && client.screen == null) {
                    isometricHud.child(new IOStateComponent().positioning(Positioning.absolute(20, 20)).id(ioStateId));
                } else {
                    isometricHud.removeChild(ioState);
                }
            }

            final var selectionHint = isometricHud.childById(AreaSelectionComponent.class, areaSelectionHintId);
            if ((selectionHint == null) == AreaSelectionHelper.shouldDraw()) {
                if (AreaSelectionHelper.shouldDraw()) {
                    isometricHud.child(new AreaSelectionComponent().id(areaSelectionHintId));
                } else {
                    isometricHud.removeChild(selectionHint);
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (SELECT.consumeClick()) {
                if (client.player.isShiftKeyDown()) {
                    AreaSelectionHelper.clear();
                } else {
                    AreaSelectionHelper.select();
                }
            }
        });
    }

    public static void skipNextWorldRender() {
        skipWorldRender = true;
    }

    public static void beginRenderableDraw(){
        inRenderableDraw = true;
    }

    public static void endRenderableDraw(){
        inRenderableDraw = false;
    }

    public static void beginRenderableTick() {
        inRenderableTick = true;
    }

    public static void endRenderableTick() {
        inRenderableTick = false;
    }
}
