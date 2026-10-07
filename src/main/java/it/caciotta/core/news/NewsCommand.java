package it.caciotta.core.news;

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

/**
 * Comando /news (alias /novita, /updates) per visualizzare il libro delle novità del server
 * o impostare la preferenza di apertura/notifica all'accesso.
 */
public class NewsCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final NewsManager newsManager;
    private final ConfigManager configManager;

    public NewsCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.newsManager = plugin.getNewsManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("caciottacore.news.admin") && !sender.hasPermission("caciottacore.admin") && !sender.isOp()) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
                return true;
            }
            configManager.reload();
            newsManager.reload();
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("news.reloaded"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("caciottacore.news.use")) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("toggle") || args[0].equalsIgnoreCase("notifica"))) {
            boolean nowChatMode = newsManager.toggleChatMode(player.getUniqueId());
            if (nowChatMode) {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("news.toggled-to-chat"));
            } else {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("news.toggled-to-book"));
            }
            return true;
        }

        if (!newsManager.hasNews()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("news.no-news"));
            return true;
        }

        newsManager.openNewsBook(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("toggle");
            boolean isAdmin = sender.hasPermission("caciottacore.news.admin") || sender.hasPermission("caciottacore.admin") || sender.isOp();
            if (isAdmin) {
                list.add("reload");
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String sub : list) {
                if (sub.startsWith(prefix)) {
                    matches.add(sub);
                }
            }
            return matches;
        }
        return List.of();
    }
}
