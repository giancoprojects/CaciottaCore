package it.caciotta.core.moderation;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comando /unban <giocatore> (alias /pardon) per revocare un ban attivo.
 */
public class UnbanCommand implements CommandExecutor, TabCompleter {

    private final ModerationManager moderationManager;
    private final ConfigManager configManager;

    public UnbanCommand(CaciottaCore plugin) {
        this.moderationManager = plugin.getModerationManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.mod.unban") && !sender.isOp()) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.unban-usage", Map.of("label", label)));
            return true;
        }

        String targetName = args[0];
        boolean success = moderationManager.unban(targetName);

        if (!success) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.not-banned", Map.of("target", targetName)));
            return true;
        }

        String staffName = sender.getName();

        // Feedback al moderatore
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.unban-success", Map.of(
                "target", targetName
        )));

        // Notifica globale
        moderationManager.broadcast("moderation.unban-broadcast", Map.of(
                "target", targetName,
                "staff", staffName
        ));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.mod.unban") && !sender.isOp()) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> list = new ArrayList<>();
            for (String banned : moderationManager.getBannedPlayerNames()) {
                if (banned.startsWith(prefix)) {
                    list.add(banned);
                }
            }
            return list;
        }
        return List.of();
    }
}
