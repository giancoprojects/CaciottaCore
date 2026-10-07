package it.caciotta.core.scoreboard;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Comando per gestire la visualizzazione e ricarica della Scoreboard.
 */
public class ScoreboardCommand implements TabExecutor {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;

    public ScoreboardCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("caciottacore.admin") && !sender.hasPermission("caciottacore.scoreboard.admin")) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
                return true;
            }
            configManager.reload();
            plugin.getScoreboardManager().reload();
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("scoreboard.reloaded"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        // Toggle della visibilità
        boolean enabled = plugin.getScoreboardManager().toggleScoreboard(player);
        if (enabled) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("scoreboard.toggle-on"));
        } else {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("scoreboard.toggle-off"));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("toggle".startsWith(args[0].toLowerCase())) {
                completions.add("toggle");
            }
            if ((sender.hasPermission("caciottacore.admin") || sender.hasPermission("caciottacore.scoreboard.admin"))
                    && "reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
        }
        return completions;
    }
}
