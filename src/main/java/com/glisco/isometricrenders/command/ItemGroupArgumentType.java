package com.glisco.isometricrenders.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

public class ItemGroupArgumentType implements ArgumentType<CreativeModeTab> {

    private static final DynamicCommandExceptionType NO_ITEMGROUP = new DynamicCommandExceptionType(o -> () -> "No such item group: " + o);

    private ItemGroupArgumentType() {}

    public static ItemGroupArgumentType itemGroup() {
        return new ItemGroupArgumentType();
    }

    public static <S> CreativeModeTab getItemGroup(String name, CommandContext<S> context) {
        return context.getArgument(name, CreativeModeTab.class);
    }

    @Override
    public CreativeModeTab parse(StringReader reader) throws CommandSyntaxException {
        var id = ResourceLocation.read(reader);
        return CreativeModeTabs.allTabs().stream().filter(itemGroup -> BuiltInRegistries.CREATIVE_MODE_TAB.getKey(itemGroup).equals(id)).findAny().orElseThrow(() -> NO_ITEMGROUP.create(id));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(CreativeModeTabs.allTabs().stream().map(BuiltInRegistries.CREATIVE_MODE_TAB::getKey), builder);
    }
}
