package it.caciotta.core.moderation;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Comando /ban <giocatore> [motivo] per bannare permanentemente un utente.
 */
public class BanCommand implements CommandExecutor, TabCompleter {

    private final ModerationManager moderationManager;
    private final ConfigManager configManager;

    public BanCommand(CaciottaCore plugin) {
        this.moderationManager = plugin.getModerationManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.mod.ban") && !sender.isOp()) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.ban-usage", Map.of("label", label)));
            return true;
        }

        String targetName = args[0];

        if (sender instanceof Player player && player.getName().equalsIgnoreCase(targetName)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.cannot-punish-self"));
            return true;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        UUID targetId;
        String resolvedName;

        if (onlineTarget != null) {
            if (sender instanceof Player && (onlineTarget.hasPermission("caciottacore.mod.bypass") || onlineTarget.isOp())) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.cannot-punish-staff"));
                return true;
            }
            targetId = onlineTarget.getUniqueId();
            resolvedName = onlineTarget.getName();
        } else {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
            targetId = offlineTarget.getUniqueId();
            resolvedName = offlineTarget.getName() != null ? offlineTarget.getName() : targetName;
        }

        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Nessun motivo specificato";
        String staffName = sender.getName();

        moderationManager.ban(targetId, resolvedName, staffName, reason);

        // Feedback al moderatore
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.ban-success", Map.of(
                "target", resolvedName,
                "reason", reason
        )));

        // Notifica globale allo staff/server
        moderationManager.broadcast("moderation.ban-broadcast", Map.of(
                "target", resolvedName,
                "staff", staffName,
                "reason", reason
        ));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.mod.ban") && !sender.isOp()) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    list.add(p.getName());
                }
            }
            return list;
        }
        return List.of();
    }
}
