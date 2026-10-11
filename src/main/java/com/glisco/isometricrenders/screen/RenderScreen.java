package com.glisco.isometricrenders.screen;

import com.glisco.isometricrenders.IsometricRenders;
import com.glisco.isometricrenders.mixin.access.NativeImageInvoker;
import com.glisco.isometricrenders.mixin.access.ParticleManagerAccessor;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.property.GlobalProperties;
import com.glisco.isometricrenders.property.Property;
import com.glisco.isometricrenders.render.DefaultRenderable;
import com.glisco.isometricrenders.render.Renderable;
import com.glisco.isometricrenders.render.RenderableDispatcher;
import com.glisco.isometricrenders.render.TickingRenderable;
import com.glisco.isometricrenders.util.*;
import com.glisco.isometricrenders.widget.IOStateComponent;
import com.glisco.isometricrenders.widget.NotificationComponent;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

import static com.glisco.isometricrenders.property.GlobalProperties.*;

public class RenderScreen extends BaseOwoScreen<FlowLayout> {

    private static final Int2ObjectMap<Consumer<DefaultPropertyBundle>> KEYBOARD_CONTROLS = new Int2ObjectOpenHashMap<>();

    static {
        KEYBOARD_CONTROLS.put(GLFW.GLFW_KEY_W, properties -> properties.yOffset.modify(-1000));
        KEYBOARD_CONTROLS.put(GLFW.GLFW_KEY_S, properties -> properties.yOffset.modify(1000));
        KEYBOARD_CONTROLS.put(GLFW.GLFW_KEY_D, properties -> properties.xOffset.modify(1000));
        KEYBOARD_CONTROLS.put(GLFW.GLFW_KEY_A, properties -> properties.xOffset.modify(-1000));

        KEYBOARD_CONTROLS.put(GLFW.GLFW_KEY_UP, properties -> properties.slant.modify(-5));
        KEYBOARD_CONTROLS.put(GLFW.GLFW_KEY_DOWN, properties -> properties.slant.modify(5));
        KEYBOARD_CONTROLS.put(GLFW.GLFW_KEY_LEFT, properties -> properties.rotation.modify(-10));
        KEYBOARD_CONTROLS.put(GLFW.GLFW_KEY_RIGHT, properties -> properties.rotation.modify(10));

        KEYBOARD_CONTROLS.put(GLFW.GLFW_KEY_RIGHT_BRACKET, properties -> properties.scale.modify(10));
        KEYBOARD_CONTROLS.put(GLFW.GLFW_KEY_SLASH, properties -> properties.scale.modify(-10));
    }

    private final MemoryGuard memoryGuard = new MemoryGuard(0.75f);

    private final Renderable<?> renderable;
    private Consumer<File> exportCallback = (file) -> {};

    public final Property<Boolean> playAnimations = Property.of(false);
    public final Property<Boolean> tickParticles = Property.of(true);

    private Button exportAnimationButton;

    private boolean drawOnlyBackground = false;
    private boolean captureScheduled = false;
    private boolean guiRebuildScheduled = false;

    private int viewportBeginX;
    private int viewportEndX;
    private boolean hasBothColumns = false;

    private final FlowLayout notificationArea = Containers.verticalFlow(Sizing.content(), Sizing.content());
    private final IOStateComponent ioStateComponent = new IOStateComponent();

    private final FlowLayout leftAnchor = Containers.verticalFlow(Sizing.content(), Sizing.content());
    private final FlowLayout rightAnchor = Containers.verticalFlow(Sizing.content(), Sizing.content());

    private final FlowLayout leftColumn = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
    private final FlowLayout rightColumn = Containers.verticalFlow(Sizing.fill(100), Sizing.content());

    private final List<RenderTarget> renderedFrames = new ArrayList<>();
    private int remainingAnimationFrames;

