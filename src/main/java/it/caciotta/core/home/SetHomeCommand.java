package it.caciotta.core.home;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Comando /sethome [nome]
 */
public class SetHomeCommand implements CommandExecutor {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_-]{1,16}$");

    private final HomeManager homeManager;
    private final ConfigManager configManager;

    public SetHomeCommand(CaciottaCore plugin) {
        this.homeManager = plugin.getHomeManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("caciottacore.home.set")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        String name = args.length > 0 ? args[0] : "home";

        if (!VALID_NAME.matcher(name).matches()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("home.invalid-name"));
            return true;
        }

        boolean success = homeManager.setHome(player, name, player.getLocation());
        if (success) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("home.set-success",
                    Map.of("name", name)));
        } else {
            int max = homeManager.getMaxHomes(player);
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("home.set-limit-reached",
                    Map.of("limit", String.valueOf(max))));
        }

        return true;
    }
}
