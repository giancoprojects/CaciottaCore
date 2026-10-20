package it.caciotta.core.event;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Gestore del comando /evento per giocatori e staff.
 */
public class EventCommand implements CommandExecutor, TabCompleter {

    private final CaciottaCore plugin;
    private final EventManager eventManager;
    private final ConfigManager configManager;

    public EventCommand(CaciottaCore plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.event.use")) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            showEventOverview(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Sezione Admin
        if (sub.equals("admin")) {
            handleAdminCommand(sender, label, args);
            return true;
        }

        switch (sub) {
            case "partecipa", "conferma", "join", "entra" -> {
                if (!(sender instanceof Player player)) {
                    MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
                    return true;
                }
                eventManager.confirmParticipation(player);
            }
            case "abbandona", "leave", "esci", "ritirati" -> {
                if (!(sender instanceof Player player)) {
                    MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
                    return true;
                }
                eventManager.leaveParticipation(player);
            }
            case "top", "classifica", "leaderboard" -> {
                showLeaderboard(sender);
            }
            case "info", "dettagli", "rules", "premi" -> {
                showEventInfo(sender);
            }
            case "dungeon" -> {
                if (!(sender instanceof Player player)) {
                    MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-only"));
                    return true;
                }
                eventManager.executeDungeonTeleport(player);
            }
            default -> {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.usage"));
            }
        }

        return true;
    }

    private void showEventOverview(CommandSender sender) {
        GameEvent event = eventManager.getCurrentEvent();
        if (event == null) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.no-event"));
            return;
        }

        sender.sendMessage(MessageUtil.format("&8&m--------------------------------------------------"));
        sender.sendMessage(MessageUtil.format("&6🏆 &e&lEVENTI A PREMI &8- &6" + event.getDisplayName()));
        sender.sendMessage(MessageUtil.format("&7Stato attuale: " + event.getState().getFormatted()));
        sender.sendMessage(MessageUtil.format("&7Data di inizio: &e" + eventManager.getEventStartFormatted()));
        sender.sendMessage(MessageUtil.format("&7Obbiettivo: &a" + eventManager.getEventTargetFormatted() + " &7blocchi scavati"));
        sender.sendMessage(MessageUtil.format("&7Partecipanti confermati: &b" + eventManager.getParticipantsCount()));

        if (sender instanceof Player player) {
            boolean isConfirmed = event.isParticipant(player.getUniqueId());
            int score = eventManager.getPlayerScore(player);
            String confirmedStatus = isConfirmed ? "&aConfermato ✔" : "&cNon confermato ✘";
            sender.sendMessage(MessageUtil.format("&7La tua iscrizione: " + confirmedStatus));
            sender.sendMessage(MessageUtil.format("&7I tuoi blocchi scavati: &e" + String.format(Locale.ITALY, "%,d", score) +
                    " &8(&a" + eventManager.getPlayerPercent(player) + "&8)"));
        }

        sender.sendMessage(Component.empty());

        // Pulsanti interattivi
        Component buttons = Component.empty();
        if (sender instanceof Player player) {
            if (!event.isParticipant(player.getUniqueId())) {
                buttons = buttons.append(Component.text("[CONFERMA PARTECIPAZIONE]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("Clicca qui per confermare la tua partecipazione all'evento!", NamedTextColor.AQUA)))
                        .clickEvent(ClickEvent.runCommand("/evento partecipa"))).append(Component.text("  "));
            } else {
                buttons = buttons.append(Component.text("[RITIRATI DALL'EVENTO]", NamedTextColor.RED, TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("Clicca qui per annullare la tua iscrizione", NamedTextColor.RED)))
                        .clickEvent(ClickEvent.runCommand("/evento abbandona"))).append(Component.text("  "));
            }
        }

