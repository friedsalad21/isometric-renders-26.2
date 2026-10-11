package com.glisco.isometricrenders.command;

import com.glisco.isometricrenders.IsometricRenders;
import com.glisco.isometricrenders.mixin.access.BlockStateArgumentAccessor;
import com.glisco.isometricrenders.mixin.access.DefaultPosArgumentAccessor;
import com.glisco.isometricrenders.property.GlobalProperties;
import com.glisco.isometricrenders.render.*;
import com.glisco.isometricrenders.screen.RenderScreen;
import com.glisco.isometricrenders.screen.ScreenScheduler;
import com.glisco.isometricrenders.util.AreaSelectionHelper;
import com.glisco.isometricrenders.util.Translate;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wispforest.owo.ui.component.EntityComponent;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.command.argument.*;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class IsorenderCommand {

    private static final SuggestionProvider<FabricClientCommandSource> CLIENT_SUMMONABLE_ENTITIES = (context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.stream().filter(EntityType::canSummon),
            builder, EntityType::getKey, entityType -> Component.translatable(Util.makeDescriptionId("entity", EntityType.getKey(entityType)))
    );

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext access) {
        dispatcher.register(literal("isorender")
                .executes(IsorenderCommand::showRootNodeHelp)
                .then(literal("area")
                        .executes(IsorenderCommand::renderAreaSelection)
                        .then(argument("start", BlockPosArgument.blockPos())
                                .then(argument("end", BlockPosArgument.blockPos())
                                        .executes(IsorenderCommand::renderAreaWithArguments))))
                .then(literal("block")
                        .executes(IsorenderCommand::renderTargetedBlock)
                        .then(argument("block", BlockStateArgument.block(access))
                                .executes(IsorenderCommand::renderBlockWithArgument)))
                .then(literal("entity")
                        .executes(IsorenderCommand::renderTargetedEntity)
                        .then(argument("entity", ResourceArgument.resource(access, Registries.ENTITY_TYPE))
                                .suggests(CLIENT_SUMMONABLE_ENTITIES)
                                .executes(IsorenderCommand::renderEntityWithoutNbt)
                                .then(argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(IsorenderCommand::renderEntityWithNbt))))
                .then(literal("player")
                        .executes(IsorenderCommand::renderSelf)
                        .then(argument("player", StringArgumentType.string())
                                .executes(IsorenderCommand::renderPlayerWithoutNbt)
                                .then(argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(IsorenderCommand::renderPlayerWithNbt))))
                .then(literal("item")
                        .executes(IsorenderCommand::renderHeldItem)
                        .then(argument("item", ItemArgument.item(access))
                                .executes(IsorenderCommand::renderItemWithArgument)))
                .then(literal("tooltip")
                        .executes(IsorenderCommand::renderHeldItemTooltip)
                        .then(argument("item", ItemArgument.item(access))
                                .executes(IsorenderCommand::renderItemTooltipWithArgument)))
                .then(literal("namespace")
                        .then(argument("namespace", NamespaceArgumentType.namespace())
                                .then(argument("task", new RenderTaskArgumentType())
                                        .executes(IsorenderCommand::renderNamespace))))
                .then(literal("creative_tab")
                        .then(argument("itemgroup", ItemGroupArgumentType.itemGroup())
                                .then(argument("task", new RenderTaskArgumentType())
                                        .executes(IsorenderCommand::renderCreativeTab))))
                .then(literal("tag")
                        .then(argument("tag", new TagArgumentType(access))
                                .then(argument("task", new RenderTaskArgumentType())
                                        .executes(IsorenderCommand::renderTagContents))))
                .then(literal("unsafe")
                        .then(literal("enable")
                                .executes(IsorenderCommand::enableUnsafe))
                        .then(literal("disable")
                                .executes(IsorenderCommand::disableUnsafe))));
    }

    private static int showRootNodeHelp(CommandContext<FabricClientCommandSource> context) {
        final var source = context.getSource();

        source.sendFeedback(Translate.prefixed(Translate.make("version", Component.literal(IsometricRenders.VERSION).withStyle(ChatFormatting.DARK_GRAY)).withStyle(ChatFormatting.GRAY)));
        source.sendFeedback(Translate.prefixed(Translate.make("command_hint").withStyle(
                style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://docs.wispforest.io/isometric-renders/slash_isorender/"))
                        .applyFormat(ChatFormatting.UNDERLINE)
                        .applyFormat(ChatFormatting.GRAY)
        )));
        return 0;
    }

    private static int disableUnsafe(CommandContext<FabricClientCommandSource> context) {
        GlobalProperties.unsafe.set(false);
        Translate.commandFeedback(context, "unsafe_disabled");
        return 0;
    }

    private static int enableUnsafe(CommandContext<FabricClientCommandSource> context) {
        GlobalProperties.unsafe.set(true);
        Translate.commandFeedback(context, "unsafe_enabled");
        return 0;
    }

    private static int renderPlayerWithNbt(CommandContext<FabricClientCommandSource> context) {
        final var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            Translate.commandError(context, "player_rendering_in_multiplayer");
            return 0;
        }

        final var gameProfile = server.getProfileCache().get(StringArgumentType.getString(context, "player"));
        if (gameProfile.isEmpty()) {
            Translate.commandError(context, "no_such_player");
            return 0;
        }

        final var playerNbt = CompoundTagArgument.getCompoundTag(context, "nbt");
        final var player = EntityComponent.createRenderablePlayer(gameProfile.get());
        ((LocalPlayer) player).load(
                playerNbt
        );

        ScreenScheduler.schedule(new RenderScreen(
                new EntityRenderable(player)
        ));

        return 0;
    }

    private static int renderPlayerWithoutNbt(CommandContext<FabricClientCommandSource> context) {
        final var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            Translate.commandError(context, "player_rendering_in_multiplayer");
            return 0;
        }

        final var gameProfile = server.getProfileCache().get(StringArgumentType.getString(context, "player"));
        if (gameProfile.isEmpty()) {
            Translate.commandError(context, "no_such_player");
            return 0;
        }

        ScreenScheduler.schedule(new RenderScreen(
                new EntityRenderable(EntityComponent.createRenderablePlayer(gameProfile.get()))
        ));

        return 0;
    }

    private static int renderSelf(CommandContext<FabricClientCommandSource> context) {
        final var player = EntityComponent.createRenderablePlayer(Minecraft.getInstance().player.getGameProfile());
        ((LocalPlayer) player).load(
                Minecraft.getInstance().player.saveWithoutId(new CompoundTag())
        );

        ScreenScheduler.schedule(new RenderScreen(
                new EntityRenderable(player)
        ));
        return 0;
    }

    private static int renderTagContents(CommandContext<FabricClientCommandSource> context) {
        final var tag = TagArgumentType.getTag("tag", context);
        RenderTaskArgumentType.getTask("task", context).action.accept(
                "tag_" + tag.id().getNamespace() + "/" + tag.id().getPath(),
                tag.entries().stream()
                        .map(Holder::value)
                        .map(Item::getDefaultInstance)
                        .toList()
        );
        return 0;
    }

    private static int renderCreativeTab(CommandContext<FabricClientCommandSource> context) {
        final var task = RenderTaskArgumentType.getTask("task", context);
        withItemGroupFromContext(context, (itemStacks, name) -> {
            task.action.accept(name, itemStacks);
        });
        return 0;
    }

    private static int renderNamespace(CommandContext<FabricClientCommandSource> context) {
        final var namespace = NamespaceArgumentType.getNamespace("namespace", context);
        RenderTaskArgumentType.getTask("task", context).action.accept("namespace_" + namespace.name(), namespace.getContent());
        return 0;
    }

    private static int renderItemWithArgument(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        ScreenScheduler.schedule(new RenderScreen(
                new ItemRenderable(ItemArgument.getItem(context, "item").createItemStack(1, false))
        ));
        return 0;
    }

    private static int renderHeldItem(CommandContext<FabricClientCommandSource> context) {
        ScreenScheduler.schedule(new RenderScreen(
                new ItemRenderable(Minecraft.getInstance().player.getMainHandItem())
        ));
        return 0;
    }

    private static int renderItemTooltipWithArgument(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        ScreenScheduler.schedule(new RenderScreen(
                new TooltipRenderable(ItemArgument.getItem(context, "item").createItemStack(1, false))
        ));
        return 0;
    }

    private static int renderHeldItemTooltip(CommandContext<FabricClientCommandSource> context) {
        ScreenScheduler.schedule(new RenderScreen(
                new TooltipRenderable(Minecraft.getInstance().player.getMainHandItem())
        ));
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static int renderEntityWithNbt(CommandContext<FabricClientCommandSource> context) {
        final var entityNbt = CompoundTagArgument.getCompoundTag(context, "nbt");
        final var entityReference = (Holder.Reference<EntityType<?>>) context.getArgument("entity", Holder.Reference.class);

        ScreenScheduler.schedule(new RenderScreen(
                EntityRenderable.of(entityReference.value(), entityNbt)
        ));

        return 0;
    }

    @SuppressWarnings("unchecked")
    private static int renderEntityWithoutNbt(CommandContext<FabricClientCommandSource> context) {
        final var entityReference = (Holder.Reference<EntityType<?>>) context.getArgument("entity", Holder.Reference.class);

        ScreenScheduler.schedule(new RenderScreen(
                EntityRenderable.of(entityReference.value(), null)
        ));

        return 0;
    }

    private static int renderTargetedEntity(CommandContext<FabricClientCommandSource> context) {
        final var client = Minecraft.getInstance();

        if (client.hitResult.getType() != HitResult.Type.ENTITY) {
            Translate.commandError(context, "no_entity");
            return 0;
        }

        final var targetEntity = ((EntityHitResult) client.hitResult).getEntity();
        ScreenScheduler.schedule(new RenderScreen(
                EntityRenderable.copyOf(targetEntity)
        ));

        return 0;
    }

    private static int renderBlockWithArgument(CommandContext<FabricClientCommandSource> context) {
        final var stateArg = context.getArgument("block", BlockInput.class);
        final var state = stateArg.getState();
        final var data = ((BlockStateArgumentAccessor) stateArg).isometric$getData();

        ScreenScheduler.schedule(new RenderScreen(
                BlockStateRenderable.of(state, data)
        ));
        return 0;
    }

    private static int renderTargetedBlock(CommandContext<FabricClientCommandSource> context) {
        final var client = Minecraft.getInstance();

        if (client.hitResult.getType() != HitResult.Type.BLOCK) {
            Translate.commandError(context, "no_block");
            return 0;
        }

        final var hitPos = ((BlockHitResult) client.hitResult).getBlockPos();
        ScreenScheduler.schedule(new RenderScreen(
                BlockStateRenderable.copyOf(client.level, hitPos)
        ));

        return 0;
    }

    private static int renderAreaWithArguments(CommandContext<FabricClientCommandSource> context) {
        final var startArg = context.getArgument("start", WorldCoordinates.class);
        final var endArg = context.getArgument("end", WorldCoordinates.class);

        final var pos1 = getPosFromArgument(startArg, context.getSource());
        final var pos2 = getPosFromArgument(endArg, context.getSource());

        ScreenScheduler.schedule(new RenderScreen(
                AreaRenderable.of(pos1, pos2)
        ));

        return 0;
    }

    private static int renderAreaSelection(CommandContext<FabricClientCommandSource> context) {
        if (!AreaSelectionHelper.tryOpenScreen()) {
            Translate.commandError(context, "incomplete_selection");
        }

        return 0;
    }

    private static <S> void withItemGroupFromContext(CommandContext<S> context, BiConsumer<List<ItemStack>, String> action) {
        final var itemGroup = ItemGroupArgumentType.getItemGroup("itemgroup", context);
        final var stacks = new ArrayList<>(itemGroup.getDisplayItems());
        action.accept(stacks, "creative-tab_" + BuiltInRegistries.CREATIVE_MODE_TAB.getKey(itemGroup).toShortLanguageKey());
    }

    public static BlockPos getPosFromArgument(WorldCoordinates argument, FabricClientCommandSource source) {

        DefaultPosArgumentAccessor accessor = (DefaultPosArgumentAccessor) argument;
        Vec3 pos = source.getPlayer().position();

        return BlockPos.containing(accessor.isometric$getX().get(pos.x), accessor.isometric$getY().get(pos.y), accessor.isometric$getZ().get(pos.z));
    }
}
