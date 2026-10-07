package it.caciotta.core.tpa;

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

/**
 * Comando /tpa <giocatore>
 */
public class TpaCommand implements CommandExecutor, TabCompleter {

    private final TpaManager tpaManager;
    private final ConfigManager configManager;

    public TpaCommand(CaciottaCore plugin) {
        this.tpaManager = plugin.getTpaManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("caciottacore.tpa.use")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("tpa.usage", java.util.Map.of("label", label)));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("tpa.player-not-found", java.util.Map.of("player", args[0])));
            return true;
        }

        tpaManager.sendRequest(player, target);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            String query = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getUniqueId().equals(player.getUniqueId()) && p.getName().toLowerCase(Locale.ROOT).startsWith(query)) {
                    list.add(p.getName());
                }
            }
            return list;
        }

        return List.of();
    }
}
