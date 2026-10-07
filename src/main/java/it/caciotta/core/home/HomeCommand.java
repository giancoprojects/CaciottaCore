package it.caciotta.core.home;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Comando /home [nome]
 */
public class HomeCommand implements CommandExecutor, TabCompleter {

    private final HomeManager homeManager;
    private final ConfigManager configManager;

    public HomeCommand(CaciottaCore plugin) {
        this.homeManager = plugin.getHomeManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("caciottacore.home.use")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        Map<String, Home> homes = homeManager.getHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("home.list-empty"));
            return true;
        }

        String targetName;
        if (args.length == 0) {
            if (homes.containsKey("home")) {
                targetName = "home";
            } else if (homes.size() == 1) {
                targetName = homes.keySet().iterator().next();
            } else {
                MessageUtil.sendMessage(player, "&cUsa: /" + label + " <nome> oppure consulta la lista con &e/homes&c.");
                return true;
            }
        } else {
            targetName = args[0];
        }

        homeManager.teleportToHome(player, targetName);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 1) {
            Map<String, Home> homes = homeManager.getHomes(player.getUniqueId());
            List<String> list = new ArrayList<>();
            for (String name : homes.keySet()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    list.add(name);
                }
            }
            return list;
        }
        return List.of();
    }
}
