package dev.albercl.conquestmod.common.fabric;

import net.fabricmc.api.ModInitializer;

import dev.albercl.conquestmod.common.ConquestMod;

public final class ConquestModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ConquestMod.init();
    }
}
