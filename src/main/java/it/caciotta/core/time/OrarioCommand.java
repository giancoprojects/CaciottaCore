package it.caciotta.core.time;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comando /orario (alias /ora, /time, /servertime) per consultare l'orario e la data
 * del server sincronizzati con il fuso orario italiano.
 */
public class OrarioCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final TimeManager timeManager;
    private final ConfigManager configManager;

    public OrarioCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.timeManager = plugin.getTimeManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("caciottacore.orario.admin") && !sender.hasPermission("caciottacore.admin") && !sender.isOp()) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
                return true;
            }
            configManager.reload();
            timeManager.reload();
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("orario.reloaded"));
            return true;
        }

        if (!sender.hasPermission("caciottacore.orario.use")) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        Map<String, String> placeholders = Map.of(
                "time", timeManager.getFormattedTime(),
                "short_time", timeManager.getFormattedShortTime(),
                "date", timeManager.getFormattedDate(),
                "timezone", timeManager.getZoneId().getId(),
                "day_of_week", timeManager.getDayOfWeek()
        );

        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("orario.current-time", placeholders));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("caciottacore.orario.admin") || sender.hasPermission("caciottacore.admin") || sender.isOp()) {
                if ("reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add("reload");
                }
            }
            return completions;
        }
        return List.of();
    }
}
