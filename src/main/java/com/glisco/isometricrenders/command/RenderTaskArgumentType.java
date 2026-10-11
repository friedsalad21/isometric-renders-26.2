package com.glisco.isometricrenders.command;

import com.glisco.isometricrenders.render.RenderTask;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class RenderTaskArgumentType implements ArgumentType<RenderTask> {

    private static final SimpleCommandExceptionType EXCEPTION = new SimpleCommandExceptionType(Component.nullToEmpty("mald about it, see if anybody notices"));

    public static <S> RenderTask getTask(String name, CommandContext<S> context) {
        return context.getArgument(name, RenderTask.class);
    }

    @Override
    public RenderTask parse(StringReader reader) throws CommandSyntaxException {
        final var first = reader.readString();
        if (first.equals("atlas")) return RenderTask.ATLAS;

        if (first.equals("batch")) {
            reader.expect(' ');
            final var second = reader.readString();

            if (second.equals("items")) return RenderTask.BATCH_ITEM;
            if (second.equals("blocks")) return RenderTask.BATCH_BLOCK;
            if (second.equals("tooltips")) return RenderTask.BATCH_TOOLTIP;
        }

        throw EXCEPTION.create();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        final var input = builder.getRemaining();

        if (input.codePoints().filter(value -> value == ' ').count() > 0 && input.contains("batch")) {
            return SharedSuggestionProvider.suggest(new String[]{"items", "blocks", "tooltips"}, builder.createOffset(builder.getStart() + 6));
        } else {
            return SharedSuggestionProvider.suggest(new String[]{"atlas", "batch"}, builder);
        }
    }

}
