package dev.albercl.conquestmod.common;

import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.pokeball.PokeBall;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class ConquestItems {
    public static final String CONQUEST_BALL_ID = "conquest_ball";
    public static final String EVENT_BALL_01_ID = "event_ball_01";
    public static final String EVENT_BALL_02_ID = "event_ball_02";
    public static final String EVENT_BALL_03_ID = "event_ball_03";

    public static final PokeBallItem CONQUEST_BALL = registerPokeBallItem(CONQUEST_BALL_ID, ConquestPokeBalls.CONQUEST_BALL);
    public static final PokeBallItem EVENT_BALL_01 = registerPokeBallItem(EVENT_BALL_01_ID, ConquestPokeBalls.EVENT_BALL_01);
    public static final PokeBallItem EVENT_BALL_02 = registerPokeBallItem(EVENT_BALL_02_ID, ConquestPokeBalls.EVENT_BALL_02);
    public static final PokeBallItem EVENT_BALL_03 = registerPokeBallItem(EVENT_BALL_03_ID, ConquestPokeBalls.EVENT_BALL_03);


    private ConquestItems() {
    }

    public static void register() {
        // Class-load static fields; explicit method keeps initialization flow clear.
    }

    private static PokeBallItem registerPokeBallItem(String id, PokeBall pokeBall) {
        PokeBallItem item = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ConquestMod.MOD_ID, id),
            new PokeBallItem(pokeBall)
        );
        pokeBall.setItem$common(item);
        return item;
    }
}

