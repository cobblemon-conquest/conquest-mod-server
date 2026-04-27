package dev.albercl.conquestmodserver.fabric.command;

import dev.albercl.conquestmodserver.common.logging.CommandExecutionLogger;
import dev.albercl.conquestmodserver.common.pcview.PcViewCommands;
import dev.albercl.conquestmodserver.common.recovery.RecoveryCommands;
import dev.albercl.conquestmodserver.common.tournament.TournamentCommands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class ConquestFabricCommands {
    private ConquestFabricCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.setConsumer((context, success, result) -> CommandExecutionLogger.log(context, success, result));
            TournamentCommands.register(dispatcher);
            RecoveryCommands.register(dispatcher);
            PcViewCommands.register(dispatcher);
        });
    }
}

