package it.caciotta.core.tpa;

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

/**
 * Comando /tpdeny [giocatore]
 */
public class TpDenyCommand implements CommandExecutor, TabCompleter {

    private final TpaManager tpaManager;
    private final ConfigManager configManager;

    public TpDenyCommand(CaciottaCore plugin) {
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

        String targetSender = args.length > 0 ? args[0] : null;
        tpaManager.denyRequest(player, targetSender);
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
            for (String name : tpaManager.getPendingSenderNames(player.getUniqueId())) {
                if (name.toLowerCase(Locale.ROOT).startsWith(query)) {
                    list.add(name);
                }
            }
            return list;
        }

        return List.of();
    }
}
