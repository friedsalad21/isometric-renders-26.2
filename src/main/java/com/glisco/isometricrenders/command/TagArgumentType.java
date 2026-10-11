package com.glisco.isometricrenders.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class TagArgumentType implements ArgumentType<TagArgumentType.TagArgument> {

    private static final DynamicCommandExceptionType UNKNOWN_TAG_EXCEPTION = new DynamicCommandExceptionType(
            tag -> Component.translatable("arguments.item.tag.unknown", tag)
    );

    private final HolderLookup<Item> registryWrapper;

    public TagArgumentType(CommandBuildContext registryAccess) {
        this.registryWrapper = registryAccess.lookupOrThrow(Registries.ITEM);
    }

    public static <S> TagArgument getTag(String name, CommandContext<S> context) {
        return context.getArgument(name, TagArgument.class);
    }

    @Override
    public TagArgument parse(StringReader reader) throws CommandSyntaxException {
        reader.expect('#');
        final var tagId = ResourceLocation.read(reader);
        return registryWrapper.get(TagKey.create(Registries.ITEM, tagId))
                .map(entryList -> new TagArgument(tagId, entryList))
                .orElseThrow(() -> UNKNOWN_TAG_EXCEPTION.createWithContext(reader, tagId));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(this.registryWrapper.listTags().map(named -> named.key().location()), builder, String.valueOf('#'));
    }

    public record TagArgument(ResourceLocation id, HolderSet<Item> entries) {}
}
