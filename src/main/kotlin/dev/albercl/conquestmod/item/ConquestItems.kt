package dev.albercl.conquestmod.item

import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.item.PokeBallItem
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier
import net.minecraft.resources.ResourceLocation

object ConquestItems {
    const val EVENT_POKEBALL_ID = "event_pokeball"

    // Start with a direct clone of Cobblemon's standard Poke Ball behavior.
    val EVENT_POKEBALL: PokeBallItem = PokeBallItem(PokeBalls.getPokeBall())

    fun register() {
        Registry.register(
            Registries.ITEM,
            Identifier.of("cobblemon_conquest", EVENT_POKEBALL_ID),
            EVENT_POKEBALL
        )
    }
}