        buttons = buttons.append(Component.text("[CLASSIFICA TOP]", NamedTextColor.GOLD, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Visualizza la classifica in tempo reale", NamedTextColor.YELLOW)))
                .clickEvent(ClickEvent.runCommand("/evento top"))).append(Component.text("  "));

        buttons = buttons.append(Component.text("[PREMI & DETTAGLI]", NamedTextColor.AQUA, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Visualizza i premi in palio per la Top 3", NamedTextColor.LIGHT_PURPLE)))
                .clickEvent(ClickEvent.runCommand("/evento info")));

        sender.sendMessage(buttons);
        sender.sendMessage(MessageUtil.format("&8&m--------------------------------------------------"));
    }

    private void showLeaderboard(CommandSender sender) {
        GameEvent event = eventManager.getCurrentEvent();
        if (event == null) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.no-event"));
            return;
        }

        sender.sendMessage(MessageUtil.format("&8&m--------------------------------------------------"));
        sender.sendMessage(MessageUtil.format(configManager.getRawMessage("event.top-header")
                .replace("{event}", event.getDisplayName())
                .replace("{target}", eventManager.getEventTargetFormatted())));

        List<Map.Entry<UUID, Integer>> top = eventManager.getLeaderboard(10);
        if (top.isEmpty()) {
            sender.sendMessage(MessageUtil.format(configManager.getRawMessage("event.top-empty")));
        } else {
            for (int i = 0; i < top.size(); i++) {
                Map.Entry<UUID, Integer> entry = top.get(i);
                String name = event.getParticipantName(entry.getKey());
                String scoreStr = String.format(Locale.ITALY, "%,d", entry.getValue());
                String status = event.isWinner(entry.getKey()) ? "&a[COMPLETATO ✔]" : "";

                sender.sendMessage(MessageUtil.format(configManager.getRawMessage("event.top-entry")
                        .replace("{rank}", String.valueOf(i + 1))
                        .replace("{player}", name)
                        .replace("{score}", scoreStr)
                        .replace("{status}", status)));
            }
        }
        sender.sendMessage(MessageUtil.format("&8&m--------------------------------------------------"));
    }

    private void showEventInfo(CommandSender sender) {
        GameEvent event = eventManager.getCurrentEvent();
        if (event == null) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.no-event"));
            return;
        }

