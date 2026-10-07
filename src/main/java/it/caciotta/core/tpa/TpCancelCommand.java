package it.caciotta.core.tpa;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Comando /tpacancel per annullare la richiesta inviata.
 */
public class TpCancelCommand implements CommandExecutor {

    private final TpaManager tpaManager;
    private final ConfigManager configManager;

    public TpCancelCommand(CaciottaCore plugin) {
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

        tpaManager.cancelOutgoing(player);
        return true;
    }
}
