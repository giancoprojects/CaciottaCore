package it.caciotta.core.warp;

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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Comando principale /warp [nome] per teletrasportarsi a un warp pubblico.
 * Supporta anche le scorciatoie /warp list, /warp set <nome>, /warp del <nome>.
 */
public class WarpCommand implements CommandExecutor, TabCompleter {

    private final WarpManager warpManager;
    private final ConfigManager configManager;
    private final WarpsCommand warpsCommand;
    private final SetWarpCommand setWarpCommand;
    private final DelWarpCommand delWarpCommand;

    public WarpCommand(CaciottaCore plugin, WarpsCommand warpsCommand, SetWarpCommand setWarpCommand, DelWarpCommand delWarpCommand) {
        this.warpManager = plugin.getWarpManager();
        this.configManager = plugin.getConfigManager();
        this.warpsCommand = warpsCommand;
        this.setWarpCommand = setWarpCommand;
        this.delWarpCommand = delWarpCommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (args.length == 0) {
            if (player.hasPermission("caciottacore.warp.list")) {
                warpsCommand.showWarpsList(player);
            } else {
                MessageUtil.sendMessage(player, "&cUso corretto: /" + label + " <nome>");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            warpsCommand.onCommand(sender, command, "warps", new String[0]);
            return true;
        }

        if (sub.equals("set")) {
            String[] shifted = Arrays.copyOfRange(args, 1, args.length);
            return setWarpCommand.onCommand(sender, command, "setwarp", shifted);
        }

        if (sub.equals("del") || sub.equals("delete") || sub.equals("remove")) {
            String[] shifted = Arrays.copyOfRange(args, 1, args.length);
            return delWarpCommand.onCommand(sender, command, "delwarp", shifted);
        }

        if (!player.hasPermission("caciottacore.warp.use")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        String warpName = args[0];
        warpManager.teleportToWarp(player, warpName);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ROOT);

            for (String sub : List.of("list", "set", "del")) {
                if (sub.startsWith(prefix)) {
                    list.add(sub);
                }
            }

            for (Warp warp : warpManager.getWarps().values()) {
                if (warp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    list.add(warp.getName());
                }
            }
            return list;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("del") || args[0].equalsIgnoreCase("delete"))) {
            return delWarpCommand.onTabComplete(sender, command, "delwarp", new String[]{args[1]});
        }

        return List.of();
    }
}
