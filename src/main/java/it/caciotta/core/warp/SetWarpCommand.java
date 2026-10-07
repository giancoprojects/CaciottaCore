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
 * Comando /setwarp <nome> per creare un punto di teletrasporto pubblico.
 * Accessibile a tutti i giocatori (con limite configurabile).
 */
public class SetWarpCommand implements CommandExecutor, TabCompleter {

    private final WarpManager warpManager;
    private final ConfigManager configManager;

    public SetWarpCommand(CaciottaCore plugin) {
        this.warpManager = plugin.getWarpManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("caciottacore.warp.set")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(player, "&cUso corretto: /" + label + " <nome>");
            return true;
        }

        String name = args[0];
        WarpManager.SetResult result = warpManager.setWarp(player, name, player.getLocation());

        switch (result) {
            case SUCCESS -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.set-success",
                    Map.of("name", name)));
            case UPDATED -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.updated-success",
                    Map.of("name", name)));
            case LIMIT_REACHED -> {
                int limit = warpManager.getMaxWarps(player);
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.set-limit-reached",
                        Map.of("limit", String.valueOf(limit))));
            }
            case ALREADY_EXISTS_OTHER -> {
                Warp existing = warpManager.getWarp(name);
                String creator = existing != null ? existing.getCreatorName() : "un altro utente";
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.already-exists-by-other",
                        Map.of("name", name, "creator", creator)));
            }
            case INVALID_NAME -> MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.invalid-name"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (Warp warp : warpManager.getWarps().values()) {
                if (player.getUniqueId().equals(warp.getCreatorUuid())
                        && warp.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    list.add(warp.getName());
                }
            }
            return list;
        }
        return List.of();
    }
}
