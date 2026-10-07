package it.caciotta.core.tpback;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestore del modulo /tpback: tracciamento posizioni di morte,
 * conteggio usi giornalieri, persistenza e teletrasporti.
 */
public class TpBackManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final File dataFile;

    // Ultima morte per giocatore (UUID -> DeathInfo)
    private final Map<UUID, DeathInfo> lastDeaths = new ConcurrentHashMap<>();

    // Conteggio giornaliero (UUID -> DailyUsage)
    private final Map<UUID, DailyUsage> dailyUsages = new ConcurrentHashMap<>();

    // Teletrasporti con conto alla rovescia in sospeso
    private final Map<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Location> initialLocations = new ConcurrentHashMap<>();

    public static class DailyUsage {
        private String date;
        private int uses;

        public DailyUsage(String date, int uses) {
            this.date = date;
            this.uses = uses;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public int getUses() {
            return uses;
        }

        public void setUses(int uses) {
            this.uses = uses;
        }
    }

    public TpBackManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.dataFile = new File(plugin.getDataFolder(), "data/tpback.yml");
        loadData();
    }

    /**
     * Registra la morte di un giocatore.
     */
    public void recordDeath(Player player, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        lastDeaths.put(uuid, new DeathInfo(uuid, location.clone(), System.currentTimeMillis()));
    }

    public DeathInfo getDeathInfo(UUID uuid) {
        return lastDeaths.get(uuid);
    }

    /**
     * Ritorna gli utilizzi odierni del giocatore.
     */
    public int getDailyUses(UUID uuid) {
        String today = LocalDate.now().toString();
        DailyUsage usage = dailyUsages.get(uuid);
        if (usage == null || !today.equals(usage.getDate())) {
            return 0;
        }
        return usage.getUses();
    }

    /**
     * Calcola il limite massimo di utilizzi giornalieri per il giocatore.
     */
    public int getMaxDailyUses(Player player) {
        if (player.hasPermission("caciottacore.tpback.unlimited") || player.isOp()) {
            return Integer.MAX_VALUE;
        }
        int max = configManager.getTpbackMaxDailyUses();
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            String perm = pai.getPermission().toLowerCase(Locale.ROOT);
            if (perm.startsWith("caciottacore.tpback.uses.")) {
                try {
                    int val = Integer.parseInt(perm.substring("caciottacore.tpback.uses.".length()));
                    if (val > max) {
                        max = val;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    public boolean hasReachedDailyLimit(Player player) {
        if (player.hasPermission("caciottacore.tpback.unlimited") || player.isOp()) {
            return false;
        }
        return getDailyUses(player.getUniqueId()) >= getMaxDailyUses(player);
    }

    public int getRemainingDailyUses(Player player) {
        if (player.hasPermission("caciottacore.tpback.unlimited") || player.isOp()) {
            return Integer.MAX_VALUE;
        }
        int remaining = getMaxDailyUses(player) - getDailyUses(player.getUniqueId());
        return Math.max(0, remaining);
    }

    /**
     * Incrementa il contatore giornaliero del giocatore e salva su disco.
     */
    public synchronized void incrementDailyUses(UUID uuid) {
        String today = LocalDate.now().toString();
        dailyUsages.compute(uuid, (id, current) -> {
            if (current == null || !today.equals(current.getDate())) {
                return new DailyUsage(today, 1);
            }
            current.setUses(current.getUses() + 1);
            return current;
        });
        saveData();
    }

    /**
     * Avvia il teletrasporto verso il punto di morte del giocatore.
     */
    public void teleportBack(Player player) {
        UUID uuid = player.getUniqueId();
        DeathInfo death = lastDeaths.get(uuid);

        if (death == null || death.isUsed()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("tpback.no-death-location"));
            return;
        }

        if (player.isDead()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("tpback.still-dead"));
            return;
        }

        int timeoutSeconds = configManager.getTpbackTimeoutSeconds();
        long elapsedMillis = System.currentTimeMillis() - death.getDeathTimestamp();
        if (elapsedMillis > (timeoutSeconds * 1000L)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("tpback.expired",
                    Map.of("seconds", String.valueOf(timeoutSeconds))));
            return;
        }

        if (hasReachedDailyLimit(player)) {
            int max = getMaxDailyUses(player);
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("tpback.daily-limit-reached",
                    Map.of("max", String.valueOf(max), "uses", String.valueOf(getDailyUses(uuid)))));
            return;
        }

        Location target = death.getDeathLocation();
        if (target == null || target.getWorld() == null) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("tpback.world-not-loaded"));
            return;
        }

        int delay = configManager.getTpbackTeleportDelay();
        if (delay <= 0 || player.hasPermission("caciottacore.tpback.bypasscooldown")) {
            executeTeleport(player, death, target);
            return;
        }

        // Annulla eventuale teletrasporto pendente
        cancelPendingTeleport(uuid, false, null);

        initialLocations.put(uuid, player.getLocation().clone());
        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("tpback.teleport-countdown",
                Map.of("seconds", String.valueOf(delay))));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTeleports.remove(uuid);
            initialLocations.remove(uuid);
            if (player.isOnline() && !player.isDead()) {
                executeTeleport(player, death, target);
            }
        }, delay * 20L);

        pendingTeleports.put(uuid, task);
    }

    private void executeTeleport(Player player, DeathInfo death, Location target) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                death.setUsed(true);
                incrementDailyUses(player.getUniqueId());

                boolean isUnlimited = player.hasPermission("caciottacore.tpback.unlimited") || player.isOp();
                if (isUnlimited) {
                    MessageUtil.sendMessage(player, configManager.getPrefixedMessage("tpback.teleport-success-unlimited"));
                } else {
                    int remaining = getRemainingDailyUses(player);
                    int max = getMaxDailyUses(player);
                    MessageUtil.sendMessage(player, configManager.getPrefixedMessage("tpback.teleport-success",
                            Map.of("remaining", String.valueOf(remaining), "max", String.valueOf(max))));
                }

                if (configManager.isTpbackPlaySound()) {
                    try {
                        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    } catch (Exception ignored) {
                    }
                }

                if (configManager.isTpbackSpawnParticles()) {
                    try {
                        target.getWorld().spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    public boolean hasPendingTeleport(UUID uuid) {
        return pendingTeleports.containsKey(uuid);
    }

    public Location getInitialLocation(UUID uuid) {
        return initialLocations.get(uuid);
    }

    public void cancelPendingTeleport(UUID uuid, boolean notify, String messageKey) {
        BukkitTask task = pendingTeleports.remove(uuid);
        initialLocations.remove(uuid);
        if (task != null) {
            task.cancel();
            if (notify && messageKey != null) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    MessageUtil.sendMessage(player, configManager.getPrefixedMessage(messageKey));
                }
            }
        }
    }

    public void onPlayerQuit(Player player) {
        cancelPendingTeleport(player.getUniqueId(), false, null);
    }

    public synchronized void loadData() {
        dailyUsages.clear();
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section == null) {
            return;
        }

        String today = LocalDate.now().toString();
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String date = section.getString(key + ".date", today);
                int uses = section.getInt(key + ".uses", 0);
                if (today.equals(date)) {
                    dailyUsages.put(uuid, new DailyUsage(date, uses));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void saveData() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }

            YamlConfiguration config = new YamlConfiguration();
            ConfigurationSection section = config.createSection("players");
            for (Map.Entry<UUID, DailyUsage> entry : dailyUsages.entrySet()) {
                String key = entry.getKey().toString();
                section.set(key + ".date", entry.getValue().getDate());
                section.set(key + ".uses", entry.getValue().getUses());
            }

            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare i dati di tpback in " + dataFile.getName(), e);
        }
    }

    public synchronized void reload() {
        for (BukkitTask task : pendingTeleports.values()) {
            task.cancel();
        }
        pendingTeleports.clear();
        initialLocations.clear();

        loadData();
        plugin.getLogger().info("TpBackManager ricaricato con successo.");
    }

    public synchronized void cleanup() {
        for (BukkitTask task : pendingTeleports.values()) {
            task.cancel();
        }
        pendingTeleports.clear();
        initialLocations.clear();
        saveData();
    }
}
