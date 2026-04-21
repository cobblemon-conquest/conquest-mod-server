package dev.albercl.conquestmodserver.common.logging;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommandExecutionLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConquestModServer/CommandAudit");

    private CommandExecutionLogger() {
    }

    public static void log(CommandContext<CommandSourceStack> context, boolean success, int result) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        String username = player.getGameProfile().getName();
        String uuid = player.getUUID().toString();
        String commandInput = context.getInput();
        String commandPath = buildCommandPath(context);
        String dimension = source.getLevel().dimension().location().toString();

        LOGGER.info(
            "player='{}' uuid='{}' command='{}' path='{}' success={} result={} dimension='{}'",
            username,
            uuid,
            commandInput,
            commandPath,
            success,
            result,
            dimension
        );
    }

    private static String buildCommandPath(CommandContext<CommandSourceStack> context) {
        List<String> literals = new ArrayList<>();
        for (ParsedCommandNode<CommandSourceStack> parsedNode : context.getNodes()) {
            CommandNode<CommandSourceStack> node = parsedNode.getNode();
            String nodeName = node.getName();
            if (nodeName != null && !nodeName.isBlank()) {
                literals.add(nodeName);
            }
        }
        return String.join(" ", literals);
    }
}