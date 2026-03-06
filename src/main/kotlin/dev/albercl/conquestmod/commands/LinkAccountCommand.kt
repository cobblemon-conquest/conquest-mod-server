package dev.albercl.conquestmod.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.albercl.conquestmod.config.ConfigManager
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Texts
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object LinkAccountCommand {

    private val logger = LoggerFactory.getLogger("ConquestMod")

    val httpClient: HttpClient = HttpClient.newHttpClient();

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(CommandManager.literal("link")
                .requires { it.hasPermissionLevel(0) }
                .then(CommandManager.argument("code", StringArgumentType.word())
                    .executes { commandExecution(it) }

                )
            )
        }
    }

    private fun commandExecution(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player

        if(!source.isExecutedByPlayer || player == null) {
            source.sendError(Texts.toText { "Only players can execute this command." })
            return 0
        }

        val code = StringArgumentType.getString(context, "code")
        val playerUuid = player.uuidAsString
        val playerName = player.name.string

        val body = """
            {
                "linkingCode": "$code",
                "minecraftUuid": "$playerUuid",
                "minecraftName": "$playerName"
            }
        """.trimIndent()

        source.sendFeedback( { Texts.toText { "Linking account with code: $code" } }, false)
        logger.info("Sending account link request for player {} with code {}", playerName, code)

        val request = HttpRequest.newBuilder(URI.create(ConfigManager.config.endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(ConfigManager.config.timeoutSeconds))
            .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept {
                if (it.statusCode() in 200..<300) {
                    logger.info("Successfully linked account linked for player {}", player.name)
                    source.sendFeedback( { Texts.toText { "Account linked successfully!" } }, false)
                } else {
                    logger.error("Received response status {}. Body: \n{}", it.statusCode(), it.body())
                    source.sendError(Texts.toText { "Failed to link account. Please check the code and try again." })
                }
            }
            .exceptionally {
                logger.error("Error while linking account for player {}", player.name, it)
                source.sendError(Texts.toText { "An error occurred while linking the account. Please try again later." })
                null
            }

        return 1
    }
}