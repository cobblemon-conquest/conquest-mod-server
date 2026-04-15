package dev.albercl.conquestmodserver.fabric;

import dev.albercl.conquestmodserver.fabric.command.ConquestFabricCommands;
import net.fabricmc.api.ModInitializer;

public final class ConquestModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ConquestFabricCommands.register();
    }
}