        sender.sendMessage(MessageUtil.format("&8&m--------------------------------------------------"));
        sender.sendMessage(MessageUtil.format("&6🏆 &e&lDETTAGLI & PREMI - &6" + event.getDisplayName()));
        sender.sendMessage(MessageUtil.format("&7Descrizione: &f" + event.getDescription()));
        sender.sendMessage(MessageUtil.format("&7Obbiettivo da raggiungere: &a" + eventManager.getEventTargetFormatted() + " blocchi scavati"));
        sender.sendMessage(MessageUtil.format("&7Data di inizio: &e" + eventManager.getEventStartFormatted()));
        sender.sendMessage(MessageUtil.format("&7Condizione di fine: &fIl primo completamento della Top 3 giocatori"));
        sender.sendMessage(Component.empty());
        sender.sendMessage(MessageUtil.format("&6🎁 &e&lPREMI IN PALIO PER I VINCITORI DELLA TOP 3:"));
        sender.sendMessage(MessageUtil.format("&7I 3 vincitori che completano l'obbiettivo possono scegliere uno tra questi premi (assegnato dallo staff):"));
        sender.sendMessage(MessageUtil.format(" &8» &6Rank Scavatore &7(15 Home, zaino 5 righe, chat colorata, tpback illimitato)"));
        sender.sendMessage(MessageUtil.format(" &8» &5Fullset Netherite &7con enchant a sorpresa di livello massimo"));
        sender.sendMessage(MessageUtil.format(" &8» &dTeletrasporto a strutture / dungeon inesplorate &7(Trial Chambers / Ancient City)"));
        sender.sendMessage(Component.empty());
        sender.sendMessage(MessageUtil.format("&eNota: &7E obbligatorio confermare la partecipazione con &e/evento partecipa &7per conteggiare i blocchi!"));
        sender.sendMessage(MessageUtil.format("&8&m--------------------------------------------------"));
    }

    private void handleAdminCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("caciottacore.admin") && !sender.hasPermission("caciottacore.event.admin") && !sender.isOp()) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.usage-admin"));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "start", "avvia", "forzastart" -> {
                String eventId = (args.length >= 3) ? args[2] : null;
                eventManager.forceStart(sender, eventId);
            }
            case "stop", "interrompi", "termina" -> {
                eventManager.stopEvent(sender);
            }
            case "kick", "espelli" -> {
                if (args.length < 3) {
                    MessageUtil.sendMessage(sender, "&cUso: &e/" + label + " admin kick <giocatore>");
                    return;
                }
                String targetName = args[2];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                boolean kicked = eventManager.kickParticipant(target);
                if (kicked) {
                    MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.admin-kicked",
                            Map.of("target", targetName, "event", eventManager.getEventName())));
                } else {
                    MessageUtil.sendMessage(sender, "&cIl giocatore &e" + targetName + " &cnon e iscritto all'evento corrente.");
                }
            }
            case "setprogress", "setscore" -> {
                if (args.length < 4) {
                    MessageUtil.sendMessage(sender, "&cUso: &e/" + label + " admin setprogress <giocatore> <valore>");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("player-not-found", Map.of("target", args[2])));
                    return;
                }
                try {
                    int val = Integer.parseInt(args[3]);
                    eventManager.setPlayerProgress(sender, target, val);
                } catch (NumberFormatException e) {
                    MessageUtil.sendMessage(sender, "&cIl valore deve essere un numero intero!");
                }
            }
            case "reset" -> {
                eventManager.resetCurrentEvent(sender);
            }
            case "reload" -> {
                eventManager.reload();
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.admin-reloaded"));
            }
            case "giveprize", "premio", "assegnapremio" -> {
                if (args.length < 4) {
                    MessageUtil.sendMessage(sender, "&cUso: &e/" + label + " admin giveprize <giocatore> <rank|netherite|dungeon>");
                    return;
                }
                String targetName = args[2];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                String prizeKey = args[3];
                eventManager.givePrize(sender, target, prizeKey);
            }
            default -> {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.usage-admin"));
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("caciottacore.event.use")) {
            return List.of();
        }

        boolean isAdmin = sender.hasPermission("caciottacore.admin") || sender.hasPermission("caciottacore.event.admin") || sender.isOp();

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            List<String> options = new ArrayList<>(List.of("partecipa", "abbandona", "top", "classifica", "info", "dungeon"));
            if (isAdmin) {
                options.add("admin");
            }
            for (String s : options) {
                if (s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    list.add(s);
                }
            }
            return list;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && isAdmin) {
            List<String> list = new ArrayList<>();
            for (String s : List.of("start", "stop", "kick", "setprogress", "reset", "reload", "giveprize")) {
                if (s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    list.add(s);
                }
            }
            return list;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && isAdmin) {
            String adminAction = args[1].toLowerCase(Locale.ROOT);
            if (adminAction.equals("kick") || adminAction.equals("setprogress")) {
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                        list.add(p.getName());
                    }
                }
                return list;
            }
            if (adminAction.equals("giveprize") || adminAction.equals("premio") || adminAction.equals("assegnapremio")) {
                List<String> list = new ArrayList<>();
                GameEvent ev = eventManager.getCurrentEvent();
                if (ev != null) {
                    for (UUID winId : ev.getWinners()) {
                        String name = ev.getParticipantName(winId);
                        if (name != null && name.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                            list.add(name);
                        }
                    }
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!list.contains(p.getName()) && p.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                        list.add(p.getName());
                    }
                }
                return list;
            }
            if (adminAction.equals("start")) {
                return List.of("scavatore");
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && isAdmin) {
            String adminAction = args[1].toLowerCase(Locale.ROOT);
            if (adminAction.equals("giveprize") || adminAction.equals("premio") || adminAction.equals("assegnapremio")) {
                List<String> list = new ArrayList<>();
                for (String p : List.of("rank", "netherite", "dungeon")) {
                    if (p.startsWith(args[3].toLowerCase(Locale.ROOT))) {
                        list.add(p);
                    }
                }
                return list;
            }
        }

        return List.of();
    }
}
