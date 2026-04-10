package dev.albercl.conquestmod.common;

public final class ConquestMod {
    public static final String MOD_ID = "conquest_mod";

    public static void init() {
        ConquestPokeBalls.register();
        ConquestItems.register();
    }
}
