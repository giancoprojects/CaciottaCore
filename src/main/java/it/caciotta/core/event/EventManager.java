package it.caciotta.core.event;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import it.caciotta.core.rank.Rank;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StructureSearchResult;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestore principale del sistema degli Eventi a Premi di CaciottaCore.
 * Gestisce il ciclo di vita degli eventi (programmato, in corso, concluso),
 * la registrazione dei partecipanti con obbligo di conferma, il tracciamento
 * dei progressi in tempo reale, l'assegnazione automatica dei premi alla top 3
 * e la persistenza dei dati.
 */
public class EventManager {

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final File eventsConfigFile;
    private final File dataFile;

    private YamlConfiguration eventsConfig;
    private YamlConfiguration dataConfig;

    private final Map<String, GameEvent> availableEvents = new ConcurrentHashMap<>();
    private GameEvent currentEvent;

    // Giocatori che hanno vinto il teletrasporto a un dungeon (UUID)
    private final Set<UUID> pendingDungeonTeleports = ConcurrentHashMap.newKeySet();

    private BukkitTask schedulerTask;
    private boolean enabled = true;
    private boolean allowJoinDuringEvent = true;
    private boolean broadcastMilestones = true;
    private int milestoneInterval = 2500;

    public EventManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.eventsConfigFile = new File(plugin.getDataFolder(), "events.yml");
        this.dataFile = new File(plugin.getDataFolder(), "data/events_data.yml");

