package dev.albercl.conquestmod.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.jvm.java

object ConfigManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path: Path =
        FabricLoader.getInstance().configDir.resolve("conquestmod.json")

    lateinit var config: Config
        private set

    fun load() {
        if (Files.notExists(path)) {
            config = Config()
            save()
        } else {
            Files.newBufferedReader(path).use {
                config = gson.fromJson(it, Config::class.java)
            }
        }
    }

    private fun save() {
        Files.newBufferedWriter(path).use {
            gson.toJson(config, it)
        }
    }
}