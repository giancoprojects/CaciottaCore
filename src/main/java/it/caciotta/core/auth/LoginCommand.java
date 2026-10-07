package it.caciotta.core.auth;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Comando /login <password>
 */
public class LoginCommand implements CommandExecutor {

    private final AuthManager authManager;
    private final ConfigManager configManager;

    public LoginCommand(CaciottaCore plugin) {
        this.authManager = plugin.getAuthManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (authManager.isAuthenticated(player)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.already-logged-in"));
            return true;
        }

        if (!authManager.isRegistered(player.getUniqueId())) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.must-register-first"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.prompt-login"));
            return true;
        }

        String password = args[0];
        AuthManager.LoginResult result = authManager.login(player, password);

        switch (result) {
            case SUCCESS -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.login-success"));
            case WRONG_PASSWORD -> {
                int remaining = authManager.getRemainingAttempts(player.getUniqueId());
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.wrong-password",
                        Map.of("attempts", String.valueOf(remaining))));
            }
            case MAX_ATTEMPTS_EXCEEDED -> player.kick(MessageUtil.format(configManager.getPrefixedMessage("auth.max-attempts-exceeded")));
            case NOT_REGISTERED -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.must-register-first"));
            case ALREADY_LOGGED_IN -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.already-logged-in"));
        }

        return true;
    }
}
