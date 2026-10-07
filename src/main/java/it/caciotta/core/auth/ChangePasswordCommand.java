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
 * Comando /changepassword <vecchiaPassword> <nuovaPassword>
 */
public class ChangePasswordCommand implements CommandExecutor {

    private final AuthManager authManager;
    private final ConfigManager configManager;

    public ChangePasswordCommand(CaciottaCore plugin) {
        this.authManager = plugin.getAuthManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!authManager.isAuthenticated(player)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.not-logged-in"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUso corretto: /" + label + " <vecchiaPassword> <nuovaPassword>");
            return true;
        }

        String oldPass = args[0];
        String newPass = args[1];

        if (newPass.length() < configManager.getMinPasswordLength()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.password-too-short",
                    Map.of("min", String.valueOf(configManager.getMinPasswordLength()))));
            return true;
        }

        if (newPass.length() > configManager.getMaxPasswordLength()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.password-too-long",
                    Map.of("max", String.valueOf(configManager.getMaxPasswordLength()))));
            return true;
        }

        boolean success = authManager.changePassword(player, oldPass, newPass);
        if (success) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.change-pass-success"));
        } else {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.change-pass-wrong-old"));
        }

        return true;
    }
}
