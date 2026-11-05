package it.caciotta.core.cestino;

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
 * Gestore del comando /cestino (alias: /trash, /bin, /disposal, /cestinocomune):
 * Cestino comune dove i giocatori possono depositare o prendere oggetti,
 * che si svuota automaticamente ogni 15 minuti.
 */
public class CestinoCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final CestinoManager cestinoManager;

    public CestinoCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.cestinoManager = plugin.getCestinoManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        boolean isAdmin = sender.isOp() || sender.hasPermission("caciottacore.admin") || sender.hasPermission("caciottacore.cestino.admin");

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
                return true;
            }

            if (!player.hasPermission("caciottacore.cestino.use")) {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
                return true;
            }

            cestinoManager.openCestino(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("tempo") || sub.equals("countdown") || sub.equals("info") || sub.equals("time")) {
            if (!sender.hasPermission("caciottacore.cestino.use")) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
                return true;
            }

            String formattedTime = cestinoManager.getFormattedCountdownDetailed();
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("cestino.countdown",
                    Map.of("time", formattedTime)));
            return true;
        }

        if (sub.equals("clear") || sub.equals("svuota") || sub.equals("reset")) {
            if (!isAdmin) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
                return true;
            }

            cestinoManager.clearCestino(false, true);
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("cestino.cleared-admin"));
            return true;
        }

        if (sub.equals("reload")) {
            if (!isAdmin) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
                return true;
            }

            cestinoManager.reload();
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("cestino.reloaded"));
            return true;
        }

        if (sub.equals("help") || sub.equals("?")) {
            MessageUtil.sendMessage(sender, "&6--- &e🗑 Cestino Comune (/cestino) &6---");
            MessageUtil.sendMessage(sender, "&e/" + label + " &7- Apri il cestino comune per depositare o prendere oggetti");
            MessageUtil.sendMessage(sender, "&e/" + label + " tempo &7- Visualizza il conto alla rovescia al prossimo reset");
            if (isAdmin) {
                MessageUtil.sendMessage(sender, "&c/" + label + " clear &7- Svuota istantaneamente il cestino comune");
                MessageUtil.sendMessage(sender, "&c/" + label + " reload &7- Ricarica configurazione e timer");
            }
            return true;
        }

        MessageUtil.sendMessage(sender, "&cUso corretto: &e/" + label + " [tempo" + (isAdmin ? "|clear|reload" : "") + "]");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        boolean isAdmin = sender.isOp() || sender.hasPermission("caciottacore.admin") || sender.hasPermission("caciottacore.cestino.admin");

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("tempo");
            list.add("info");
            list.add("help");
            if (isAdmin) {
                list.add("clear");
                list.add("reload");
            }

            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String item : list) {
                if (item.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matches.add(item);
                }
            }
            return matches;
        }

        return List.of();
    }
}

