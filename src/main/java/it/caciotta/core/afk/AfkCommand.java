package it.caciotta.core.afk;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Gestore del comando /afk (e alias /away).
 */
public class AfkCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final AfkManager afkManager;
    private final ConfigManager configManager;

    public AfkCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.afkManager = plugin.getAfkManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Nessun argomento: entra/esce dall'AFK per il giocatore che invia il comando
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
                return true;
            }

            if (!player.hasPermission("caciottacore.afk.use") && !player.isOp()) {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
                return true;
            }

            afkManager.toggleAfk(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Subcomando: /afk list
        if (sub.equals("list")) {
            if (!sender.hasPermission("caciottacore.afk.use") && !sender.isOp()) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
                return true;
            }

            Map<UUID, AfkManager.AfkData> list = afkManager.getAfkPlayers();
            if (list.isEmpty()) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("afk.list-empty"));
                return true;
            }

            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("afk.list-header", Map.of("count", String.valueOf(list.size()))));
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, AfkManager.AfkData> entry : list.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                String pName = p != null ? p.getName() : "Sconosciuto";
                long elapsedSeconds = (now - entry.getValue().startTime()) / 1000L;
                String timeFormatted = formatTime(elapsedSeconds);
                MessageUtil.sendMessage(sender, configManager.getRawMessage("afk.list-entry")
                        .replace("{player}", pName)
                        .replace("{time}", timeFormatted));
            }
            return true;
        }

        // Subcomando: /afk reload
        if (sub.equals("reload")) {
            if (!sender.hasPermission("caciottacore.admin") && !sender.hasPermission("caciottacore.afk.admin") && !sender.isOp()) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
                return true;
            }

            afkManager.reload();
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("afk.reloaded"));
            return true;
        }

        // Subcomando per staffer: /afk <giocatore>
        if (sender.hasPermission("caciottacore.admin") || sender.hasPermission("caciottacore.afk.admin") || sender.isOp()) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("afk.target-not-found", Map.of("target", args[0])));
                return true;
            }

            afkManager.toggleAfk(target);
            return true;
        }

        // Se utente normale senza argomenti validi
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
        return true;
    }

    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remSec = seconds % 60;
        if (minutes < 60) {
            return minutes + "m " + remSec + "s";
        }
        long hours = minutes / 60;
        long remMin = minutes % 60;
        return hours + "h " + remMin + "m";
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("list");
            boolean isAdmin = sender.hasPermission("caciottacore.admin") || sender.hasPermission("caciottacore.afk.admin") || sender.isOp();
            if (isAdmin) {
                list.add("reload");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    list.add(p.getName());
                }
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
