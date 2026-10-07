package it.caciotta.core.moderation;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Comando /kick <giocatore> [motivo] per cacciare/espellere immediatamente un giocatore online.
 */
public class KickCommand implements CommandExecutor, TabCompleter {

    private final ModerationManager moderationManager;
    private final ConfigManager configManager;

    public KickCommand(CaciottaCore plugin) {
        this.moderationManager = plugin.getModerationManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.mod.kick") && !sender.isOp()) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.kick-usage", Map.of("label", label)));
            return true;
        }

        String targetName = args[0];

        if (sender instanceof Player player && player.getName().equalsIgnoreCase(targetName)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.cannot-punish-self"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.player-not-found", Map.of("target", targetName)));
            return true;
        }

        if (sender instanceof Player && (target.hasPermission("caciottacore.mod.bypass") || target.isOp())) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.cannot-punish-staff"));
            return true;
        }

        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Nessun motivo specificato";
        String staffName = sender.getName();
        String targetPlayerName = target.getName();

        moderationManager.kick(target, staffName, reason);

        // Feedback al moderatore
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.kick-success", Map.of(
                "target", targetPlayerName,
                "reason", reason
        )));

        // Notifica globale
        moderationManager.broadcast("moderation.kick-broadcast", Map.of(
                "target", targetPlayerName,
                "staff", staffName,
                "reason", reason
        ));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.mod.kick") && !sender.isOp()) {
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
