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
 * Comando /tempmute <giocatore> <durata> [motivo] per mutare temporaneamente un utente.
 */
public class TempMuteCommand implements CommandExecutor, TabCompleter {

    private final ModerationManager moderationManager;
    private final ConfigManager configManager;

    private static final List<String> SUGGESTED_DURATIONS = List.of("10m", "30m", "1h", "12h", "1d", "7d");

    public TempMuteCommand(CaciottaCore plugin) {
        this.moderationManager = plugin.getModerationManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.mod.mute") && !sender.isOp()) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.tempmute-usage", Map.of("label", label)));
            return true;
        }

        String targetName = args[0];

        if (sender instanceof Player player && player.getName().equalsIgnoreCase(targetName)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.cannot-punish-self"));
            return true;
        }

        long durationMillis;
        try {
            durationMillis = TimeUtil.parseDuration(args[1]);
        } catch (IllegalArgumentException e) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.invalid-duration", Map.of("error", e.getMessage())));
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

        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Nessun motivo specificato";
        String staffName = sender.getName();
        String formattedDuration = TimeUtil.formatDuration(durationMillis);

        moderationManager.tempMute(targetId, resolvedName, staffName, durationMillis, reason);

        // Feedback al moderatore
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("moderation.tempmute-success", Map.of(
                "target", resolvedName,
                "duration", formattedDuration,
                "reason", reason
        )));

        // Notifica globale allo staff/server
        moderationManager.broadcast("moderation.tempmute-broadcast", Map.of(
                "target", resolvedName,
                "staff", staffName,
                "duration", formattedDuration,
                "reason", reason
        ));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.mod.mute") && !sender.isOp()) {
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
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> list = new ArrayList<>();
            for (String dur : SUGGESTED_DURATIONS) {
                if (dur.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    list.add(dur);
                }
            }
            return list;
        }
        return List.of();
    }
}
