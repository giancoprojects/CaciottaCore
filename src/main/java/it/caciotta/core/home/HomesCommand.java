package it.caciotta.core.home;

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
 * Comando /homes per visualizzare l'elenco interattivo delle home.
 */
public class HomesCommand implements CommandExecutor {

    private final HomeManager homeManager;
    private final ConfigManager configManager;

    public HomesCommand(CaciottaCore plugin) {
        this.homeManager = plugin.getHomeManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("caciottacore.home.list")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        Map<String, Home> homes = homeManager.getHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("home.list-empty"));
            return true;
        }

        int max = homeManager.getMaxHomes(player);
        String header = configManager.getFormatted("home.list-header", Map.of(
                "count", String.valueOf(homes.size()),
                "max", max >= 9999 ? "∞" : String.valueOf(max)
        ));
        MessageUtil.sendMessage(player, header);

        for (Home home : homes.values()) {
            String entryText = configManager.getFormatted("home.list-entry", Map.of(
                    "name", home.getName(),
                    "world", home.getWorldName(),
                    "x", String.valueOf((int) home.getX()),
                    "y", String.valueOf((int) home.getY()),
                    "z", String.valueOf((int) home.getZ())
            ));

            Component entryComp = MessageUtil.format(entryText);

            // Pulsante cliccabile [Teletrasportati]
            Component teleportButton = Component.text(" [Teletrasportati]")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
                    .hoverEvent(HoverEvent.showText(Component.text("Clicca per andare alla home: " + home.getName(), NamedTextColor.YELLOW)))
                    .clickEvent(ClickEvent.runCommand("/home " + home.getName()));

            // Pulsante cliccabile [Elimina]
            Component deleteButton = Component.text(" [X]")
                    .color(NamedTextColor.RED)
                    .hoverEvent(HoverEvent.showText(Component.text("Clicca per eliminare la home: " + home.getName(), NamedTextColor.RED)))
                    .clickEvent(ClickEvent.suggestCommand("/delhome " + home.getName()));

            player.sendMessage(entryComp.append(teleportButton).append(deleteButton));
        }

        return true;
    }
}
