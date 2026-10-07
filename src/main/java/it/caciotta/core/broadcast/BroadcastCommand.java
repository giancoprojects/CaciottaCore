package it.caciotta.core.broadcast;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Gestione del comando /broadcast (/bc, /annuncio, /alert) per inviare annunci globali.
 * Supporta annunci in chat con banner stilizzato, title a schermo intero e actionbar.
 */
public class BroadcastCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;

    public BroadcastCommand(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("caciottacore.broadcast") && !sender.isOp()) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("title")) {
            if (args.length < 2) {
                MessageUtil.sendMessage(sender, "&cUso: &e/" + label + " title <titolo> [| sottotitolo]");
                return true;
            }
            String fullText = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            broadcastTitle(fullText);
            return true;
        }

        if (sub.equals("actionbar") || sub.equals("ab")) {
            if (args.length < 2) {
                MessageUtil.sendMessage(sender, "&cUso: &e/" + label + " actionbar <messaggio>");
                return true;
            }
            String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            broadcastActionBar(message);
            return true;
        }

        // Standard chat broadcast
        String message = String.join(" ", args);
        broadcastChat(message);

        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        MessageUtil.sendMessage(sender, "&6=== &eComando Broadcast - CaciottaCore &6===");
        MessageUtil.sendMessage(sender, "&e/" + label + " <messaggio> &7- Invia un annuncio in chat a tutti");
        MessageUtil.sendMessage(sender, "&e/" + label + " title <titolo> [| sottotitolo] &7- Mostra un annuncio a schermo intero");
        MessageUtil.sendMessage(sender, "&e/" + label + " actionbar <messaggio> &7- Invia un annuncio nell'actionbar");
    }

    /**
     * Trasmette un annuncio in chat a tutti i giocatori e in console.
     */
    public void broadcastChat(String message) {
        playNotificationSound();

        boolean fancyBanner = configManager.isBroadcastFancyBanner();
        if (fancyBanner) {
            String header = configManager.getBroadcastBannerHeader();
            String title = configManager.getBroadcastBannerTitle();
            String footer = configManager.getBroadcastBannerFooter();

            List<String> lines = new ArrayList<>();
            lines.add("");
            if (header != null && !header.isEmpty()) lines.add(header);
            if (title != null && !title.isEmpty()) lines.add(title);
            lines.add("");
            lines.add("  " + message);
            lines.add("");
            if (footer != null && !footer.isEmpty()) lines.add(footer);
            lines.add("");

            for (Player player : Bukkit.getOnlinePlayers()) {
                for (String line : lines) {
                    MessageUtil.sendMessage(player, line);
                }
            }
            // Invia anche alla console
            for (String line : lines) {
                MessageUtil.sendMessage(Bukkit.getConsoleSender(), line);
            }
        } else {
            String prefix = configManager.getBroadcastPrefix();
            String full = prefix + message;
            for (Player player : Bukkit.getOnlinePlayers()) {
                MessageUtil.sendMessage(player, full);
            }
            MessageUtil.sendMessage(Bukkit.getConsoleSender(), full);
        }
    }

    /**
     * Trasmette un annuncio a schermo intero con Title e opzionale Sottotitolo.
     */
    public void broadcastTitle(String fullText) {
        playNotificationSound();

        String mainTitle;
        String subtitle = "";

        if (fullText.contains("|")) {
            String[] split = fullText.split("\\|", 2);
            mainTitle = split[0].trim();
            subtitle = split[1].trim();
        } else {
            mainTitle = fullText.trim();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            MessageUtil.sendTitle(player, mainTitle, subtitle, 10, 70, 20);
        }

        MessageUtil.sendMessage(Bukkit.getConsoleSender(), "&8[&4&lBROADCAST TITLE&8]&r &f" + mainTitle + (subtitle.isEmpty() ? "" : " &7| &e" + subtitle));
    }

    /**
     * Trasmette un annuncio nell'Action Bar sopra la barra degli oggetti.
     */
    public void broadcastActionBar(String message) {
        playNotificationSound();

        for (Player player : Bukkit.getOnlinePlayers()) {
            MessageUtil.sendActionBar(player, message);
        }

        MessageUtil.sendMessage(Bukkit.getConsoleSender(), "&8[&4&lBROADCAST ACTIONBAR&8]&r &f" + message);
    }

    @SuppressWarnings({"deprecation", "removal"})
    private void playNotificationSound() {
        if (!configManager.isBroadcastPlaySound()) {
            return;
        }

        String soundName = configManager.getBroadcastSound();
        Sound sound = Sound.BLOCK_NOTE_BLOCK_PLING;
        try {
            sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("title", "actionbar"));
            String lower = args[0].toLowerCase(Locale.ROOT);
            options.removeIf(opt -> !opt.startsWith(lower));
            return options;
        }
        return Collections.emptyList();
    }
}
