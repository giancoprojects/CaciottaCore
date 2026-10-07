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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comando /delwarp <nome> per eliminare un warp.
 * Il creatore del warp o gli amministratori possono eliminare il warp.
 */
public class DelWarpCommand implements CommandExecutor, TabCompleter {

    private final WarpManager warpManager;
    private final ConfigManager configManager;

    public DelWarpCommand(CaciottaCore plugin) {
        this.warpManager = plugin.getWarpManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("caciottacore.warp.delete")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(player, "&cUso corretto: /" + label + " <nome>");
            return true;
        }

        String name = args[0];
        WarpManager.DeleteResult result = warpManager.deleteWarp(player, name);

        switch (result) {
            case SUCCESS -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.deleted",
                    Map.of("name", name)));
            case NOT_FOUND -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.not-found",
                    Map.of("name", name)));
            case NOT_OWNER -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.delete-not-owner",
                    Map.of("name", name)));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            boolean isAdmin = player.hasPermission("caciottacore.warp.admin") || player.isOp();
            List<String> list = new ArrayList<>();
            for (Warp warp : warpManager.getWarps().values()) {
                if (isAdmin || player.getUniqueId().equals(warp.getCreatorUuid())) {
                    if (warp.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                        list.add(warp.getName());
                    }
                }
            }
            return list;
        }
        return List.of();
    }
}
