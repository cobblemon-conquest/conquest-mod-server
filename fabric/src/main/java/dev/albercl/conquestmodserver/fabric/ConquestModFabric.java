package dev.albercl.conquestmod.fabric;

import net.fabricmc.api.ModInitializer;

import dev.albercl.conquestmod.fabric.command.ConquestFabricCommands;

public final class ConquestModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ConquestFabricCommands.register();
    }
}
