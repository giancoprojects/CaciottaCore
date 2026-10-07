package it.caciotta.core.bag;

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
 * Comando /bag (alias /backpack, /zaino, /borsa) per aprire il proprio zaino portatile
 * o ispezionare quello di un altro giocatore (staff).
 */
public class BagCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final BagManager bagManager;
    private final ConfigManager configManager;

    public BagCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.bagManager = plugin.getBagManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!configManager.isBagEnabled()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("bag.disabled"));
            return true;
        }

        if (!player.hasPermission("caciottacore.bag.use")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (player.isDead()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("bag.cannot-open-dead"));
            return true;
        }

        // Apertura zaino personale
        if (args.length == 0) {
            bagManager.openBag(player, player.getUniqueId(), player.getName());
            return true;
        }

        // Ispezione zaino di un altro giocatore (riservato allo staff)
        if (!player.hasPermission("caciottacore.bag.admin") && !player.hasPermission("caciottacore.admin") && !player.isOp()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        String targetName = args[0];
        Player onlineTarget = Bukkit.getPlayerExact(targetName);

        if (onlineTarget != null) {
            bagManager.openBag(player, onlineTarget.getUniqueId(), onlineTarget.getName());
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("bag.open-other", Map.of("target", onlineTarget.getName())));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        if (offlineTarget.hasPlayedBefore() || offlineTarget.isOnline()) {
            String name = offlineTarget.getName() != null ? offlineTarget.getName() : targetName;
            bagManager.openBag(player, offlineTarget.getUniqueId(), name);
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("bag.open-other", Map.of("target", name)));
            return true;
        }

        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("bag.target-not-found", Map.of("target", targetName)));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            boolean isAdmin = sender.hasPermission("caciottacore.bag.admin") || sender.hasPermission("caciottacore.admin") || sender.isOp();
            if (!isAdmin) {
                return List.of();
            }

            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matches.add(p.getName());
                }
            }
            return matches;
        }
        return List.of();
    }
}