        loadConfig();
        loadData();
        startTask();
    }

    /**
     * Carica o ricarica la configurazione da events.yml.
     */
    public synchronized void loadConfig() {
        if (!eventsConfigFile.exists()) {
            try {
                plugin.saveResource("events.yml", false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Impossibile estrarre events.yml dal jar", e);
            }
        }

        eventsConfig = YamlConfiguration.loadConfiguration(eventsConfigFile);
        this.enabled = eventsConfig.getBoolean("enabled", true);
        this.allowJoinDuringEvent = eventsConfig.getBoolean("allow-join-during-event", true);
        this.broadcastMilestones = eventsConfig.getBoolean("broadcast-milestones", true);
        this.milestoneInterval = eventsConfig.getInt("milestone-interval", 2500);

        availableEvents.clear();
        ConfigurationSection sec = eventsConfig.getConfigurationSection("events");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection eSec = sec.getConfigurationSection(key);
                if (eSec == null) continue;

                String name = eSec.getString("name", key);
                String desc = eSec.getString("description", "");
                String typeStr = eSec.getString("type", "BLOCK_BREAK");
                EventType type;
                try {
                    type = EventType.valueOf(typeStr.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    type = EventType.BLOCK_BREAK;
                }
                int target = eSec.getInt("target", 10000);
                String startDateRaw = eSec.getString("start-date", "25/10/2026 21:00");
                ZonedDateTime startDate = parseDate(startDateRaw);

                GameEvent event = new GameEvent(key, name, desc, type, target, startDateRaw, startDate);
                availableEvents.put(event.getId(), event);
            }
        }

        String activeId = eventsConfig.getString("active-event", "scavatore").toLowerCase(Locale.ROOT);
        GameEvent configuredEvent = availableEvents.get(activeId);
        if (configuredEvent == null && !availableEvents.isEmpty()) {
            configuredEvent = availableEvents.values().iterator().next();
        }

        if (configuredEvent != null) {
            if (currentEvent == null || !currentEvent.getId().equalsIgnoreCase(configuredEvent.getId())) {
                currentEvent = configuredEvent;
            } else {
                // Aggiorna tutti i campi configurabili da events.yml sull'evento in memoria
                currentEvent.setDisplayName(configuredEvent.getDisplayName());
                currentEvent.setDescription(configuredEvent.getDescription());
                currentEvent.setType(configuredEvent.getType());
                currentEvent.setTarget(configuredEvent.getTarget());
                currentEvent.setStartDateRaw(configuredEvent.getStartDateRaw());
                currentEvent.setStartDate(configuredEvent.getStartDate());

                // Se la nuova data configurata è nel futuro, assicura che lo stato sia SCHEDULED
                if (configuredEvent.getStartDate() != null && configuredEvent.getStartDate().isAfter(ZonedDateTime.now(configManager.getZoneId()))) {
                    currentEvent.setState(EventState.SCHEDULED);
                }
            }
        }
    }

    private ZonedDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return ZonedDateTime.now(configManager.getZoneId()).plusDays(1);
        }
        String trimmed = raw.trim();
        List<DateTimeFormatter> formatters = List.of(
                DATE_TIME_FORMATTER,
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
        );
        for (DateTimeFormatter fmt : formatters) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(trimmed, fmt);
                return ldt.atZone(configManager.getZoneId());
            } catch (Exception ignored) {
            }
        }
        plugin.getLogger().warning("Formato data non valido per evento: '" + raw + "'. Usa 'dd/MM/yyyy HH:mm'");
        return ZonedDateTime.now(configManager.getZoneId()).plusDays(1);
    }

    /**
     * Carica i dati persistenti da data/events_data.yml.
     */
    public synchronized void loadData() {
        if (!dataFile.exists()) {
            try {
                if (dataFile.getParentFile() != null && !dataFile.getParentFile().exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    dataFile.getParentFile().mkdirs();
                }
                //noinspection ResultOfMethodCallIgnored
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Impossibile creare data/events_data.yml", e);
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (currentEvent == null) {
            return;
        }

        String stateStr = dataConfig.getString(currentEvent.getId() + ".state", EventState.SCHEDULED.name());
        try {
            EventState state = EventState.valueOf(stateStr);
            // Se la data configurata è nel futuro, l'evento deve rimanere o tornare in SCHEDULED
            if (state == EventState.ENDED && currentEvent.getStartDate() != null && currentEvent.getStartDate().isAfter(ZonedDateTime.now(configManager.getZoneId()))) {
                state = EventState.SCHEDULED;
            }
            currentEvent.setState(state);
        } catch (IllegalArgumentException e) {
            currentEvent.setState(EventState.SCHEDULED);
        }

        // Carica partecipanti
        List<String> partList = dataConfig.getStringList(currentEvent.getId() + ".participants");
        for (String s : partList) {
            try {
                UUID uuid = UUID.fromString(s);
                String name = dataConfig.getString(currentEvent.getId() + ".participant_names." + s, "Sconosciuto");
                currentEvent.addParticipant(uuid, name);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Carica punteggi
        ConfigurationSection scoreSec = dataConfig.getConfigurationSection(currentEvent.getId() + ".scores");
        if (scoreSec != null) {
            for (String key : scoreSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int score = scoreSec.getInt(key, 0);
                    currentEvent.setScore(uuid, score);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // Carica vincitori
        List<String> winList = dataConfig.getStringList(currentEvent.getId() + ".winners");
        for (String s : winList) {
            try {
                UUID uuid = UUID.fromString(s);
                currentEvent.addWinner(uuid);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Carica teletrasporti dungeon pendenti
        List<String> dungeonList = dataConfig.getStringList("pending_dungeons");
        for (String s : dungeonList) {
            try {
                pendingDungeonTeleports.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /**
     * Salva i dati correnti su disco.
     */
    public synchronized void saveData() {
        if (dataConfig == null || currentEvent == null) {
            return;
        }

        String prefix = currentEvent.getId() + ".";
        dataConfig.set(prefix + "state", currentEvent.getState().name());

        // Pulisce preventivamente le sezioni per rimuovere chiavi di giocatori espulsi o azzerati
        dataConfig.set(prefix + "participant_names", null);
        dataConfig.set(prefix + "scores", null);

        List<String> partList = new ArrayList<>();
        for (UUID uuid : currentEvent.getParticipants()) {
            partList.add(uuid.toString());
            dataConfig.set(prefix + "participant_names." + uuid, currentEvent.getParticipantName(uuid));
        }
        dataConfig.set(prefix + "participants", partList);

        for (Map.Entry<UUID, Integer> entry : currentEvent.getScores().entrySet()) {
            dataConfig.set(prefix + "scores." + entry.getKey().toString(), entry.getValue());
        }

        List<String> winList = new ArrayList<>();
        for (UUID uuid : currentEvent.getWinners()) {
            winList.add(uuid.toString());
        }
        dataConfig.set(prefix + "winners", winList);

        List<String> dungList = new ArrayList<>();
        for (UUID uuid : pendingDungeonTeleports) {
            dungList.add(uuid.toString());
        }
        dataConfig.set("pending_dungeons", dungList);

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare data/events_data.yml", e);
        }
    }

    /**
     * Avvia il task periodico per il controllo dell'orario di inizio e la notifica degli eventi.
     */
    public void startTask() {
        stopTask();
        if (!enabled) return;

        this.schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stopTask() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }

    private void tick() {
        if (!enabled || currentEvent == null) {
            return;
        }

        // Se l'evento è programmato in attesa di inizio, controlla la data di avvio
        if (currentEvent.isScheduled() && currentEvent.getStartDate() != null) {
            ZonedDateTime now = ZonedDateTime.now(configManager.getZoneId());
            if (now.isEqual(currentEvent.getStartDate()) || now.isAfter(currentEvent.getStartDate())) {
                startEvent();
            }
        }
    }

    /**
     * Avvia ufficialmente l'evento corrente.
     */
    public synchronized void startEvent() {
        if (currentEvent == null || currentEvent.isRunning()) {
            return;
        }

        currentEvent.setState(EventState.RUNNING);
        saveData();

        // Annuncio globale in chat
        String broadcastMsg = configManager.getRawMessage("event.broadcast-start")
                .replace("{event}", currentEvent.getDisplayName());
        Bukkit.broadcast(MessageUtil.format(broadcastMsg));

        // Suono ed effetto per tutti i giocatori online
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            p.showTitle(Title.title(
                    MessageUtil.format("&6&lEVENTO INIZIATO!"),
                    MessageUtil.format("&e" + currentEvent.getDisplayName() + " &7- Che vincano i migliori 3!"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000))
            ));
        }

        // Ricarica la scoreboard per mostrare a tutti la scoreboard dell'evento
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().updateAll();
        }

        plugin.getLogger().info("Evento " + currentEvent.getId() + " avviato con successo!");
    }

    /**
     * Conclude l'evento corrente con festeggiamenti e proclamazione del podio.
     */
    public synchronized void endEvent() {
        if (currentEvent == null || currentEvent.isEnded()) {
            return;
        }

        currentEvent.setState(EventState.ENDED);
        saveData();

        List<UUID> winners = currentEvent.getWinners();
        String w1 = winners.size() > 0 ? currentEvent.getParticipantName(winners.get(0)) : "Nessuno";
        String w2 = winners.size() > 1 ? currentEvent.getParticipantName(winners.get(1)) : "Nessuno";
        String w3 = winners.size() > 2 ? currentEvent.getParticipantName(winners.get(2)) : "Nessuno";

        String endMsg = configManager.getRawMessage("event.broadcast-end")
                .replace("{event}", currentEvent.getDisplayName())
                .replace("{winner1}", w1)
                .replace("{winner2}", w2)
                .replace("{winner3}", w3);
        Bukkit.broadcast(MessageUtil.format(endMsg));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
        }

        // Aggiorna le scoreboard
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().updateAll();
        }

        plugin.getLogger().info("Evento " + currentEvent.getId() + " terminato! Podio: 1° " + w1 + ", 2° " + w2 + ", 3° " + w3);
    }

    /**
     * Conferma la partecipazione obbligatoria di un giocatore all'evento.
     */
    public boolean confirmParticipation(Player player) {
        if (player == null || currentEvent == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (currentEvent.isParticipant(uuid)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("event.already-joined"));
            return false;
        }

        if (currentEvent.isEnded()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("event.not-running",
                    Map.of("event", currentEvent.getDisplayName(), "status", currentEvent.getState().getDisplayName())));
            return false;
        }

        if (currentEvent.isRunning() && !allowJoinDuringEvent) {
            MessageUtil.sendMessage(player, "&cLe iscrizioni a questo evento sono state chiuse all'avvio.");
            return false;
        }

        currentEvent.addParticipant(uuid, player.getName());
        saveData();

        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("event.join-success",
                Map.of("event", currentEvent.getDisplayName())));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().update(player);
        }
        return true;
    }

    /**
     * Ritiro volontario dall'evento.
     */
    public boolean leaveParticipation(Player player) {
        if (player == null || currentEvent == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (!currentEvent.isParticipant(uuid)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("event.not-joined"));
            return false;
        }

        currentEvent.removeParticipant(uuid);
        saveData();

        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("event.leave-success",
                Map.of("event", currentEvent.getDisplayName())));

        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().update(player);
        }
        return true;
    }

    /**
     * Espelle un giocatore dalla partecipazione all'evento (sia prima che durante l'evento).
     */
    public boolean kickParticipant(OfflinePlayer target) {
        if (target == null || currentEvent == null) {
            return false;
        }

        UUID uuid = target.getUniqueId();
        boolean removed = currentEvent.removeParticipant(uuid);
        currentEvent.setScore(uuid, 0);
        saveData();

        if (target.isOnline() && target.getPlayer() != null) {
            MessageUtil.sendMessage(target.getPlayer(), configManager.getPrefixedMessage("event.kicked-notify"));
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().update(target.getPlayer());
            }
        }
        return removed;
    }

    /**
     * Registra i progressi fatti da un giocatore (es. blocco scavato).
     */
    public void recordProgress(Player player, int amount) {
        if (!enabled || currentEvent == null || !currentEvent.isRunning() || player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // Obbligo di conferma: se non ha confermato la partecipazione, i blocchi non contano!
        if (!currentEvent.isParticipant(uuid)) {
            return;
        }

        // Se ha già vinto uno dei posti sul podio, non incrementare ulteriormente
        if (currentEvent.isWinner(uuid)) {
            return;
        }

        int prevScore = currentEvent.getScore(uuid);
        int newScore = currentEvent.addScore(uuid, amount);

        // Controllo pietre miliari (es. ogni 2500 blocchi)
        if (broadcastMilestones && milestoneInterval > 0) {
            int prevMilestone = prevScore / milestoneInterval;
            int newMilestone = newScore / milestoneInterval;
            if (newMilestone > prevMilestone && newScore < currentEvent.getTarget()) {
                String mMsg = configManager.getRawMessage("event.broadcast-milestone")
                        .replace("{player}", player.getName())
                        .replace("{score}", String.format(Locale.ITALY, "%,d", newScore))
                        .replace("{target}", String.format(Locale.ITALY, "%,d", currentEvent.getTarget()))
                        .replace("{event}", currentEvent.getDisplayName());
                Bukkit.broadcast(MessageUtil.format(mMsg));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
            }
        }

        // Controllo raggiungimento obbiettivo
        if (newScore >= currentEvent.getTarget()) {
            handleWinnerAchievement(player);
        }
    }

    private synchronized void handleWinnerAchievement(Player player) {
        UUID uuid = player.getUniqueId();
        if (currentEvent.isWinner(uuid)) {
            return;
        }

        boolean added = currentEvent.addWinner(uuid);
        if (!added) return;

        int place = currentEvent.getWinners().size();
        saveData();

        // Annuncio globale in chat (i premi NON vengono assegnati automaticamente in base alla posizione)
        String winMsg = configManager.getRawMessage("event.broadcast-winner")
                .replace("{player}", player.getName())
                .replace("{place}", String.valueOf(place));
        Bukkit.broadcast(MessageUtil.format(winMsg));

        // Notifica e suoni epici per il vincitore
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.showTitle(Title.title(
                MessageUtil.format("&6&l" + place + "° POSTO RAGGIUNTO!"),
                MessageUtil.format("&eComunica all'admin il premio che hai scelto!"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(4000), Duration.ofMillis(1000))
        ));

        // Se tutti e 3 i posti della top 3 sono completati, l'evento termina!
        if (currentEvent.isTop3Complete()) {
            endEvent();
        }
    }

    /**
     * Assegna manualmente un premio a scelta a un vincitore o giocatore da parte dello staff.
     */
    public boolean givePrize(CommandSender admin, OfflinePlayer target, String prizeKey) {
        if (target == null || prizeKey == null) return false;

        String key = prizeKey.toLowerCase(Locale.ROOT);
        String prizeName;

        switch (key) {
            case "rank", "scavatore", "1" -> {
                prizeName = "&6Rank Scavatore &7(15 Home, zaino da 5 righe, chat colorata, tpback illimitato)";
                if (plugin.getRankManager() != null) {
                    Rank scavatoreRank = plugin.getRankManager().getRank("scavatore");
                    if (scavatoreRank != null) {
                        plugin.getRankManager().setPlayerRank(target.getUniqueId(), scavatoreRank, target.getName() != null ? target.getName() : "Sconosciuto");
                    }
                }
            }
            case "netherite", "set", "fullset", "armatura", "2" -> {
                prizeName = "&5Fullset Netherite Leggendario &7con enchant a sorpresa";
                Player online = target.getPlayer();
                if (online == null || !online.isOnline()) {
                    MessageUtil.sendMessage(admin, "&cIl giocatore &e" + target.getName() + " &cdeve essere online per ricevere gli oggetti nell'inventario!");
                    return false;
                }
                giveSurpriseNetheriteFullset(online);
            }
            case "dungeon", "struttura", "teleport", "pergamena", "3" -> {
                prizeName = "&dTeletrasporto a strutture / dungeon inesplorate";
                pendingDungeonTeleports.add(target.getUniqueId());
                saveData();
                Player online = target.getPlayer();
                if (online != null && online.isOnline()) {
                    giveDungeonScroll(online);
                }
            }
            default -> {
                MessageUtil.sendMessage(admin, configManager.getPrefixedMessage("event.admin-prize-unknown", Map.of("prize", prizeKey)));
                return false;
            }
        }

        // Salva il premio assegnato nei dati persistenti
        if (currentEvent != null && dataConfig != null) {
            dataConfig.set(currentEvent.getId() + ".assigned_prizes." + target.getUniqueId(), key);
            saveData();
        }

        MessageUtil.sendMessage(admin, configManager.getPrefixedMessage("event.admin-prize-given",
                Map.of("prize", prizeName, "target", target.getName() != null ? target.getName() : "Giocatore", "event", getEventName())));

        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            MessageUtil.sendMessage(online, configManager.getPrefixedMessage("event.prize-received",
                    Map.of("prize", prizeName, "event", getEventName())));
            online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        return true;
    }

    public String awardPrize(Player player, int place) {
        String prizeKey = switch (place) {
            case 1 -> "rank";
            case 2 -> "netherite";
            case 3 -> "dungeon";
            default -> "rank";
        };
        givePrize(Bukkit.getConsoleSender(), player, prizeKey);
        return prizeKey;
    }

    public String awardPrize(Player player, String prizeKey) {
        givePrize(Bukkit.getConsoleSender(), player, prizeKey);
        return prizeKey;
    }

    /**
     * Crea e consegna al giocatore il Fullset in Netherite con enchant a sorpresa.
     */
    private void giveSurpriseNetheriteFullset(Player player) {
        ItemStack helmet = createEnchantedGear(Material.NETHERITE_HELMET, "&6&lElmo dello Scavatore Leggendario",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1));

        ItemStack chestplate = createEnchantedGear(Material.NETHERITE_CHESTPLATE, "&6&lCorazza dello Scavatore Leggendario",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.THORNS, 3));

        ItemStack leggings = createEnchantedGear(Material.NETHERITE_LEGGINGS, "&6&lGambiere dello Scavatore Leggendario",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.SWIFT_SNEAK, 3));

        ItemStack boots = createEnchantedGear(Material.NETHERITE_BOOTS, "&6&lStivali dello Scavatore Leggendario",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FEATHER_FALLING, 4, Enchantment.DEPTH_STRIDER, 3, Enchantment.SOUL_SPEED, 3));

        deliverItem(player, helmet);
        deliverItem(player, chestplate);
        deliverItem(player, leggings);
        deliverItem(player, boots);
    }

    private ItemStack createEnchantedGear(Material mat, String name, Map<Enchantment, Integer> enchants) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.format(name));
            List<Component> lore = List.of(
                    MessageUtil.format("&7&m----------------------------"),
                    MessageUtil.format("&ePremio Vincitore - Evento a Premi"),
                    MessageUtil.format("&7Forgiato nelle viscere della terra"),
                    MessageUtil.format("&7per il maestro scavatore di Caciotta!"),
                    MessageUtil.format("&7&m----------------------------")
            );
            meta.lore(lore);
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                meta.addEnchant(e.getKey(), e.getValue(), true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void giveDungeonScroll(Player player) {
        ItemStack scroll = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = scroll.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.format("&d&lPergamena del Dungeon Inesplorato"));
            List<Component> lore = List.of(
                    MessageUtil.format("&7&m----------------------------"),
                    MessageUtil.format("&ePremio Vincitore - Evento Scavatore"),
                    MessageUtil.format("&7Clicca con il &etasto destro &7oppure digita"),
                    MessageUtil.format("&e/evento dungeon &7per teletrasportarti in"),
                    MessageUtil.format("&7una struttura/dungeon inesplorata!"),
                    MessageUtil.format("&7&m----------------------------")
            );
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            scroll.setItemMeta(meta);
        }
        deliverItem(player, scroll);
    }

    private void deliverItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            MessageUtil.sendMessage(player, "&eIl tuo inventario era pieno! Alcuni oggetti premio sono stati rilasciati a terra.");
        }
    }

    /**
     * Esegue il teletrasporto sicuro del vincitore del 3° posto a una struttura inesplorata.
     */
    public boolean executeDungeonTeleport(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (!pendingDungeonTeleports.contains(uuid)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("event.dungeon-no-item"));
            return false;
        }

        World world = player.getWorld();
        Location playerLoc = player.getLocation();

        // Cerca Trial Chambers, Ancient City, Fortress o Mineshaft in base al mondo
        Structure targetStructure = Structure.TRIAL_CHAMBERS;
        if (world.getEnvironment() == World.Environment.NETHER) {
            targetStructure = Structure.FORTRESS;
        }

        StructureSearchResult result = null;
        try {
            result = world.locateNearestStructure(playerLoc, targetStructure, 10000, true);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Errore nella ricerca della struttura " + targetStructure, t);
        }

        if (result == null && world.getEnvironment() == World.Environment.NORMAL) {
            // Prova con Ancient City
            try {
                result = world.locateNearestStructure(playerLoc, Structure.ANCIENT_CITY, 10000, true);
            } catch (Throwable ignored) {
            }
        }

        if (result == null) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("event.dungeon-failed"));
            return false;
        }

        Location structLoc = result.getLocation();
        // Trova una posizione sicura con blocco d'aria e terreno solido
        Location safeLoc = findSafeLanding(world, structLoc.getBlockX(), structLoc.getBlockY(), structLoc.getBlockZ());
        if (safeLoc == null) {
            safeLoc = structLoc;
        }

        // Effetti di protezione atterraggio (Resistenza, Fuoco, Caduta lenta per 15s)
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 15, 4, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 15, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 15, 0, false, false));

        player.teleport(safeLoc);
        player.playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        player.spawnParticle(Particle.PORTAL, safeLoc, 50, 0.5, 1.0, 0.5, 0.2);

        pendingDungeonTeleports.remove(uuid);
        saveData();

        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("event.dungeon-teleported"));
        return true;
    }

    private Location findSafeLanding(World world, int x, int startY, int z) {
        int minY = Math.max(world.getMinHeight() + 2, startY - 20);
        int maxY = Math.min(world.getMaxHeight() - 2, startY + 40);

        for (int y = startY; y < maxY; y++) {
            if (isSafeBlock(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        for (int y = startY - 1; y >= minY; y--) {
            if (isSafeBlock(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
    }

    private boolean isSafeBlock(World world, int x, int y, int z) {
        Material feet = world.getBlockAt(x, y, z).getType();
        Material head = world.getBlockAt(x, y + 1, z).getType();
        Material ground = world.getBlockAt(x, y - 1, z).getType();

        return (feet.isAir() || feet == Material.LIGHT) &&
                (head.isAir() || head == Material.LIGHT) &&
                ground.isSolid() && ground != Material.LAVA && ground != Material.MAGMA_BLOCK;
    }

    public boolean hasPendingDungeon(UUID uuid) {
        return pendingDungeonTeleports.contains(uuid);
    }

    public List<Map.Entry<UUID, Integer>> getLeaderboard(int limit) {
        if (currentEvent == null) {
            return List.of();
        }
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(currentEvent.getScores().entrySet());
        list.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));
        if (list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    // --- Metodi Helper per la Scoreboard e Comandi ---

    public boolean isEventRunning() {
        return enabled && currentEvent != null && currentEvent.isRunning();
    }

    public GameEvent getCurrentEvent() {
        return currentEvent;
    }

    public String getEventName() {
        return currentEvent != null ? currentEvent.getDisplayName() : "Nessuno";
    }

    public String getEventStatus() {
        return currentEvent != null ? currentEvent.getState().getFormatted() : "&cInattivo";
    }

    public String getEventStartFormatted() {
        if (currentEvent == null) {
            return "N/D";
        }
        if (currentEvent.getStartDate() != null) {
            return currentEvent.getStartDate().format(DATE_TIME_FORMATTER);
        }
        return currentEvent.getStartDateRaw() != null ? currentEvent.getStartDateRaw() : "N/D";
    }

    public String getEventTargetFormatted() {
        if (currentEvent == null) return "0";
        return String.format(Locale.ITALY, "%,d", currentEvent.getTarget());
    }

    public int getParticipantsCount() {
        return currentEvent != null ? currentEvent.getParticipants().size() : 0;
    }

    public int getPlayerScore(Player player) {
        if (player == null || currentEvent == null) return 0;
        return currentEvent.getScore(player.getUniqueId());
    }

    public int getPlayerRemaining(Player player) {
        if (player == null || currentEvent == null) return 0;
        int score = currentEvent.getScore(player.getUniqueId());
        return Math.max(0, currentEvent.getTarget() - score);
    }

    public String getPlayerPercent(Player player) {
        if (player == null || currentEvent == null) return "0%";
        int score = currentEvent.getScore(player.getUniqueId());
        double pct = Math.min(100.0, ((double) score / currentEvent.getTarget()) * 100.0);
        return String.format(Locale.US, "%.1f%%", pct);
    }

    public String getPlayerConfirmedStatus(Player player) {
        if (player == null || currentEvent == null) return "&cNon Iscritto";
        return currentEvent.isParticipant(player.getUniqueId()) ? "&aConfermato" : "&cNon Iscritto";
    }

    public String getTopEntryFormatted(int rankIndex) {
        if (currentEvent == null) return "&7-";
        List<Map.Entry<UUID, Integer>> top = getLeaderboard(3);
        if (rankIndex <= 0 || rankIndex > top.size()) {
            return "&7In attesa...";
        }
        Map.Entry<UUID, Integer> entry = top.get(rankIndex - 1);
        String name = currentEvent.getParticipantName(entry.getKey());
        String scoreStr = String.format(Locale.ITALY, "%,d", entry.getValue());
        String check = currentEvent.isWinner(entry.getKey()) ? " &a✔" : "";
        return "&f" + name + " &8- &a" + scoreStr + check;
    }

    public String getTopPlayerName(int rankIndex) {
        if (currentEvent == null) return "-";
        List<Map.Entry<UUID, Integer>> top = getLeaderboard(3);
        if (rankIndex <= 0 || rankIndex > top.size()) {
            return "-";
        }
        return currentEvent.getParticipantName(top.get(rankIndex - 1).getKey());
    }

    public String getTopPlayerScore(int rankIndex) {
        if (currentEvent == null) return "0";
        List<Map.Entry<UUID, Integer>> top = getLeaderboard(3);
        if (rankIndex <= 0 || rankIndex > top.size()) {
            return "0";
        }
        return String.format(Locale.ITALY, "%,d", top.get(rankIndex - 1).getValue());
    }

    public int getTotalBlocksBroken() {
        if (currentEvent == null) return 0;
        int total = 0;
        for (int score : currentEvent.getScores().values()) {
            total += score;
        }
        return total;
    }

    public String getTotalBlocksBrokenFormatted() {
        return String.format(Locale.ITALY, "%,d", getTotalBlocksBroken());
    }

    public String getPlayerRankFormatted(Player player) {
        if (player == null || currentEvent == null || !currentEvent.isParticipant(player.getUniqueId())) {
            return "&7N/D";
        }
        int playerScore = currentEvent.getScore(player.getUniqueId());
        if (playerScore <= 0) {
            return "&7-";
        }
        List<Map.Entry<UUID, Integer>> leaderboard = getLeaderboard(Integer.MAX_VALUE);
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).getKey().equals(player.getUniqueId())) {
                return (i + 1) + "°";
            }
        }
        return "&7-";
    }

    public String getEventTimerFormatted() {
        if (currentEvent == null) return "&7N/D";
        if (currentEvent.isEnded()) return "&cTerminato";
        if (currentEvent.isRunning()) return "&aIn corso";
        if (currentEvent.isScheduled() && currentEvent.getStartDate() != null) {
            ZonedDateTime now = ZonedDateTime.now(configManager.getZoneId());
            Duration duration = Duration.between(now, currentEvent.getStartDate());
            if (duration.isNegative() || duration.isZero()) {
                return "&eIn avvio...";
            }
            long totalSeconds = duration.getSeconds();
            long days = totalSeconds / 86400;
            long hours = (totalSeconds % 86400) / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            if (days > 0) {
                return String.format("%dg %02dh %02dm", days, hours, minutes);
            } else if (hours > 0) {
                return String.format("%02d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format("%02d:%02d", minutes, seconds);
            }
        }
        return "&7N/D";
    }

    // --- Comandi Amministrativi ---

    public boolean forceStart(CommandSender sender, String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            GameEvent e = availableEvents.get(eventId.toLowerCase(Locale.ROOT));
            if (e != null) {
                currentEvent = e;
            } else {
                sender.sendMessage(MessageUtil.format("&cEvento '" + eventId + "' non trovato in events.yml!"));
                return false;
            }
        }
        if (currentEvent == null) {
            sender.sendMessage(MessageUtil.format("&cNessun evento disponibile da avviare!"));
            return false;
        }

        startEvent();
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.admin-started",
                Map.of("event", currentEvent.getDisplayName())));
        return true;
    }

    public boolean stopEvent(CommandSender sender) {
        if (currentEvent == null || !currentEvent.isRunning()) {
            MessageUtil.sendMessage(sender, "&cL'evento non e attualmente in corso.");
            return false;
        }

        currentEvent.setState(EventState.ENDED);
        saveData();

        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.admin-stopped",
                Map.of("event", currentEvent.getDisplayName())));
        Bukkit.broadcast(MessageUtil.format("&c&lEVENTO &8» &cL'evento &e" + currentEvent.getDisplayName() + " &ce stato interrotto dallo staff."));

        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().updateAll();
        }
        return true;
    }

    public void setPlayerProgress(CommandSender sender, Player target, int progress) {
        if (currentEvent == null || target == null) return;
        currentEvent.setScore(target.getUniqueId(), progress);
        saveData();
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.admin-progress-set",
                Map.of("target", target.getName(), "score", String.valueOf(progress))));
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().update(target);
        }
    }

    public void resetCurrentEvent(CommandSender sender) {
        if (currentEvent == null) return;
        
        // 1. Azzera in memoria tutti i progressi, punteggi, classifica, vincitori e partecipanti
        currentEvent.resetProgress();
        currentEvent.setState(EventState.SCHEDULED);

        // 2. Cancella integralmente le sezioni YAML da disco per eliminare qualsiasi residuo
        if (dataConfig != null) {
            String prefix = currentEvent.getId() + ".";
            dataConfig.set(prefix + "scores", null);
            dataConfig.set(prefix + "participants", null);
            dataConfig.set(prefix + "participant_names", null);
            dataConfig.set(prefix + "winners", null);
            dataConfig.set(prefix + "assigned_prizes", null);
            dataConfig.set(prefix + "state", EventState.SCHEDULED.name());
        }
        saveData();

        // 3. Notifica lo staff e annuncia globalmente il reset della competizione
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("event.admin-reset",
                Map.of("event", currentEvent.getDisplayName())));
        Bukkit.broadcast(MessageUtil.format("&6🏆 &eL'evento &6" + currentEvent.getDisplayName() + " &ee stato resettato dallo staff. Classifica, progressi e partecipanti azzerati!"));

        // 4. Aggiorna immediatamente tutte le scoreboard
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().updateAll();
        }
    }

    /**
     * Ricarica il modulo senza riavviare il server (Pillar 3 di guida.md).
     */
    public synchronized void reload() {
        stopTask();
        saveData();
        loadConfig();
        loadData();
        startTask();
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().updateAll();
        }
        plugin.getLogger().info("EventManager ricaricato con successo.");
    }

    /**
     * Pulizia allo spegnimento del plugin.
     */
    public synchronized void cleanup() {
        stopTask();
        saveData();
    }
}
