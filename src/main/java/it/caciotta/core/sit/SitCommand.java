package it.caciotta.core.sit;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Comando /sit per sedersi ovunque sul terreno o alzarsi.
 */
public class SitCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final SitManager sitManager;
    private final ConfigManager configManager;

    public SitCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.sitManager = plugin.getSitManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("caciottacore.sit") && !player.isOp()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (!configManager.isSitEnabled()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sit.disabled"));
            return true;
        }

        if (sitManager.isSitting(player)) {
            sitManager.dismount(player, true);
        } else {
            sitManager.sit(player);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
