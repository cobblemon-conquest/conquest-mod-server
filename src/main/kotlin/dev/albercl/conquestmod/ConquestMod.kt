package dev.albercl.conquestmod

import dev.albercl.conquestmod.commands.LinkAccountCommand
import dev.albercl.conquestmod.config.ConfigManager
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object ConquestMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("ConquestMod")

	override fun onInitialize() {
		logger.info("Conquest Mod is initializing...")

		ConfigManager.load()
		LinkAccountCommand.register()

		logger.info("Conquest Mod has been initialized!")
	}
}