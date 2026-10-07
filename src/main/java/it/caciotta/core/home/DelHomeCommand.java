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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comando /delhome <nome>
 */
public class DelHomeCommand implements CommandExecutor, TabCompleter {

    private final HomeManager homeManager;
    private final ConfigManager configManager;

    public DelHomeCommand(CaciottaCore plugin) {
        this.homeManager = plugin.getHomeManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("caciottacore.home.delete")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(player, "&cUso corretto: /" + label + " <nome>");
            return true;
        }

        String name = args[0];
        boolean deleted = homeManager.deleteHome(player.getUniqueId(), name);
        if (deleted) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("home.deleted",
                    Map.of("name", name)));
        } else {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("home.not-found",
                    Map.of("name", name)));
        }

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
