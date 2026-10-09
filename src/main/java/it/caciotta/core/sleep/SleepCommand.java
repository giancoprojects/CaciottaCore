package it.caciotta.core.sleep;

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
 * Gestore del comando /sleep (e alias /dormi).
 */
public class SleepCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final SleepManager sleepManager;
    private final ConfigManager configManager;

    public SleepCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.sleepManager = plugin.getSleepManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        // Se sono passati argomenti da parte di staffer/admin
        if (args.length > 0) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("reload") && (player.hasPermission("caciottacore.admin") || player.hasPermission("caciottacore.sleep.admin") || player.isOp())) {
                sleepManager.reload();
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sleep.reloaded"));
                return true;
            } else if (sub.equals("status")) {
                long time = player.getWorld().getTime();
                int sleeping = sleepManager.getSleepingCount(player.getWorld());
                int total = sleepManager.getEligiblePlayersCount(player.getWorld());
                MessageUtil.sendMessage(player, "&6--- &e🧀 Stato Sonno &6---");
                MessageUtil.sendMessage(player, "&7Ora mondo: &e" + time + " &7tick (" + (time >= configManager.getSleepStartTick() && time <= configManager.getSleepEndTick() ? "&aNotte" : "&6Giorno") + "&7)");
                MessageUtil.sendMessage(player, "&7Giocatori a dormire: &e" + sleeping + "&7/&e" + total);
                return true;
            }
        }

        // Permesso base per dormire
        if (!player.hasPermission("caciottacore.sleep.use") && !player.isOp()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        // Esegue il toggle del sonno
        sleepManager.toggleSleep(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("status");
            if (sender.hasPermission("caciottacore.admin") || sender.hasPermission("caciottacore.sleep.admin") || sender.isOp()) {
                list.add("reload");
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return list.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
