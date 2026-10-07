package it.caciotta.core.warp;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Comando /warps per visualizzare l'elenco interattivo di tutti i warp del server.
 */
public class WarpsCommand implements CommandExecutor {

    private final WarpManager warpManager;
    private final ConfigManager configManager;

    public WarpsCommand(CaciottaCore plugin) {
        this.warpManager = plugin.getWarpManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("caciottacore.warp.list")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        showWarpsList(player);
        return true;
    }

    public void showWarpsList(Player player) {
        Map<String, Warp> warps = warpManager.getWarps();
        if (warps.isEmpty()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.list-empty"));
            return;
        }

        String header = configManager.getFormatted("warp.list-header", Map.of(
                "count", String.valueOf(warps.size())
        ));
        MessageUtil.sendMessage(player, header);

        boolean isAdmin = player.hasPermission("caciottacore.warp.admin") || player.isOp();

        for (Warp warp : warps.values()) {
            String entryText = configManager.getFormatted("warp.list-entry", Map.of(
                    "name", warp.getName(),
                    "world", warp.getWorldName(),
                    "x", String.valueOf((int) warp.getX()),
                    "y", String.valueOf((int) warp.getY()),
                    "z", String.valueOf((int) warp.getZ()),
                    "creator", warp.getCreatorName()
            ));

            Component entryComp = MessageUtil.format(entryText);

            // Pulsante interattivo [Teletrasportati]
            Component teleportButton = Component.text(" [Teletrasportati]")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
                    .hoverEvent(HoverEvent.showText(Component.text("Clicca per andare al warp: " + warp.getName(), NamedTextColor.YELLOW)))
                    .clickEvent(ClickEvent.runCommand("/warp " + warp.getName()));

            entryComp = entryComp.append(teleportButton);

            // Pulsante [X] se il giocatore e il creatore o un amministratore
            boolean isCreator = player.getUniqueId().equals(warp.getCreatorUuid());
            if (isCreator || isAdmin) {
                Component deleteButton = Component.text(" [X]")
                        .color(NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("Clicca per eliminare il warp: " + warp.getName(), NamedTextColor.RED)))
                        .clickEvent(ClickEvent.suggestCommand("/delwarp " + warp.getName()));
                entryComp = entryComp.append(deleteButton);
            }

            player.sendMessage(entryComp);
        }
    }
}
