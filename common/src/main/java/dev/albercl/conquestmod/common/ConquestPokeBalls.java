package dev.albercl.conquestmod.common;

import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.pokeball.PokeBall;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

import static dev.albercl.conquestmod.common.ConquestItems.*;

public final class ConquestPokeBalls {
    public static final PokeBall CONQUEST_BALL = createFromCherishBall(CONQUEST_BALL_ID);
    public static final PokeBall EVENT_BALL_01 = createFromCherishBall(EVENT_BALL_01_ID);
    public static final PokeBall EVENT_BALL_02 = createFromCherishBall(EVENT_BALL_02_ID);
    public static final PokeBall EVENT_BALL_03 = createFromCherishBall(EVENT_BALL_03_ID);


    private ConquestPokeBalls() {
    }

    public static void register() {
        registerInCobblemonRegistry(CONQUEST_BALL);
        registerInCobblemonRegistry(EVENT_BALL_01);
        registerInCobblemonRegistry(EVENT_BALL_02);
        registerInCobblemonRegistry(EVENT_BALL_03);
    }

    private static PokeBall createFromCherishBall(String id) {
        PokeBall cherish = PokeBalls.getCherishBall();
        ResourceLocation conquestBallId = ResourceLocation.fromNamespaceAndPath(ConquestMod.MOD_ID, id);

        return new PokeBall(
            conquestBallId,
            cherish.getCatchRateModifier(),
            cherish.getEffects(),
            cherish.getWaterDragValue(),
            conquestBallId,
            ResourceLocation.fromNamespaceAndPath(ConquestMod.MOD_ID, "item/" + id + "_model"),
            cherish.getThrowPower(),
            cherish.getAncient()
        );
    }

    @SuppressWarnings("unchecked")
    private static void registerInCobblemonRegistry(PokeBall pokeBall) {
        try {
            Field defaultsField = PokeBalls.class.getDeclaredField("defaults");
            defaultsField.setAccessible(true);
            Map<ResourceLocation, PokeBall> defaults = (Map<ResourceLocation, PokeBall>) defaultsField.get(PokeBalls.INSTANCE);
            defaults.putIfAbsent(pokeBall.getName(), pokeBall);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not register custom pokeball in Cobblemon registry", exception);
        }
    }
}


