package it.caciotta.core.rank;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestore principale del sistema di Rank.
 * Gestisce il caricamento dei rank, le assegnazioni ai giocatori, i permessi dinamici,
 * la visualizzazione nella tablist e l'ordinamento tramite Scoreboard Teams.
 */
public class RankManager {

    private final CaciottaCore plugin;
    private final File ranksConfigFile;
    private final File playerDataFile;

    private YamlConfiguration ranksConfig;
    private YamlConfiguration playerDataConfig;

    private final Map<String, Rank> ranks = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerRanks = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    private Rank defaultRank;
    private boolean chatFormattingEnabled = true;
    private String chatFormat = "{prefix}{player}{suffix}&8 » {chat_color}{message}";
    private boolean tablistEnabled = true;
    private String tablistFormat = "{prefix}{player}{suffix}";

    public RankManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.ranksConfigFile = new File(plugin.getDataFolder(), "ranks.yml");
        this.playerDataFile = new File(plugin.getDataFolder(), "data/ranks_data.yml");

        loadRanks();
        loadPlayerData();
    }

    /**
     * Carica i rank dal file ranks.yml. Se il file non esiste, crea la configurazione predefinita.
     */
    public synchronized void loadRanks() {
        ranks.clear();

        if (!ranksConfigFile.exists()) {
            try {
                plugin.saveResource("ranks.yml", false);
            } catch (Exception e) {
                createDefaultRanksConfig();
            }
        }

        ranksConfig = YamlConfiguration.loadConfiguration(ranksConfigFile);

        this.chatFormattingEnabled = ranksConfig.getBoolean("chat.enabled", true);
        this.chatFormat = ranksConfig.getString("chat.format", "{prefix}{player}{suffix}&8 » {chat_color}{message}");
        this.tablistEnabled = ranksConfig.getBoolean("tablist.enabled", true);
        this.tablistFormat = ranksConfig.getString("tablist.format", "{prefix}{player}{suffix}");

        ConfigurationSection ranksSection = ranksConfig.getConfigurationSection("ranks");
        if (ranksSection != null) {
            for (String key : ranksSection.getKeys(false)) {
                ConfigurationSection sec = ranksSection.getConfigurationSection(key);
                if (sec == null) continue;

                String displayName = sec.getString("display-name", key);
                String prefix = sec.getString("prefix", "");
                String suffix = sec.getString("suffix", "");
                String chatColor = sec.getString("chat-color", "&f");
                int weight = sec.getInt("weight", 0);
                boolean isDef = sec.getBoolean("default", false);
                int maxHomes = sec.getInt("max-homes", 3);
                List<String> perms = sec.getStringList("permissions");

                Rank rank = new Rank(key, displayName, prefix, suffix, chatColor, weight, isDef, maxHomes, perms);
                ranks.put(rank.getId(), rank);

                if (isDef) {
                    this.defaultRank = rank;
                }
            }
        }

        // Se nessun rank è marcato come default, cerchiamo "utente" o il rank con peso minore
        if (this.defaultRank == null && !ranks.isEmpty()) {
            this.defaultRank = ranks.get("utente");
            if (this.defaultRank == null) {
                this.defaultRank = ranks.values().stream()
                        .min(Comparator.comparingInt(Rank::getWeight))
                        .orElse(null);
            }
        }

        // Se non è stato caricato alcun rank, crea quello base di emergenza
        if (ranks.isEmpty()) {
            Rank emergency = new Rank("utente", "&7Utente", "&7[Utente] ", "", "&7", 1, true, 3, Collections.emptyList());
            ranks.put(emergency.getId(), emergency);
            this.defaultRank = emergency;
        }

        plugin.getLogger().info("Caricati " + ranks.size() + " rank dal file ranks.yml. Default: " + (defaultRank != null ? defaultRank.getId() : "nessuno"));
    }

    private void createDefaultRanksConfig() {
        plugin.getDataFolder().mkdirs();
        ranksConfig = new YamlConfiguration();

        ranksConfig.set("chat.enabled", true);
        ranksConfig.set("chat.format", "{prefix}{player}{suffix}&8 » {chat_color}{message}");
        ranksConfig.set("tablist.enabled", true);
        ranksConfig.set("tablist.format", "{prefix}{player}{suffix}");

        // Owner
        setRankInConfig("owner", "&4&lOWNER", "&4[Owner] &r", "", "&f", 100, false, 100,
                List.of("caciottacore.*", "*"));

        // Admin
        setRankInConfig("admin", "&c&lADMIN", "&c[Admin] &r", "", "&f", 80, false, 50,
                List.of("caciottacore.*", "caciottacore.admin", "caciottacore.rank.admin"));

        // Mod
        setRankInConfig("mod", "&6&lMOD", "&6[Mod] &r", "", "&f", 60, false, 20,
                List.of("caciottacore.home.*", "caciottacore.tpa.*", "caciottacore.scoreboard.*", "caciottacore.chat.color"));

        // Helper
        setRankInConfig("helper", "&e&lHELPER", "&e[Helper] &r", "", "&f", 40, false, 10,
                List.of("caciottacore.home.*", "caciottacore.tpa.*", "caciottacore.scoreboard.*", "caciottacore.chat.color"));

        // VIP+
        setRankInConfig("vip+", "&b&lVIP+", "&b[VIP+] &r", "", "&f", 25, false, 8,
                List.of("caciottacore.home.bypasscooldown", "caciottacore.tpa.bypasscooldown", "caciottacore.chat.color"));

        // VIP
        setRankInConfig("vip", "&a&lVIP", "&a[VIP] &r", "", "&f", 20, false, 5,
                List.of("caciottacore.chat.color"));

        // Utente (Default)
        setRankInConfig("utente", "&7Utente", "&7[Utente] &r", "", "&7", 1, true, 3,
                List.of("caciottacore.home.use", "caciottacore.home.set", "caciottacore.home.delete",
                        "caciottacore.home.list", "caciottacore.tpa.use", "caciottacore.scoreboard.use"));

        try {
            ranksConfig.save(ranksConfigFile);
            plugin.getLogger().info("Creato file predefinito ranks.yml con 7 rank bilanciati.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare il file ranks.yml", e);
        }
    }

    private void setRankInConfig(String id, String displayName, String prefix, String suffix,
                                 String chatColor, int weight, boolean isDefault, int maxHomes, List<String> perms) {
        String path = "ranks." + id + ".";
        ranksConfig.set(path + "display-name", displayName);
        ranksConfig.set(path + "prefix", prefix);
        ranksConfig.set(path + "suffix", suffix);
        ranksConfig.set(path + "chat-color", chatColor);
        ranksConfig.set(path + "weight", weight);
        ranksConfig.set(path + "default", isDefault);
        ranksConfig.set(path + "max-homes", maxHomes);
        ranksConfig.set(path + "permissions", perms);
    }

    /**
     * Carica le associazioni UUID -> Rank dal file data/ranks_data.yml
     */
    public synchronized void loadPlayerData() {
        playerRanks.clear();
        if (!playerDataFile.exists()) {
            playerDataConfig = new YamlConfiguration();
            return;
        }

        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        ConfigurationSection section = playerDataConfig.getConfigurationSection("players");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String rankId = section.getString(key + ".rank");
                    if (rankId != null) {
                        playerRanks.put(uuid, rankId.toLowerCase());
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /**
     * Salva le associazioni giocatore nel file dati.
     */
    public synchronized void savePlayerData() {
        if (playerDataConfig == null) {
            playerDataConfig = new YamlConfiguration();
        }

        for (Map.Entry<UUID, String> entry : playerRanks.entrySet()) {
            String path = "players." + entry.getKey().toString();
            playerDataConfig.set(path + ".rank", entry.getValue());
        }

        try {
            if (!playerDataFile.getParentFile().exists()) {
                playerDataFile.getParentFile().mkdirs();
            }
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare data/ranks_data.yml", e);
        }
    }

    /**
     * Ottiene il rank per identificatore (case-insensitive).
     */
    public Rank getRank(String id) {
        if (id == null) return null;
        return ranks.get(id.toLowerCase());
    }

    /**
     * Restituisce tutti i rank ordinati per peso decrescente.
     */
    public List<Rank> getRanks() {
        List<Rank> list = new ArrayList<>(ranks.values());
        Collections.sort(list);
        return list;
    }

    public Rank getDefaultRank() {
        return defaultRank;
    }

    public void setDefaultRank(Rank rank) {
        if (rank == null) return;
        if (defaultRank != null) {
            defaultRank.setDefault(false);
            ranksConfig.set("ranks." + defaultRank.getId() + ".default", false);
        }
        this.defaultRank = rank;
        rank.setDefault(true);
        ranksConfig.set("ranks." + rank.getId() + ".default", true);
        try {
            ranksConfig.save(ranksConfigFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore salvataggio default rank in ranks.yml", e);
        }
    }

    /**
     * Restituisce il rank di un giocatore tramite UUID.
     */
    public Rank getPlayerRank(UUID uuid) {
        String rankId = playerRanks.get(uuid);
        if (rankId != null) {
            Rank r = ranks.get(rankId);
            if (r != null) {
                return r;
            }
        }
        return defaultRank;
    }

    /**
     * Restituisce il rank di un giocatore online.
     * Se il giocatore è OP e non ha rank esplicito, assegna il rank con peso massimo (es. owner o admin).
     */
    public Rank getPlayerRank(Player player) {
        if (player == null) return defaultRank;
        String rankId = playerRanks.get(player.getUniqueId());
        if (rankId != null) {
            Rank r = ranks.get(rankId);
            if (r != null) {
                return r;
            }
        }
        if (player.isOp()) {
            Rank owner = ranks.get("owner");
            if (owner != null) return owner;
            Rank admin = ranks.get("admin");
            if (admin != null) return admin;
        }
        return defaultRank;
    }

    /**
     * Assegna un rank a un giocatore e aggiorna permessi, tablist, scoreboard e dati salvati.
     */
    public void setPlayerRank(UUID uuid, Rank rank, String playerName) {
        if (rank == null) {
            playerRanks.remove(uuid);
            if (playerDataConfig != null) {
                playerDataConfig.set("players." + uuid.toString(), null);
            }
        } else {
            playerRanks.put(uuid, rank.getId());
            if (playerDataConfig != null) {
                String path = "players." + uuid.toString();
                playerDataConfig.set(path + ".rank", rank.getId());
                if (playerName != null) {
                    playerDataConfig.set(path + ".name", playerName);
                }
            }
        }
        savePlayerData();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            applyPermissions(player);
            updatePlayerTab(player);
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().update(player);
            }
        }
    }

    /**
     * Rimuove il rank personalizzato di un giocatore, ripristinando il rank default.
     */
    public void resetPlayerRank(UUID uuid) {
        setPlayerRank(uuid, null, null);
    }

    /**
     * Applica i permessi Bukkit del rank al giocatore online via PermissionAttachment.
     */
    public void applyPermissions(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        // Rimuovi l'attachment precedente se presente
        PermissionAttachment oldAttachment = attachments.remove(player.getUniqueId());
        if (oldAttachment != null) {
            try {
                player.removeAttachment(oldAttachment);
            } catch (Exception ignored) {
            }
        }

        Rank rank = getPlayerRank(player);
        if (rank == null) {
            return;
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String perm : rank.getPermissions()) {
            if (perm == null || perm.isBlank()) continue;
            String trimmed = perm.trim();
            if (trimmed.startsWith("-")) {
                attachment.setPermission(trimmed.substring(1), false);
            } else {
                attachment.setPermission(trimmed, true);
            }
        }

        attachments.put(player.getUniqueId(), attachment);
        player.recalculatePermissions();
    }

    /**
     * Aggiorna il nome in tablist del giocatore e l'assegnazione al team Scoreboard per l'ordinamento.
     */
    public void updatePlayerTab(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Rank rank = getPlayerRank(player);
        if (rank == null) {
            return;
        }

        if (tablistEnabled) {
            String formatted = tablistFormat
                    .replace("{prefix}", rank.getPrefix())
                    .replace("{player}", player.getName())
                    .replace("{suffix}", rank.getSuffix())
                    .replace("{rank}", rank.getDisplayName());
            player.playerListName(MessageUtil.format(formatted));
        }

        // Gestione team scoreboard per ordinare i giocatori in tab in base al peso (più alto = più in alto)
        try {
            Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            int weightOrder = 9999 - Math.min(9999, Math.max(0, rank.getWeight()));
            String teamId = String.format("%04d_%s", weightOrder, rank.getId());
            if (teamId.length() > 16) {
                teamId = teamId.substring(0, 16);
            }

            Team team = mainScoreboard.getTeam(teamId);
            if (team == null) {
                team = mainScoreboard.registerNewTeam(teamId);
                team.prefix(MessageUtil.format(rank.getPrefix()));
                team.suffix(MessageUtil.format(rank.getSuffix()));
            }

            if (!team.hasEntry(player.getName())) {
                // Rimuovi il giocatore da altri vecchi team di rank
                for (Team oldTeam : mainScoreboard.getTeams()) {
                    if (oldTeam.hasEntry(player.getName())) {
                        oldTeam.removeEntry(player.getName());
                    }
                }
                team.addEntry(player.getName());
            }
        } catch (Exception e) {
            // Ignora se la scoreboard non è pronta
        }
    }

    /**
     * Aggiorna tutti i giocatori online (permessi, tablist e scoreboard).
     */
    public void updateAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPermissions(player);
            updatePlayerTab(player);
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().update(player);
            }
        }
    }

    /**
     * Chiamato quando un giocatore entra nel server.
     */
    public void onPlayerJoin(Player player) {
        applyPermissions(player);
        updatePlayerTab(player);
    }

    /**
     * Chiamato quando un giocatore lascia il server.
     */
    public void onPlayerQuit(Player player) {
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Ricarica la configurazione dei rank, i dati dei giocatori e aggiorna tutti i player online.
     */
    public synchronized void reload() {
        loadRanks();
        loadPlayerData();
        updateAllOnlinePlayers();
    }

    /**
     * Pulisce tutte le risorse al reload/disable del plugin.
     */
    public void cleanup() {
        for (Map.Entry<UUID, PermissionAttachment> entry : attachments.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                try {
                    p.removeAttachment(entry.getValue());
                } catch (Exception ignored) {
                }
            }
        }
        attachments.clear();
    }

    public boolean isChatFormattingEnabled() {
        return chatFormattingEnabled;
    }

    public String getChatFormat() {
        return chatFormat;
    }

    public boolean isTablistEnabled() {
        return tablistEnabled;
    }
}
