package dev.albercl.conquestmodserver.fabric.command;

import dev.albercl.conquestmodserver.common.tournament.TournamentCommands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class ConquestFabricCommands {
    private ConquestFabricCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TournamentCommands.register(dispatcher));
    }
}

