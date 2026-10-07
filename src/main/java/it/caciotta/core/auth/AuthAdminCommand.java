package it.caciotta.core.auth;

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
 * Comando amministrativo /authadmin <reload|unregister> [giocatore]
 */
public class AuthAdminCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final AuthManager authManager;
    private final ConfigManager configManager;

    public AuthAdminCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.authManager = plugin.getAuthManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.admin")) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendMessage(sender, "&6--- Comandi Admin CaciottaCore ---");
            MessageUtil.sendMessage(sender, "&e/" + label + " reload &7- Ricarica la configurazione");
            MessageUtil.sendMessage(sender, "&e/" + label + " unregister <giocatore> &7- Cancella la registrazione di un giocatore");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            plugin.reloadPlugin();
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("plugin-reloaded"));
            return true;
        }

        if (sub.equals("unregister")) {
            if (args.length < 2) {
                MessageUtil.sendMessage(sender, "&cUso: /" + label + " unregister <giocatore>");
                return true;
            }
            String targetName = args[1];
            boolean success = authManager.unregisterByUsername(targetName);
            if (success) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("auth.admin-unregistered",
                        Map.of("player", targetName)));
            } else {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("auth.admin-player-not-found"));
            }
            return true;
        }

        MessageUtil.sendMessage(sender, "&cComando sconosciuto. Usa /" + label + " per la guida.");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> sub = List.of("reload", "unregister");
            List<String> res = new ArrayList<>();
            for (String s : sub) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    res.add(s);
                }
            }
            return res;
        }
        return List.of();
    }
}
