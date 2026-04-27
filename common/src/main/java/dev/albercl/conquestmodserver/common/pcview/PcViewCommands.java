package dev.albercl.conquestmodserver.common.pcview;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.permission.CobblemonPermissions;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager;
import com.cobblemon.mod.common.api.storage.pc.link.PermissiblePcLink;
import com.cobblemon.mod.common.net.messages.client.storage.pc.OpenPCPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PcViewCommands {
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYER_SUGGESTIONS = (context, builder) -> {
        for (String playerName : context.getSource().getServer().getPlayerNames()) {
            builder.suggest(playerName);
        }
        return builder.buildFuture();
    };

    private PcViewCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("conquest")
                .then(
                    Commands.literal("pcview")
                        .requires(source -> source.hasPermission(2))
                        .then(
                            Commands.argument("player", StringArgumentType.word())
                                .suggests(ONLINE_PLAYER_SUGGESTIONS)
                                .then(
                                    Commands.argument("boxNumber", IntegerArgumentType.integer(1))
                                        .executes(PcViewCommands::openPcView)
                                )
                        )
                )
        );
    }

    private static int openPcView(CommandContext<CommandSourceStack> context) {
        ServerPlayer viewer;
        try {
            viewer = context.getSource().getPlayerOrException();
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("Este comando solo puede ser ejecutado por un jugador."));
            return 0;
        }

        String username = StringArgumentType.getString(context, "player");
        int boxNumber = IntegerArgumentType.getInteger(context, "boxNumber");

        ServerPlayer targetPlayer = context.getSource().getServer().getPlayerList().getPlayerByName(username);
        if (targetPlayer == null) {
            context.getSource().sendFailure(Component.literal("El jugador '" + username + "' no esta conectado."));
            return 0;
        }

        PCStore pcStore;
        try {
            pcStore = Cobblemon.INSTANCE.getStorage().getPC(targetPlayer);
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("No se pudo cargar el PC del jugador objetivo."));
            return 0;
        }

        int totalBoxes = pcStore.getBoxes().size();
        if (totalBoxes <= 0) {
            context.getSource().sendFailure(Component.literal("El PC de este jugador no contiene cajas disponibles."));
            return 0;
        }

        if (boxNumber > totalBoxes) {
            context.getSource().sendFailure(
                Component.literal("Numero de caja invalido. Rango permitido: 1-" + totalBoxes + ".")
            );
            return 0;
        }

        pcStore.sendTo(viewer);
        PCLinkManager.INSTANCE.addLink(new PermissiblePcLink(pcStore, viewer, CobblemonPermissions.getPC()));
        new OpenPCPacket(pcStore, boxNumber - 1).sendToPlayer(viewer);

        context.getSource().sendSuccess(
            () -> Component.literal("Abriendo PC de '" + username + "' en la caja " + boxNumber + "."),
            false
        );
        return 1;
    }
}