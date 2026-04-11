package dev.albercl.conquestmod.fabric;

import net.fabricmc.api.ModInitializer;

import dev.albercl.conquestmod.common.ConquestMod;

public final class ConquestModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ConquestMod.init();
    }
}
