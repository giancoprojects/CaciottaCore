package it.caciotta.core.rtp;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gestore del comando /rtp (alias: /randomtp, /rteleport):
 * Teletrasporto casuale sicuro utilizzabile 1 volta al mese da tutti gli utenti a prescindere dal rank.
 */
public class RtpCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final RtpManager rtpManager;

    public RtpCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.rtpManager = plugin.getRtpManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        boolean isAdmin = sender.isOp() || sender.hasPermission("caciottacore.admin") || sender.hasPermission("caciottacore.rtp.admin");

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
                return true;
            }

            if (!player.hasPermission("caciottacore.rtp.use")) {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
                return true;
            }

            rtpManager.startRtp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("info") || sub.equals("status")) {
            if (!(sender instanceof Player player)) {
                MessageUtil.sendMessage(sender, "&6--- &eRTP Info &6---");
                MessageUtil.sendMessage(sender, "&7Mese corrente: &e" + rtpManager.getCurrentMonthFormatted());
                MessageUtil.sendMessage(sender, "&7Prossimo reset: &e" + rtpManager.getNextResetDateFormatted());
                return true;
            }

            if (!player.hasPermission("caciottacore.rtp.use")) {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
                return true;
            }

            boolean canUse = rtpManager.canUseRtp(player);
            Map<String, String> placeholders = Map.of(
                    "month", rtpManager.getCurrentMonthFormatted(),
                    "reset_date", rtpManager.getNextResetDateFormatted()
            );

            if (canUse) {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.status-available", placeholders));
            } else {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.status-used", placeholders));
            }
            return true;
        }

        if (sub.equals("admin")) {
            if (!isAdmin) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
                return true;
            }

            if (args.length < 2) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("rtp.admin-usage", Map.of("label", label)));
                return true;
            }

            String action = args[1].toLowerCase(Locale.ROOT);

            switch (action) {
                case "reset" -> {
                    if (args.length < 3) {
                        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("rtp.admin-usage", Map.of("label", label)));
                        return true;
                    }

                    String targetName = args[2];
                    Player target = Bukkit.getPlayer(targetName);
                    if (target != null) {
                        rtpManager.resetUsage(target.getUniqueId());
                        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("rtp.admin-reset-success",
                                Map.of("player", target.getName())));
                        return true;
                    }

                    @SuppressWarnings("deprecation")
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
                    if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline() || offlinePlayer.getName() != null) {
                        rtpManager.resetUsage(offlinePlayer.getUniqueId());
                        String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : targetName;
                        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("rtp.admin-reset-success",
                                Map.of("player", name)));
                    } else {
                        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("rtp.player-not-found",
                                Map.of("player", targetName)));
                    }
                    return true;
                }
                case "resetall" -> {
                    rtpManager.resetAllUsages();
                    MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("rtp.admin-reset-all-success"));
                    return true;
                }
                case "reload" -> {
                    rtpManager.reload();
                    MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("rtp.admin-reload-success"));
                    return true;
                }
                default -> {
                    MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("rtp.admin-usage", Map.of("label", label)));
                    return true;
                }
            }
        }

        if (sub.equals("help") || sub.equals("?")) {
            MessageUtil.sendMessage(sender, "&6--- &e🧀 Teletrasporto Casuale (/rtp) &6---");
            MessageUtil.sendMessage(sender, "&e/" + label + " &7- Teletrasportati in un punto casuale del mondo (1 volta al mese)");
            MessageUtil.sendMessage(sender, "&e/" + label + " info &7- Visualizza lo stato di utilizzo mensile e la data del prossimo reset");
            if (isAdmin) {
                MessageUtil.sendMessage(sender, "&c/" + label + " admin reset <giocatore> &7- Resetta l'utilizzo mensile di un giocatore");
                MessageUtil.sendMessage(sender, "&c/" + label + " admin resetall &7- Resetta l'utilizzo mensile di tutti i giocatori");
                MessageUtil.sendMessage(sender, "&c/" + label + " admin reload &7- Ricarica configurazione e dati RTP");
            }
            return true;
        }

        MessageUtil.sendMessage(sender, "&cUso corretto: &e/" + label + " [info|status]");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        boolean isAdmin = sender.isOp() || sender.hasPermission("caciottacore.admin") || sender.hasPermission("caciottacore.rtp.admin");

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("info");
            list.add("status");
            list.add("help");
            if (isAdmin) {
                list.add("admin");
            }
            return filterPrefix(list, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && isAdmin) {
            List<String> list = List.of("reset", "resetall", "reload");
            return filterPrefix(list, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("reset") && isAdmin) {
            List<String> playerNames = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                playerNames.add(p.getName());
            }
            return filterPrefix(playerNames, args[2]);
        }

        return List.of();
    }

    private List<String> filterPrefix(List<String> list, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String item : list) {
            if (item.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(item);
            }
        }
        return matches;
    }
}

