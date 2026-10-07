package it.caciotta.core.rank;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gestione comandi del sistema di Rank (/rank).
 */
public class RankCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final RankManager rankManager;

    public RankCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "set" -> handleSet(sender, args);
            case "reset" -> handleReset(sender, args);
            case "default" -> handleDefault(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender, label);
        }

        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        MessageUtil.sendMessage(sender, "&6=== &eSistema Rank - CaciottaCore &6===");
        MessageUtil.sendMessage(sender, "&e/" + label + " list &7- Visualizza tutti i rank disponibili");
        MessageUtil.sendMessage(sender, "&e/" + label + " info [giocatore] &7- Mostra dettagli del rank");
        if (sender.hasPermission("caciottacore.rank.admin") || sender.isOp()) {
            MessageUtil.sendMessage(sender, "&e/" + label + " set <giocatore> <rank> &7- Assegna un rank");
            MessageUtil.sendMessage(sender, "&e/" + label + " reset <giocatore> &7- Reimposta rank default");
            MessageUtil.sendMessage(sender, "&e/" + label + " default <rank> &7- Imposta il rank predefinito");
            MessageUtil.sendMessage(sender, "&e/" + label + " reload &7- Ricarica configurazione rank");
        }
    }

    private void handleList(CommandSender sender) {
        MessageUtil.sendMessage(sender, "&6=== &eRank Disponibili nel Server &6===");
        List<Rank> ranks = rankManager.getRanks();
        if (ranks.isEmpty()) {
            MessageUtil.sendMessage(sender, "&cNessun rank configurato.");
            return;
        }

        for (Rank rank : ranks) {
            long onlineCount = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> rankManager.getPlayerRank(p).equals(rank))
                    .count();

            String defaultTag = rank.isDefault() ? " &a[DEFAULT]" : "";
            MessageUtil.sendMessage(sender, "&8» &f" + rank.getId() + " &7(Display: " + rank.getDisplayName() + "&7, Peso: &e"
                    + rank.getWeight() + "&7, MaxHome: &b" + rank.getMaxHomes() + defaultTag + "&7) &8- &a" + onlineCount + " online");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Player targetPlayer = null;
        if (args.length >= 2) {
            targetPlayer = Bukkit.getPlayerExact(args[1]);
            if (targetPlayer == null) {
                // Prova offline player
                OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
                if (offline.hasPlayedBefore() || offline.isOnline()) {
                    Rank rank = rankManager.getPlayerRank(offline.getUniqueId());
                    showRankInfo(sender, offline.getName(), rank);
                    return;
                }
                MessageUtil.sendMessage(sender, plugin.getConfigManager().getPrefixedMessage("rank.player-not-found",
                        Map.of("player", args[1])));
                return;
            }
        } else if (sender instanceof Player p) {
            targetPlayer = p;
        } else {
            MessageUtil.sendMessage(sender, "&cUso console: /rank info <giocatore>");
            return;
        }

        Rank rank = rankManager.getPlayerRank(targetPlayer);
        showRankInfo(sender, targetPlayer.getName(), rank);
    }

    private void showRankInfo(CommandSender sender, String playerName, Rank rank) {
        if (rank == null) {
            MessageUtil.sendMessage(sender, "&cRank non trovato per " + playerName);
            return;
        }

        MessageUtil.sendMessage(sender, "&6=== &eInfo Rank: " + playerName + " &6===");
        MessageUtil.sendMessage(sender, "&7ID Rank: &f" + rank.getId() + (rank.isDefault() ? " &a[DEFAULT]" : ""));
        MessageUtil.sendMessage(sender, "&7Display Name: " + rank.getDisplayName());
        MessageUtil.sendMessage(sender, "&7Prefisso: " + rank.getPrefix());
        if (!rank.getSuffix().isEmpty()) {
            MessageUtil.sendMessage(sender, "&7Suffisso: " + rank.getSuffix());
        }
        MessageUtil.sendMessage(sender, "&7Peso gerarchico: &e" + rank.getWeight());
        MessageUtil.sendMessage(sender, "&7Max Home consentite: &b" + rank.getMaxHomes());
        MessageUtil.sendMessage(sender, "&7Colore chat: " + rank.getChatColor() + "Esempio testo");

        List<String> perms = rank.getPermissions();
        if (perms.isEmpty()) {
            MessageUtil.sendMessage(sender, "&7Permessi: &oNessuno");
        } else if (perms.size() <= 6) {
            MessageUtil.sendMessage(sender, "&7Permessi: &e" + String.join("&7, &e", perms));
        } else {
            MessageUtil.sendMessage(sender, "&7Permessi (" + perms.size() + "): &e"
                    + String.join("&7, &e", perms.subList(0, 6)) + "&7... (altri " + (perms.size() - 6) + ")");
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("caciottacore.rank.admin") && !sender.isOp()) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getPrefixedMessage("no-permission"));
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "&cUso: /rank set <giocatore> <rank>");
            return;
        }

        String targetName = args[1];
        String rankId = args[2].toLowerCase(Locale.ROOT);

        Rank rank = rankManager.getRank(rankId);
        if (rank == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getPrefixedMessage("rank.rank-not-found",
                    Map.of("rank", rankId)));
            return;
        }

        UUID targetUUID;
        Player onlinePlayer = Bukkit.getPlayerExact(targetName);
        if (onlinePlayer != null) {
            targetUUID = onlinePlayer.getUniqueId();
            targetName = onlinePlayer.getName();
        } else {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
            targetUUID = offlinePlayer.getUniqueId();
        }

        rankManager.setPlayerRank(targetUUID, rank, targetName);

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getPrefixedMessage("rank.set-success-sender",
                Map.of("player", targetName, "rank", rank.getDisplayName())));

        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            MessageUtil.sendMessage(onlinePlayer, plugin.getConfigManager().getPrefixedMessage("rank.set-success-target",
                    Map.of("rank", rank.getDisplayName())));
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("caciottacore.rank.admin") && !sender.isOp()) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getPrefixedMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "&cUso: /rank reset <giocatore>");
            return;
        }

        String targetName = args[1];
        UUID targetUUID;
        Player onlinePlayer = Bukkit.getPlayerExact(targetName);
        if (onlinePlayer != null) {
            targetUUID = onlinePlayer.getUniqueId();
            targetName = onlinePlayer.getName();
        } else {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
            targetUUID = offlinePlayer.getUniqueId();
        }

        rankManager.resetPlayerRank(targetUUID);

        Rank defaultRank = rankManager.getDefaultRank();
        String defName = defaultRank != null ? defaultRank.getDisplayName() : "Default";

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getPrefixedMessage("rank.reset-success-sender",
                Map.of("player", targetName, "rank", defName)));

        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            MessageUtil.sendMessage(onlinePlayer, plugin.getConfigManager().getPrefixedMessage("rank.reset-success-target",
                    Map.of("rank", defName)));
        }
    }

    private void handleDefault(CommandSender sender, String[] args) {
        if (!sender.hasPermission("caciottacore.rank.admin") && !sender.isOp()) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getPrefixedMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "&cUso: /rank default <rank>");
            return;
        }

        String rankId = args[1].toLowerCase(Locale.ROOT);
        Rank rank = rankManager.getRank(rankId);
        if (rank == null) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getPrefixedMessage("rank.rank-not-found",
                    Map.of("rank", rankId)));
            return;
        }

        rankManager.setDefaultRank(rank);
        MessageUtil.sendMessage(sender, plugin.getConfigManager().getPrefixedMessage("rank.default-updated",
                Map.of("rank", rank.getDisplayName())));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("caciottacore.rank.admin") && !sender.isOp()) {
            MessageUtil.sendMessage(sender, plugin.getConfigManager().getPrefixedMessage("no-permission"));
            return;
        }

        rankManager.reload();

        MessageUtil.sendMessage(sender, plugin.getConfigManager().getPrefixedMessage("rank.reloaded"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> sub = new ArrayList<>(List.of("list", "info"));
            if (sender.hasPermission("caciottacore.rank.admin") || sender.isOp()) {
                sub.addAll(List.of("set", "reset", "default", "reload"));
            }
            return filter(sub, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("set") || sub.equals("info") || sub.equals("reset")) {
                List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                return filter(names, args[1]);
            }
            if (sub.equals("default")) {
                List<String> rankIds = rankManager.getRanks().stream().map(Rank::getId).collect(Collectors.toList());
                return filter(rankIds, args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            List<String> rankIds = rankManager.getRanks().stream().map(Rank::getId).collect(Collectors.toList());
            return filter(rankIds, args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}
