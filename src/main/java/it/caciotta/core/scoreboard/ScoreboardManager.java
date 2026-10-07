package it.caciotta.core.scoreboard;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestore principale delle scoreboard dei giocatori per CaciottaCore.
 */
public class ScoreboardManager {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final File disabledPlayersFile;
    private YamlConfiguration disabledPlayersConfig;

    private final Map<UUID, PlayerBoard> activeBoards = new ConcurrentHashMap<>();
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    private BukkitTask updateTask;
    private BukkitTask titleTask;
    private int titleFrameIndex = 0;

    public ScoreboardManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.disabledPlayersFile = new File(plugin.getDataFolder(), "data/scoreboard_disabled.yml");

        loadDisabledPlayers();
        startTasks();
    }

    private void loadDisabledPlayers() {
        if (!disabledPlayersFile.exists()) {
            try {
                if (disabledPlayersFile.getParentFile() != null && !disabledPlayersFile.getParentFile().exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    disabledPlayersFile.getParentFile().mkdirs();
                }
                //noinspection ResultOfMethodCallIgnored
                disabledPlayersFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Impossibile creare scoreboard_disabled.yml", e);
            }
        }
        disabledPlayersConfig = YamlConfiguration.loadConfiguration(disabledPlayersFile);
        List<String> list = disabledPlayersConfig.getStringList("disabled");
        for (String s : list) {
            try {
                disabledPlayers.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveDisabledPlayers() {
        List<String> list = new ArrayList<>();
        for (UUID uuid : disabledPlayers) {
            list.add(uuid.toString());
        }
        disabledPlayersConfig.set("disabled", list);
        try {
            disabledPlayersConfig.save(disabledPlayersFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare scoreboard_disabled.yml", e);
        }
    }

    /**
     * Avvia i task di aggiornamento per le linee e per l'eventuale animazione del titolo.
     */
    public void startTasks() {
        stopTasks();

        if (!configManager.isScoreboardEnabled()) {
            return;
        }

        long interval = Math.max(1L, configManager.getScoreboardUpdateIntervalTicks());
        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, interval, interval);

        if (configManager.isScoreboardAnimatedTitle()) {
            long titleInterval = Math.max(1L, configManager.getScoreboardAnimatedTitleIntervalTicks());
            List<String> frames = configManager.getScoreboardAnimatedTitleFrames();
            if (!frames.isEmpty()) {
                this.titleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (frames.isEmpty()) {
                        return;
                    }
                    String currentFrame = frames.get(titleFrameIndex % frames.size());
                    titleFrameIndex++;
                    for (PlayerBoard board : activeBoards.values()) {
                        board.updateTitle(currentFrame);
                    }
                }, titleInterval, titleInterval);
            }
        }
    }

    /**
     * Ferma i task di aggiornamento.
     */
    public void stopTasks() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (titleTask != null) {
            titleTask.cancel();
            titleTask = null;
        }
    }

    /**
     * Crea o recupera la scoreboard per il giocatore.
     */
    public void createScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!configManager.isScoreboardEnabled() || disabledPlayers.contains(player.getUniqueId())) {
            return;
        }

        String initialTitle = getCurrentTitle();
        boolean showNumbers = configManager.isScoreboardShowNumbers();
        PlayerBoard board = new PlayerBoard(player, initialTitle, showNumbers);
        activeBoards.put(player.getUniqueId(), board);

        updatePlayerBoard(player, board);
    }

    /**
     * Rimuove e distrugge la scoreboard di un giocatore.
     */
    public void removeScoreboard(Player player) {
        if (player == null) {
            return;
        }
        PlayerBoard board = activeBoards.remove(player.getUniqueId());
        if (board != null) {
            board.destroy();
        }
    }

    /**
     * Alterna (toggle) la visibilità della scoreboard per un giocatore.
     * @return true se ora è visibile, false se disattivata.
     */
    public boolean toggleScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            saveDisabledPlayers();
            createScoreboard(player);
            return true;
        } else {
            disabledPlayers.add(uuid);
            saveDisabledPlayers();
            removeScoreboard(player);
            return false;
        }
    }

    public boolean isScoreboardDisabled(Player player) {
        return disabledPlayers.contains(player.getUniqueId());
    }

    /**
     * Aggiorna tutti i giocatori online.
     */
    public void updateAll() {
        if (!configManager.isScoreboardEnabled()) {
            return;
        }

        String title = getCurrentTitle();
        boolean isAnimated = configManager.isScoreboardAnimatedTitle();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (disabledPlayers.contains(player.getUniqueId())) {
                continue;
            }

            PlayerBoard board = activeBoards.computeIfAbsent(player.getUniqueId(), id ->
                    new PlayerBoard(player, title, configManager.isScoreboardShowNumbers()));

            if (!isAnimated) {
                board.updateTitle(title);
            }
            updatePlayerBoard(player, board);
        }
    }

    /**
     * Aggiorna la singola scoreboard di un giocatore.
     */
    public void update(Player player) {
        if (player == null || !player.isOnline() || disabledPlayers.contains(player.getUniqueId())) {
            return;
        }
        PlayerBoard board = activeBoards.get(player.getUniqueId());
        if (board == null) {
            createScoreboard(player);
        } else {
            updatePlayerBoard(player, board);
        }
    }

    private void updatePlayerBoard(Player player, PlayerBoard board) {
        List<String> rawLines = configManager.getScoreboardLines();
        List<String> parsedLines = new ArrayList<>(rawLines.size());

        for (String raw : rawLines) {
            parsedLines.add(parsePlaceholders(player, raw));
        }

        board.updateLines(parsedLines, configManager.isScoreboardShowNumbers());
    }

    private String getCurrentTitle() {
        if (configManager.isScoreboardAnimatedTitle()) {
            List<String> frames = configManager.getScoreboardAnimatedTitleFrames();
            if (!frames.isEmpty()) {
                return frames.get(titleFrameIndex % frames.size());
            }
        }
        return configManager.getScoreboardTitle();
    }

    /**
     * Sostituisce tutti i placeholder supportati all'interno della riga.
     */
    public String parsePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        int kills = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);
        double kdr = (deaths == 0) ? (double) kills : ((double) kills / deaths);
        String kdrFormatted = String.format(Locale.US, "%.2f", kdr);

        int mobKills = player.getStatistic(Statistic.MOB_KILLS);
        int ping = player.getPing();
        int online = Bukkit.getOnlinePlayers().size();
        int maxOnline = Bukkit.getMaxPlayers();
        String worldName = player.getWorld().getName();
        int level = player.getLevel();

        double health = player.getHealth();
        double maxHealth = 20.0;
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
        }

        int homesCount = 0;
        int maxHomes = 0;
        if (plugin.getHomeManager() != null) {
            homesCount = plugin.getHomeManager().getHomes(player.getUniqueId()).size();
            maxHomes = plugin.getHomeManager().getMaxHomes(player);
        }

        int warpsCount = 0;
        if (plugin.getWarpManager() != null) {
            warpsCount = plugin.getWarpManager().getWarps().size();
        }

        String rank = "&7Utente";
        String rankPrefix = "&7[Utente] ";
        if (plugin.getRankManager() != null) {
            it.caciotta.core.rank.Rank r = plugin.getRankManager().getPlayerRank(player);
            if (r != null) {
                rank = r.getDisplayName();
                rankPrefix = r.getPrefix();
            }
        } else if (player.isOp()) {
            rank = "&cAdmin";
            rankPrefix = "&c[Admin] ";
        } else if (player.hasPermission("caciottacore.admin")) {
            rank = "&6Mod";
            rankPrefix = "&6[Mod] ";
        } else if (player.hasPermission("caciottacore.vip")) {
            rank = "&aVIP";
            rankPrefix = "&a[VIP] ";
        }

        Location loc = player.getLocation();
        int posX = loc.getBlockX();
        int posY = loc.getBlockY();
        int posZ = loc.getBlockZ();
        String coords = posX + ", " + posY + ", " + posZ;

        float yaw = (loc.getYaw() % 360 + 360) % 360;
        String direction;
        String directionShort;
        if (yaw >= 315 || yaw < 45) {
            direction = "Sud";
            directionShort = "S";
        } else if (yaw < 135) {
            direction = "Ovest";
            directionShort = "W";
        } else if (yaw < 225) {
            direction = "Nord";
            directionShort = "N";
        } else {
            direction = "Est";
            directionShort = "E";
        }

        String status = "&aOnline";
        if (plugin.getAuthManager() != null) {
            status = plugin.getAuthManager().isAuthenticated(player) ? "&aAutenticato" : "&cNon Autenticato";
        }

        String date = LocalDate.now().format(DATE_FORMATTER);
        String time = LocalTime.now().format(TIME_FORMATTER);

        return text
                .replace("{player}", player.getName())
                .replace("{name}", player.getName())
                .replace("{display_name}", player.getName())
                .replace("{online}", String.valueOf(online))
                .replace("{max_online}", String.valueOf(maxOnline))
                .replace("{ping}", String.valueOf(ping))
                .replace("{kills}", String.valueOf(kills))
                .replace("{deaths}", String.valueOf(deaths))
                .replace("{mob_kills}", String.valueOf(mobKills))
                .replace("{kdr}", kdrFormatted)
                .replace("{world}", worldName)
                .replace("{level}", String.valueOf(level))
                .replace("{health}", String.format(Locale.US, "%.1f", health))
                .replace("{max_health}", String.format(Locale.US, "%.1f", maxHealth))
                .replace("{rank}", rank)
                .replace("{rank_prefix}", rankPrefix)
                .replace("{x}", String.valueOf(posX))
                .replace("{y}", String.valueOf(posY))
                .replace("{z}", String.valueOf(posZ))
                .replace("{coords}", coords)
                .replace("{coordinates}", coords)
                .replace("{direction}", direction)
                .replace("{direction_short}", directionShort)
                .replace("{facing}", direction)
                .replace("{homes}", String.valueOf(homesCount))
                .replace("{max_homes}", String.valueOf(maxHomes))
                .replace("{warps}", String.valueOf(warpsCount))
                .replace("{status}", status)
                .replace("{date}", date)
                .replace("{time}", time);
    }

    /**
     * Ricarica la configurazione e rigenera le scoreboard.
     */
    public void reload() {
        startTasks();
        for (PlayerBoard board : activeBoards.values()) {
            board.destroy();
        }
        activeBoards.clear();

        if (configManager.isScoreboardEnabled()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                createScoreboard(player);
            }
        }
    }

    /**
     * Pulizia completa allo shutdown del plugin.
     */
    public void cleanup() {
        stopTasks();
        for (PlayerBoard board : activeBoards.values()) {
            board.destroy();
        }
        activeBoards.clear();
    }
}
