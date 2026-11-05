package it.caciotta.core.invsee;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
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
 * Comando amministrativo /invsee per ispezionare e gestire in tempo reale l'inventario dei giocatori.
 */
public class InvseeCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final InvseeManager invseeManager;
    private final ConfigManager configManager;

    public InvseeCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.invseeManager = plugin.getInvseeManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!configManager.isInvseeEnabled()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("invsee.disabled"));
            return true;
        }

        if (!player.hasPermission("caciottacore.invsee") && !player.hasPermission("caciottacore.admin") && !player.isOp()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("invsee.usage", Map.of("label", label)));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getPlayer(targetName);
        }

        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("invsee.player-not-found", Map.of("target", targetName)));
            return true;
        }

        // Impedisci l'ispezione del proprio inventario tramite GUI container (causa glitch grafici client)
        if (player.getUniqueId().equals(target.getUniqueId())) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("invsee.cannot-inspect-self"));
            return true;
        }

        // Controllo immunità bersaglio
        if (configManager.isInvseePreventInspectExempt()
                && target.hasPermission("caciottacore.invsee.exempt")
                && !player.isOp()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("invsee.target-exempt", Map.of("target", target.getName())));
            return true;
        }

        // Gestione sotto-argomenti: readonly oppure ender
        boolean readOnly = false;
        if (args.length >= 2) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (mode.equals("ender") || mode.equals("ec") || mode.equals("enderchest")) {
                invseeManager.openEnderChest(player, target);
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("invsee.opened-ender", Map.of("target", target.getName())));
                return true;
            } else if (mode.equals("readonly") || mode.equals("ro")) {
                readOnly = true;
            }
        }

        invseeManager.openInvsee(player, target, readOnly);
        if (readOnly) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("invsee.opened-readonly", Map.of("target", target.getName())));
        } else {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("invsee.opened", Map.of("target", target.getName())));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.invsee") && !sender.hasPermission("caciottacore.admin") && !sender.isOp()) {
            return List.of();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player pl && pl.getUniqueId().equals(p.getUniqueId())) {
                    continue;
                }
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    list.add(p.getName());
                }
            }
            return list;
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> options = List.of("readonly", "ender");
            List<String> list = new ArrayList<>();
            for (String opt : options) {
                if (opt.startsWith(prefix)) {
                    list.add(opt);
                }
            }
            return list;
        }

        return List.of();
    }
}

