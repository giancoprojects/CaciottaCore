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
 * Comando /register <password> <confermaPassword>
 */
public class RegisterCommand implements CommandExecutor {

    private final AuthManager authManager;
    private final ConfigManager configManager;

    public RegisterCommand(CaciottaCore plugin) {
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

        if (authManager.isRegistered(player.getUniqueId())) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.already-registered"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.prompt-register"));
            return true;
        }

        String password = args[0];
        String confirm = args[1];

        AuthManager.RegisterResult result = authManager.register(player, password, confirm);
        switch (result) {
            case SUCCESS -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.register-success"));
            case PASSWORDS_DO_NOT_MATCH -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.passwords-do-not-match"));
            case PASSWORD_TOO_SHORT -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.password-too-short",
                    Map.of("min", String.valueOf(configManager.getMinPasswordLength()))));
            case PASSWORD_TOO_LONG -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.password-too-long",
                    Map.of("max", String.valueOf(configManager.getMaxPasswordLength()))));
            case ALREADY_REGISTERED -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.already-registered"));
            case ALREADY_LOGGED_IN -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.already-logged-in"));
        }

        return true;
    }
}