    public RenderScreen(Renderable<?> renderable) {
        this.renderable = renderable;
        this.memoryGuard.update();
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::horizontalFlow);
    }

    @Override
    protected void init() {
        this.viewportBeginX = (int) ((this.width - this.height) * 0.5);
        this.viewportEndX = (int) (this.width - (this.width - this.height) * 0.5) + 1;

        this.leftAnchor.clearChildren();
        this.rightAnchor.clearChildren();

        if (this.viewportBeginX < 200) {
            this.viewportEndX -= this.viewportBeginX;
            this.viewportBeginX = 0;
            this.hasBothColumns = false;

            this.leftAnchor.horizontalSizing(Sizing.fixed(0)).verticalSizing(Sizing.fixed(this.height));
            this.rightAnchor.positioning(Positioning.absolute(viewportEndX, 0)).horizontalSizing(Sizing.fixed(this.width - this.viewportEndX)).verticalSizing(Sizing.fixed(this.height));

            this.rightAnchor.child(
                Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), Containers.verticalFlow(Sizing.content(), Sizing.content())
                    .child(leftColumn)
                    .child(Components.box(Sizing.fill(85), Sizing.fixed(1)).color(Color.ofDye(DyeColor.GRAY)).fill(true).margins(Insets.top(15)))
                    .child(rightColumn)
                    .horizontalAlignment(HorizontalAlignment.CENTER))

            );
        } else {
            this.hasBothColumns = true;

            this.leftAnchor.horizontalSizing(Sizing.fixed(viewportBeginX)).verticalSizing(Sizing.fixed(this.height));
            this.rightAnchor.positioning(Positioning.absolute(viewportEndX, 0)).horizontalSizing(Sizing.fixed(viewportBeginX)).verticalSizing(Sizing.fixed(this.height));

            this.leftAnchor.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), this.leftColumn));
            this.rightAnchor.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), this.rightColumn));
        }

        this.notificationArea.positioning(Positioning.absolute(this.viewportBeginX + 5, 5)).sizing(Sizing.fixed(this.height - 10));

        super.init();
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);

        ((ParticleManagerAccessor) Minecraft.getInstance().particleEngine).isometric$getParticles().clear();
        IsometricRenders.particleRestriction = this.renderable.particleRestriction();

        this.leftColumn.margins(Insets.top(20));
        this.rightColumn.margins(Insets.top(20));

        rootComponent.child(leftAnchor.padding(Insets.left(10)).positioning(Positioning.absolute(0, 0)));
        rootComponent.child(rightAnchor.padding(Insets.left(10)));

        rootComponent.child(
            this.notificationArea.child(this.ioStateComponent.positioning(Positioning.relative(0, 100)))
                .horizontalAlignment(HorizontalAlignment.RIGHT)
                .verticalAlignment(VerticalAlignment.BOTTOM)
                .padding(Insets.of(5))
        );

        this.renderable.properties().buildGuiControls(this.renderable, this.leftColumn);

        // ---

        IsometricUI.sectionHeader(rightColumn, "render_options", false);

        var colorField = IsometricUI.labelledTextField(rightColumn, "#000000", "background_color", Sizing.fixed(50));
        colorField.setFilter(s -> s.matches("^#([A-Fa-f\\d]{0,6})$"));
        colorField.setValue("#" + String.format("%02X", backgroundColor >> 16) + String.format("%02X", backgroundColor >> 8 & 0xFF) + String.format("%02X", backgroundColor & 0xFF));
        colorField.moveCursorToStart(false);
        colorField.setResponder(s -> {
            if (s.substring(1).length() < 6) return;
            backgroundColor = Integer.parseInt(s.substring(1), 16);
        });

        IsometricUI.booleanControl(rightColumn, this.playAnimations, "animations");
        IsometricUI.booleanControl(rightColumn, this.tickParticles, "particles");

        IsometricUI.sectionHeader(rightColumn, "export_options", true);
        IsometricUI.booleanControl(rightColumn, saveIntoRoot, "dump_into_root");
        IsometricUI.booleanControl(rightColumn, overwriteLatest, "overwrite_latest");

        final Button exportButton;
        try (var builder = IsometricUI.row(rightColumn)) {
            exportButton = Components.button(Translate.gui("export"), button -> this.captureScheduled = true);
            builder.row.child(exportButton.horizontalSizing(Sizing.fixed(75)));

            builder.row.child(Components.button(Translate.gui("open_folder"), button -> {
                Util.getPlatform().openFile(this.renderable.exportPath().resolveOffset().toFile());
            }).horizontalSizing(Sizing.fixed(75)).margins(Insets.left(5)));
        }

        if (!GraphicsEnvironment.isHeadless()) {
            rightColumn.child(Components.button(Translate.gui("export_to_clipboard"), button -> {

                this.notify(Translate.gui("copied_to_clipboard"));

                try (var image = RenderableDispatcher.drawIntoImage(this.renderable, 0, exportResolution)) {
                    var stream = new ByteArrayOutputStream();
                    var channel = Channels.newChannel(stream);

                    ((NativeImageInvoker) (Object) image).isometric$write(channel);

                    final var transferable = new ImageTransferable(javax.imageio.ImageIO.read(new ByteArrayInputStream(stream.toByteArray())));
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, transferable);
                } catch (IOException e) {
                    IsometricRenders.LOGGER.error("mfw", e);
                }

            }).horizontalSizing(Sizing.fixed(75)));
        }

        var resolutionField = IsometricUI.labelledTextField(rightColumn, String.valueOf(exportResolution), "renderer_resolution", Sizing.fixed(50));

        resolutionField.setTextColor(0x00FF00);
        resolutionField.setFilter(s -> s.matches("\\d{0,5}"));
        resolutionField.setResponder(s -> {
            if (s.isBlank()) return;
            int resolution = Integer.parseInt(s);

            // Exhibit A
            // ((resolution & (resolution - 1)) != 0)
            // what?

            if ((resolution < 16 || resolution > 16384) && !unsafe.get()) {
                resolutionField.setTextColor(0xFF0000);
                exportButton.active = false;
            } else {
                resolutionField.setTextColor(0x00FF00);
                exportResolution = resolution;
                exportButton.active = true;
            }
        });

        IsometricUI.sectionHeader(rightColumn, "animation_options", true);

        if (FFmpegDispatcher.wasFFmpegDetected()) {
            if (FFmpegDispatcher.ffmpegAvailable()) {
                var framesField = IsometricUI.labelledTextField(rightColumn, String.valueOf(exportFrames), "animation_frames", Sizing.fixed(30));
                framesField.setFilter(s -> s.matches("\\d*"));
                framesField.setResponder(s -> {
                    if (s.isBlank()) return;
                    exportFrames = Integer.parseInt(s);
                });

                var framerateField = IsometricUI.labelledTextField(rightColumn, String.valueOf(exportFramerate), "animation_framerate", Sizing.fixed(30));
                framerateField.setFilter(s -> s.matches("\\d*"));
                framerateField.setResponder(s -> {
                    if (s.isBlank()) return;
                    exportFramerate = Integer.parseInt(s);
                });

                try (var builder = IsometricUI.row(rightColumn)) {
                    this.exportAnimationButton = Components.button(Translate.gui("export_animation"), button -> {
                        if (this.memoryGuard.canFit(this.estimateMemoryUsage(exportFrames)) || Screen.hasShiftDown()) {
                            this.remainingAnimationFrames = exportFrames;

                            this.minecraft.getFramerateLimitTracker().setFramerateLimit(Integer.parseInt(framerateField.getValue()));
                            IsometricRenders.skipNextWorldRender();

                            button.active = false;
                            button.setMessage(Translate.gui("exporting"));
                        }
                    });
                    builder.row.child(this.exportAnimationButton.horizontalSizing(Sizing.fixed(100)).margins(Insets.right(5)));

                    builder.row.child(Components.button(Translate.gui("format." + animationFormat.extension), button -> {
                        animationFormat = animationFormat.next();
                        button.setMessage(Translate.gui("format." + animationFormat.extension));
                    }).horizontalSizing(Sizing.fixed(35)));
                }

                IsometricUI.dynamicLabel(rightColumn, () -> {
                    return this.remainingAnimationFrames == 0
                        ? Component.empty()
                        : Translate.gui("export_remaining_frames", this.remainingAnimationFrames);
                });
            } else {
                IsometricUI.sectionHeader(rightColumn, "no_ffmpeg_1", true);
                IsometricUI.sectionHeader(rightColumn, "no_ffmpeg_2", false);
                IsometricUI.sectionHeader(rightColumn, "no_ffmpeg_3", false)
                    .cursorStyle(CursorStyle.HAND)
                    .mouseDown().subscribe((mouseX, mouseY, button) -> {
                        this.minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
                            if (confirmed) {
                                Util.getPlatform().openUri("https://ffmpeg.org/download.html");
                            }

                            this.minecraft.setScreen(this);
                        }, "https://ffmpeg.org/download.html", true));
                        return true;
                    });
            }
        } else {
            IsometricUI.sectionHeader(rightColumn, "detecting_ffmpeg", false);
            FFmpegDispatcher.detectFFmpeg().whenComplete((aBoolean, throwable) -> {
                this.guiRebuildScheduled = true;
            });
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {

        if (this.guiRebuildScheduled) {
            this.guiRebuildScheduled = false;

            this.uiAdapter = null;
            this.rightColumn.clearChildren();
            this.leftColumn.clearChildren();

            this.rebuildWidgets();
        }

        if (this.drawOnlyBackground) {
            context.fill(0, 0, this.width, this.height, GlobalProperties.backgroundColor | 255 << 24);
        } else {
            this.renderTransparentBackground(context);
        }

        context.flush();

        final var window = minecraft.getWindow();
        final var effectiveTickDelta = playAnimations.get() ? minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false) : 0;
        RenderableDispatcher.drawIntoActiveFramebuffer(
            this.renderable,
            window.getWidth() / (float) window.getHeight(),
            effectiveTickDelta,
            this.hasBothColumns
                ? matrixStack -> {}
                : matrixStack -> matrixStack.translate(1 - window.getWidth() / (float) window.getHeight(), 0, 0)
        );

        if (!this.drawOnlyBackground && this.uiAdapter != null) {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT);

            drawFramingHint(context);
            drawGuiBackground(context);

            super.render(context, mouseX, mouseY, delta);

            if (this.exportAnimationButton != null) {
                this.exportAnimationButton.tooltip(this.memoryGuard.getStatusTooltip(this.estimateMemoryUsage(exportFrames)).stream().map(text -> ClientTooltipComponent.create(text.getVisualOrderText())).toList());
            }

//            fill(matrices, viewportEndX + 160, 45, viewportEndX + 168, 53, GlobalProperties.backgroundColor | 255 << 24);

//            client.textRenderer.draw(matrices, Translate.gui("hotkeys"), viewportEndX + 12, height - 20, 0xAAAAAA);
//
//            client.textRenderer.draw(matrices, Translate.gui("memory_warning1"), 10, height - 60, 0xAAAAAA);
//            client.textRenderer.draw(matrices, Translate.gui("memory_warning2"), 10, height - 50, 0xAAAAAA);
//            client.textRenderer.draw(matrices, Translate.gui("memory_warning3"), 10, height - 40, 0xAAAAAA);
//            client.textRenderer.draw(matrices, Translate.gui("memory_warning4"), 10, height - 30, 0xAAAAAA);
//            client.textRenderer.draw(matrices, Translate.gui("memory_warning5"), 10, height - 20, 0xAAAAAA);

            if (ImageIO.taskCount() > 0) {
                if (!this.ioStateComponent.hasParent()) {
                    this.notificationArea.child(this.ioStateComponent);
                }
            } else if (this.ioStateComponent.hasParent()) {
                this.notificationArea.removeChild(this.ioStateComponent);
            }
        }

        if (this.captureScheduled) {
            ImageIO.save(
                RenderableDispatcher.drawIntoImage(this.renderable, 0, exportResolution),
                this.renderable.exportPath()
            ).whenComplete((file, throwable) -> {
                exportCallback.accept(file);
                this.minecraft.execute(() -> this.notify(
                    () -> Util.getPlatform().openFile(file),
                    Translate.gui("exported_as"),
                    Component.literal(ExportPathSpec.exportRoot().relativize(file.toPath()).toString())
                ));
            });

            this.captureScheduled = false;
        }

        if (this.remainingAnimationFrames > 0) {
            this.renderedFrames.add(RenderableDispatcher.drawIntoTexture(this.renderable, effectiveTickDelta, exportResolution));

            IsometricRenders.skipNextWorldRender();

            if (--this.remainingAnimationFrames == 0) {
                this.minecraft.getFramerateLimitTracker().setFramerateLimit(this.minecraft.options.framerateLimit().get());

                final var overwriteValue = overwriteLatest.get();
                overwriteLatest.set(false);

                CompletableFuture<File> exportFuture = null;

                for (int i = 0; i < this.renderedFrames.size(); i++) {
                    exportFuture = ImageIO.save(
                        RenderableDispatcher.copyFramebufferIntoImage(this.renderedFrames.get(i)),
                        ExportPathSpec.forced("sequence", "seq_" + i)
                    );
                    this.renderedFrames.get(i).destroyBuffers();
                }

                this.renderedFrames.clear();

                exportFuture.whenComplete((file, throwable) -> {
                    overwriteLatest.set(overwriteValue);
                    if (throwable != null) return;

                    this.exportAnimationButton.setMessage(Translate.gui("converting"));
                    this.minecraft.execute(() -> this.notify(Translate.gui("converting_image_sequence")));

                    FFmpegDispatcher.assemble(
                        this.renderable.exportPath(),
                        ExportPathSpec.exportRoot().resolve("sequence/"),
                        animationFormat
                    ).whenComplete((animationFile, animationThrowable) -> {
                        this.exportAnimationButton.active = true;
                        this.exportAnimationButton.setMessage(Translate.gui("export_animation"));

                        this.minecraft.execute(() -> this.notify(
                            () -> Util.getPlatform().openFile(animationFile),
                            Translate.gui("animation_saved"),
                            Component.literal(ExportPathSpec.exportRoot().relativize(animationFile.toPath()).toString())
                        ));
                    });
                });
            }
        }
    }

    @Override
    public void tick() {
        if (this.minecraft.level.getGameTime() % 40 == 0) {
            this.memoryGuard.update();
        }

        if (playAnimations.get() && this.renderable instanceof TickingRenderable<?> tickable) {
            IsometricRenders.beginRenderableTick();
            tickable.tick();
            IsometricRenders.endRenderableTick();
        }
    }

    private void notify(@NotNull Runnable onClick, Component... messages) {
        this.notificationArea.child(0, new NotificationComponent(onClick, messages));
    }

    private void notify(Component... messages) {
        this.notificationArea.child(0, new NotificationComponent(null, messages));
    }

    private boolean isInViewport(double mouseX) {
        return mouseX > viewportBeginX && mouseX < viewportEndX;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!(this.renderable.properties() instanceof DefaultPropertyBundle properties)) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);

        if (this.isInViewport(mouseX)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                double xScaling = (100d / properties.scale.get()) * (this.minecraft.getWindow().getScreenWidth() / (float) this.minecraft.getWindow().getGuiScaledWidth());
                double yScaling = (100d / properties.scale.get()) * (this.minecraft.getWindow().getScreenHeight() / (float) this.minecraft.getWindow().getGuiScaledHeight());

                properties.xOffset.modify((int) (50 * deltaX * xScaling));
                properties.yOffset.modify((int) (50 * deltaY * yScaling));
                return true;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                properties.rotation.modify((int) (deltaX * 2));
                return true;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                properties.slant.modify((int) (deltaY * 2));
                return true;
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!(this.renderable.properties() instanceof DefaultPropertyBundle properties)) return super.mouseClicked(mouseX, mouseY, button);

        if (this.isInViewport(mouseX) && Screen.hasControlDown()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                properties.xOffset.setToDefault();
                properties.yOffset.setToDefault();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                properties.rotation.setToDefault();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                properties.slant.setToDefault();
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!(this.renderable.properties() instanceof DefaultPropertyBundle properties)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (this.isInViewport(mouseX)) {
            properties.scale.modify((int) (verticalAmount * Math.max(1, properties.scale.get() * 0.075)));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;

        if (keyCode == GLFW.GLFW_KEY_F12) {
            this.captureScheduled = true;
        } else if (keyCode == GLFW.GLFW_KEY_F10) {
            this.drawOnlyBackground = !this.drawOnlyBackground;
        } else if (KEYBOARD_CONTROLS.containsKey(keyCode) && this.renderable instanceof DefaultRenderable) {
            KEYBOARD_CONTROLS.get(keyCode).accept((DefaultPropertyBundle) this.renderable.properties());
        }
        return true;
    }

    private int estimateMemoryUsage(int frames) {
        return (int) ((exportResolution * exportResolution * 4L * frames) / 1024L / 1024L);
    }

    public void scheduleCapture() {
        this.captureScheduled = true;
    }

    public void setExportCallback(Consumer<File> exportCallback) {
        this.exportCallback = exportCallback;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        this.renderable.dispose();
        IsometricRenders.particleRestriction = ParticleRestriction.always();
        this.minecraft.getFramerateLimitTracker().setFramerateLimit(this.minecraft.options.framerateLimit().get());
    }

    private void drawFramingHint(GuiGraphics context) {
        context.fill(viewportBeginX + 5, 0, viewportEndX - 5, 5, 0x90000000);
        context.fill(viewportBeginX + 5, height - 5, viewportEndX - 5, height, 0x90000000);
        context.fill(viewportBeginX, 0, viewportBeginX + 5, height, 0x90000000);
        context.fill(viewportEndX - 5, 0, viewportEndX, height, 0x90000000);
    }

    private void drawGuiBackground(GuiGraphics context) {
        context.fill(0, 0, viewportBeginX, height, 0x90000000);
        context.fill(viewportEndX, 0, width, height, 0x90000000);
    }
}
