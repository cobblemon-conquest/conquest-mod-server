package dev.albercl.conquestmodserver.common.recovery;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RecoveryCommands {
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYER_SUGGESTIONS = (context, builder) -> {
        for (String playerName : context.getSource().getServer().getPlayerNames()) {
            builder.suggest(playerName);
        }
        return builder.buildFuture();
    };

    private RecoveryCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("conquest")
                .then(
                    Commands.literal("pokegive")
                        .requires(source -> source.hasPermission(2))
                        .then(
                            Commands.argument("username", StringArgumentType.word())
                                .suggests(ONLINE_PLAYER_SUGGESTIONS)
                                .then(
                                    Commands.argument("pokemonData", StringArgumentType.greedyString())
                                        .executes(RecoveryCommands::pokeGive)
                                )
                        )
                )
        );
    }

    private static int pokeGive(CommandContext<CommandSourceStack> context) {
        String username = StringArgumentType.getString(context, "username");
        String rawData = StringArgumentType.getString(context, "pokemonData").trim();

        ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayerByName(username);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("El jugador '" + username + "' no esta conectado."));
            return 0;
        }

        if (rawData.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Debes proporcionar datos JSON/NBT del Pokemon."));
            return 0;
        }

        ParseResult parseResult = parsePokemon(rawData, context.getSource());
        if (parseResult.error() != null) {
            context.getSource().sendFailure(Component.literal(parseResult.error()));
            return 0;
        }

        Pokemon pokemon = parseResult.pokemon();
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int occupiedBefore = party.occupied();
        boolean added = party.add(pokemon);

        if (!added) {
            context.getSource().sendFailure(
                Component.literal("No se pudo entregar el Pokemon: no hay espacio ni en equipo ni en PC.")
            );
            return 0;
        }

        int occupiedAfter = party.occupied();
        String destination = occupiedAfter > occupiedBefore ? "equipo" : "PC";
        String format = parseResult.usedNbtFormat() ? "NBT" : "JSON";

        context.getSource().sendSuccess(
            () -> Component.literal(
                "Pokemon restaurado para '" + username + "' desde formato " + format
                    + ". Especie: " + pokemon.getSpecies().showdownId()
                    + ", UUID: " + pokemon.getUuid()
                    + ", destino: " + destination + "."
            ),
            true
        );
        return 1;
    }

    private static ParseResult parsePokemon(String rawData, CommandSourceStack source) {
        try {
            JsonElement root = JsonParser.parseString(rawData);
            if (!root.isJsonObject()) {
                return ParseResult.error("El JSON debe ser un objeto con los datos completos del Pokemon.");
            }

            JsonObject json = unwrapJsonPokemonRoot(root.getAsJsonObject());
            Pokemon pokemon = Pokemon.Companion.loadFromJSON(source.registryAccess(), json);
            return ParseResult.success(pokemon, false);
        } catch (Exception jsonException) {
            try {
                CompoundTag tag = unwrapNbtPokemonRoot(TagParser.parseTag(rawData));
                Pokemon pokemon = Pokemon.Companion.loadFromNBT(source.registryAccess(), tag);
                return ParseResult.success(pokemon, true);
            } catch (Exception nbtException) {
                String jsonMessage = safeMessage(jsonException);
                String nbtMessage = safeMessage(nbtException);
                return ParseResult.error(
                    "No se pudo parsear el Pokemon. Intenta con el JSON serializado por Cobblemon o con SNBT. "
                        + "Error JSON: " + jsonMessage + " | Error NBT: " + nbtMessage
                );
            }
        }
    }

    private static JsonObject unwrapJsonPokemonRoot(JsonObject json) {
        if (json.has("pokemon") && json.get("pokemon").isJsonObject()) {
            return json.getAsJsonObject("pokemon");
        }
        return json;
    }

    private static CompoundTag unwrapNbtPokemonRoot(CompoundTag tag) {
        if (tag.contains("pokemon", CompoundTag.TAG_COMPOUND)) {
            return tag.getCompound("pokemon");
        }
        return tag;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record ParseResult(Pokemon pokemon, boolean usedNbtFormat, String error) {
        private static ParseResult success(Pokemon pokemon, boolean usedNbtFormat) {
            return new ParseResult(pokemon, usedNbtFormat, null);
        }

        private static ParseResult error(String error) {
            return new ParseResult(null, false, error);
        }
    }
}
