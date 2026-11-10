package it.caciotta.core.auth;

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

import java.util.Collections;
import java.util.List;

/**
 * Comando /logout (alias: /esci, /sconnect) per disconnettere la sessione dell'utente
 * e riapplicare le restrizioni di sicurezza dell'autenticazione.
 */
public class LogoutCommand implements CommandExecutor, TabCompleter {

    private final AuthManager authManager;
    private final ConfigManager configManager;

    public LogoutCommand(CaciottaCore plugin) {
        this.authManager = plugin.getAuthManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("caciottacore.auth.logout") && !player.hasPermission("caciottacore.auth.*")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (!authManager.isAuthenticated(player)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.not-logged-in"));
            return true;
        }

        boolean success = authManager.logout(player);
        if (success) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.logout-success"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

