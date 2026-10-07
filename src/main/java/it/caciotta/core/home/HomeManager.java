package it.caciotta.core.home;

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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestore delle home dei giocatori, salvataggio dati e logica di teletrasporto.
 */
public class HomeManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final File homesFolder;

    private final Map<UUID, Map<String, Home>> cachedHomes = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Location> initialLocations = new ConcurrentHashMap<>();

    public HomeManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.homesFolder = new File(plugin.getDataFolder(), "data/homes");
        if (!homesFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            homesFolder.mkdirs();
        }
    }

    private File getPlayerFile(UUID uuid) {
        return new File(homesFolder, uuid.toString() + ".yml");
    }

    public Map<String, Home> getHomes(UUID uuid) {
        return cachedHomes.computeIfAbsent(uuid, this::loadHomesFromFile);
    }

    private Map<String, Home> loadHomesFromFile(UUID uuid) {
        Map<String, Home> map = new HashMap<>();
        File file = getPlayerFile(uuid);
        if (!file.exists()) {
            return map;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("homes");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection homeSec = section.getConfigurationSection(key);
                if (homeSec != null) {
                    Map<String, Object> values = homeSec.getValues(false);
                    map.put(key.toLowerCase(Locale.ROOT), Home.fromMap(key, values));
                }
            }
        }
        return map;
    }

    public void saveHomes(UUID uuid) {
        Map<String, Home> homes = cachedHomes.get(uuid);
        if (homes == null) {
            return;
        }

        File file = getPlayerFile(uuid);
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("homes");

        for (Map.Entry<String, Home> entry : homes.entrySet()) {
            section.createSection(entry.getKey(), entry.getValue().toMap());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore durante il salvataggio delle home per " + uuid, e);
        }
    }

    public Home getHome(UUID uuid, String name) {
        Map<String, Home> homes = getHomes(uuid);
        return homes.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean setHome(Player player, String name, Location loc) {
        UUID uuid = player.getUniqueId();
        Map<String, Home> homes = getHomes(uuid);
        String lower = name.toLowerCase(Locale.ROOT);

        // Se non esiste gia, controlla il limite massimo consentito
        if (!homes.containsKey(lower)) {
            int max = getMaxHomes(player);
            if (homes.size() >= max) {
                return false;
            }
        }

        Home home = Home.fromLocation(name, loc);
        homes.put(lower, home);
        saveHomes(uuid);
        return true;
    }

    public boolean deleteHome(UUID uuid, String name) {
        Map<String, Home> homes = getHomes(uuid);
        Home removed = homes.remove(name.toLowerCase(Locale.ROOT));
        if (removed != null) {
            saveHomes(uuid);
            return true;
        }
        return false;
    }

    public int getMaxHomes(Player player) {
        if (player.hasPermission("caciottacore.homes.unlimited") || player.isOp()) {
            return 9999;
        }
        int max = configManager.getDefaultMaxHomes();
        if (plugin.getRankManager() != null) {
            it.caciotta.core.rank.Rank rank = plugin.getRankManager().getPlayerRank(player);
            if (rank != null && rank.getMaxHomes() > max) {
                max = rank.getMaxHomes();
            }
        }
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            String perm = pai.getPermission().toLowerCase(Locale.ROOT);
            if (perm.startsWith("caciottacore.homes.")) {
                try {
                    String numStr = perm.substring("caciottacore.homes.".length());
                    int val = Integer.parseInt(numStr);
                    if (val > max) {
                        max = val;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    public void teleportToHome(Player player, String homeName) {
        UUID uuid = player.getUniqueId();
        Home home = getHome(uuid, homeName);
        if (home == null) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("home.not-found",
                    Map.of("name", homeName)));
            return;
        }

        Location target = home.toLocation();
        if (target == null) {
            MessageUtil.sendMessage(player, "&cIl mondo '" + home.getWorldName() + "' non e al momento caricato.");
            return;
        }

        int delay = configManager.getTeleportDelay();
        if (delay <= 0 || player.hasPermission("caciottacore.home.bypasscooldown")) {
            executeTeleport(player, home, target);
            return;
        }

        // Annulla eventuale teletrasporto pendente
        cancelPendingTeleport(uuid, false, null);

        initialLocations.put(uuid, player.getLocation().clone());
        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("home.teleport-countdown",
                Map.of("seconds", String.valueOf(delay), "name", home.getName())));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTeleports.remove(uuid);
            initialLocations.remove(uuid);
            if (player.isOnline()) {
                executeTeleport(player, home, target);
            }
        }, delay * 20L);

        pendingTeleports.put(uuid, task);
    }

    private void executeTeleport(Player player, Home home, Location target) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("home.teleporting-now",
                        Map.of("name", home.getName())));

                if (configManager.isPlayHomeSound()) {
                    try {
                        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    } catch (Exception ignored) {
                    }
                }

                if (configManager.isSpawnHomeParticles()) {
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
        UUID uuid = player.getUniqueId();
        cancelPendingTeleport(uuid, false, null);
        cachedHomes.remove(uuid);
    }

    public void reload() {
        cachedHomes.clear();
    }

    public void cleanup() {
        for (BukkitTask task : pendingTeleports.values()) {
            task.cancel();
        }
        pendingTeleports.clear();
        initialLocations.clear();
        cachedHomes.clear();
    }
}
