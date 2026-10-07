package it.caciotta.core.command;

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

/**
 * Gestore del comando /reload (e /caciottacore [reload], /corereload, ecc.)
 * per ricaricare tutte le configurazioni, i dati e i moduli del plugin CaciottaCore.
 */
public class ReloadCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;

    public ReloadCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.admin") && !sender.hasPermission("caciottacore.reload") && !sender.isOp()) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        // Se invocato come /caciottacore o /core senza argomenti o con help
        if (label.equalsIgnoreCase("caciottacore") || label.equalsIgnoreCase("core")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                MessageUtil.sendMessage(sender, "&6--- &e🧀 CaciottaCore &6---");
                MessageUtil.sendMessage(sender, "&e/" + label + " reload &7- Ricarica tutte le configurazioni e i moduli");
                MessageUtil.sendMessage(sender, "&e/aiuto &7- Guida interattiva ai comandi");
                return true;
            } else if (!args[0].equalsIgnoreCase("reload")) {
                MessageUtil.sendMessage(sender, "&cUso corretto: &e/" + label + " reload");
                return true;
            }
        }

        long start = System.currentTimeMillis();

        // Ricarica tutti i moduli
        plugin.reloadPlugin();

        long timeTaken = System.currentTimeMillis() - start;

        // Feedback in chat
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("plugin-reloaded"));
        MessageUtil.sendMessage(sender, "&7Moduli ricaricati in &e" + timeTaken + "ms&7:");
        MessageUtil.sendMessage(sender, " &8» &econfig.yml &7(Impostazioni generali, prefisso, messaggi)");
        MessageUtil.sendMessage(sender, " &8» &eranks.yml &7(Rank, permessi e tablist online)");
        MessageUtil.sendMessage(sender, " &8» &escoreboard &7(Sidebar dinamica e task)");
        MessageUtil.sendMessage(sender, " &8» &ewarps &7(Warp pubblici e permessi)");
        MessageUtil.sendMessage(sender, " &8» &ehomes &7(Cache dati home)");
        MessageUtil.sendMessage(sender, " &8» &eauth &7(Database account e sessioni)");
        MessageUtil.sendMessage(sender, " &8» &etpback &7(Teletrasporto punto di morte e conteggio giornaliero)");
        MessageUtil.sendMessage(sender, " &8» &emoderazione &7(Archivio sanzioni, ban e mute attivi)");
        MessageUtil.sendMessage(sender, " &8» &enews &7(Libro novita e aggiornamenti server)");
        MessageUtil.sendMessage(sender, " &8» &ebag &7(Zaini personali e cache inventari)");

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.admin") && !sender.hasPermission("caciottacore.reload") && !sender.isOp()) {
            return List.of();
        }

        if (label.equalsIgnoreCase("caciottacore") || label.equalsIgnoreCase("core")) {
            if (args.length == 1) {
                List<String> list = new ArrayList<>();
                if ("reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    list.add("reload");
                }
                if ("help".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    list.add("help");
                }
                return list;
            }
        }

        return List.of();
    }
}
