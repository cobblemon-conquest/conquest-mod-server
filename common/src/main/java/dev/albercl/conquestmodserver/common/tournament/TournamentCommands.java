package dev.albercl.conquestmodserver.common.tournament;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class TournamentCommands {
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYER_SUGGESTIONS = (context, builder) -> {
        for (String playerName : context.getSource().getServer().getPlayerNames()) {
            builder.suggest(playerName);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> TOURNAMENT_NAME_SUGGESTIONS = (context, builder) -> {
        MinecraftServer server = context.getSource().getServer();
        Set<String> suggestions = new LinkedHashSet<>();
        for (String playerName : server.getPlayerNames()) {
            suggestions.add(playerName);
        }
        suggestions.addAll(TournamentParticipantService.listParticipantDisplayNames(server));
        for (String suggestion : suggestions) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    };

    private TournamentCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("tournament")
                .then(
                    Commands.literal("participant")
                        .then(
                            Commands.literal("register")
                                .requires(source -> source.hasPermission(2))
                                .then(
                                    Commands.argument("username", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYER_SUGGESTIONS)
                                        .executes(TournamentCommands::registerParticipant)
                                )
                        )
                        .then(
                            Commands.literal("remove")
                                .requires(source -> source.hasPermission(2))
                                .then(
                                    Commands.argument("username", StringArgumentType.word())
                                        .suggests(TOURNAMENT_NAME_SUGGESTIONS)
                                        .executes(TournamentCommands::removeParticipant)
                                )
                        )
                        .then(
                            Commands.literal("check")
                                .requires(source -> source.hasPermission(2))
                                .then(
                                    Commands.argument("username", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYER_SUGGESTIONS)
                                        .executes(TournamentCommands::checkParticipant)
                                )
                        )
                        .then(
                            Commands.literal("view")
                                .requires(source -> source.hasPermission(2))
                                .then(
                                    Commands.argument("username", StringArgumentType.word())
                                        .suggests(TOURNAMENT_NAME_SUGGESTIONS)
                                        .executes(TournamentCommands::viewParticipant)
                                )
                        )
                )
                .then(
                    Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(TournamentCommands::clearTournament)
                )
        );
    }

    private static int registerParticipant(CommandContext<CommandSourceStack> context) {
        String participantName = StringArgumentType.getString(context, "username");
        MinecraftServer server = context.getSource().getServer();
        ServerPlayer player = server.getPlayerList().getPlayerByName(participantName);

        if (player == null) {
            context.getSource().sendFailure(Component.literal("El jugador '" + participantName + "' no esta conectado."));
            return 0;
        }

        try {
            TournamentParticipantService.RegisterResult result = TournamentParticipantService.register(participantName, player, server);
            context.getSource().sendSuccess(
                () -> Component.literal(
                    "Participante '" + result.participantName() + "' registrado con " + result.teamSize() + " Pokemon en equipo."
                ),
                true
            );
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int removeParticipant(CommandContext<CommandSourceStack> context) {
        String participantName = StringArgumentType.getString(context, "username");
        MinecraftServer server = context.getSource().getServer();
        boolean removed = TournamentParticipantService.remove(participantName, server);

        if (!removed) {
            context.getSource().sendFailure(Component.literal("No existe un participante registrado con ese username."));
            return 0;
        }

        context.getSource().sendSuccess(
            () -> Component.literal("Participante '" + participantName + "' eliminado."),
            true
        );
        return 1;
    }

    private static int checkParticipant(CommandContext<CommandSourceStack> context) {
        String username = StringArgumentType.getString(context, "username");
        MinecraftServer server = context.getSource().getServer();
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);

        if (player == null) {
            context.getSource().sendFailure(Component.literal("El jugador '" + username + "' no esta conectado."));
            return 0;
        }

        try {
            TournamentParticipantService.CheckResult result = TournamentParticipantService.check(player, server);
            if (result.matches()) {
                context.getSource().sendSuccess(
                    () -> Component.literal("Equipo competitivo coincide para '" + result.participantName() + "'"),
                    false
                );
                return 1;
            }

            context.getSource().sendFailure(
                Component.literal(
                    "Se detectaron " + result.differences().size() + " diferencias para '" + result.participantName() + "':"
                )
            );
            for (String difference : result.differences()) {
                context.getSource().sendFailure(Component.literal("- " + difference));
            }
            return 0;
        } catch (IllegalStateException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int viewParticipant(CommandContext<CommandSourceStack> context) {
        String username = StringArgumentType.getString(context, "username");
        MinecraftServer server = context.getSource().getServer();

        try {
            TournamentParticipantService.ViewResult result = TournamentParticipantService.view(username, server);
            sendViewLine(context, separatorLine());
            sendViewLine(context, titleLine(result.participantName()));
            sendViewLine(context, metaLine("Jugador", result.playerName()));
            sendViewLine(context, metaLine("Equipo", result.occupiedSlots() + "/6 ocupados"));
            for (TournamentParticipantService.ViewSlotResult slot : result.slots()) {
                sendViewLine(context, slot.isEmpty() ? emptySlotLine(slot.slotNumber()) : slotCardLine(slot));
            }
            sendViewLine(context, separatorLine());
            return 1;
        } catch (IllegalStateException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static void sendViewLine(CommandContext<CommandSourceStack> context, Component component) {
        context.getSource().sendSuccess(() -> component, false);
    }

    private static Component separatorLine() {
        return Component.literal("------------------------------------------------").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component titleLine(String participantName) {
        return Component.literal("✦ TORNEO | RESUMEN DEL EQUIPO ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal("  •  ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(participantName).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
    }

    private static Component metaLine(String label, String value) {
        return Component.literal("› ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static Component emptySlotLine(int slotNumber) {
        return Component.literal("  ○ ").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD)
            .append(Component.literal("Slot " + slotNumber).withStyle(ChatFormatting.GRAY))
            .append(Component.literal("  |  ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("vacío").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component slotCardLine(TournamentParticipantService.ViewSlotResult slot) {
        TournamentParticipantService.ViewPokemon pokemon = slot.pokemon();
        Component hover = slotHoverTooltip(slot.slotNumber(), pokemon);
        return coloredWithHover(
            Component.literal("  ◆ ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal("Slot " + slot.slotNumber()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  |  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(pokemon.speciesId()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Lv." + pokemon.level()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("[" + pokemon.formId() + "]").withStyle(ChatFormatting.DARK_GRAY)),
            hover
        );
    }

    private static Component slotHoverTooltip(int slotNumber, TournamentParticipantService.ViewPokemon pokemon) {
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.literal("Slot " + slotNumber).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(Component.literal("Especie: ").withStyle(ChatFormatting.GRAY).append(Component.literal(pokemon.speciesId()).withStyle(ChatFormatting.WHITE)));
        lines.add(Component.literal("Forma: ").withStyle(ChatFormatting.GRAY).append(Component.literal(pokemon.formId()).withStyle(ChatFormatting.WHITE)));
        lines.add(Component.literal("Nivel: ").withStyle(ChatFormatting.GRAY).append(Component.literal(Integer.toString(pokemon.level())).withStyle(ChatFormatting.WHITE)));
        lines.add(Component.literal("Habilidad: ").withStyle(ChatFormatting.GRAY).append(Component.literal(pokemon.abilityId()).withStyle(ChatFormatting.WHITE)));
        lines.add(Component.literal("Naturaleza: ").withStyle(ChatFormatting.GRAY).append(Component.literal(pokemon.natureId()).withStyle(ChatFormatting.WHITE)));
        lines.add(Component.literal("Objeto: ").withStyle(ChatFormatting.GRAY).append(Component.literal(pokemon.heldItemId()).withStyle(ChatFormatting.WHITE)));
        lines.add(Component.literal("Genero: ").withStyle(ChatFormatting.GRAY).append(Component.literal(pokemon.gender()).withStyle(ChatFormatting.WHITE)));
        lines.add(Component.literal("Movimientos: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.join(", ", pokemon.moveIds())).withStyle(ChatFormatting.WHITE)));
        lines.add(Component.literal("IVs: ").withStyle(ChatFormatting.GRAY).append(Component.literal(formatStats(pokemon.ivs())).withStyle(ChatFormatting.AQUA)));
        lines.add(Component.literal("EVs: ").withStyle(ChatFormatting.GRAY).append(Component.literal(formatStats(pokemon.evs())).withStyle(ChatFormatting.DARK_AQUA)));
        return joinLines(lines);
    }

    private static Component coloredWithHover(Component component, Component hoverText) {
        if (component instanceof MutableComponent mutableComponent) {
            mutableComponent.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)));
            return mutableComponent;
        }
        return component;
    }

    private static Component joinLines(List<Component> lines) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result.append(Component.literal("\n"));
            }
            result.append(lines.get(i));
        }
        return result;
    }

    private static String formatStats(int[] values) {
        return "[HP=" + values[0]
            + ", Atk=" + values[1]
            + ", Def=" + values[2]
            + ", SpA=" + values[3]
            + ", SpD=" + values[4]
            + ", Spe=" + values[5]
            + "]";
    }

    private static int clearTournament(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        TournamentParticipantService.ClearResult result = TournamentParticipantService.clear(server);
        context.getSource().sendSuccess(
            () -> Component.literal("Torneo borrado. Se eliminaron " + result.removedParticipants() + " participantes."),
            true
        );
        return 1;
    }
}
