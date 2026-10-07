package it.caciotta.core.warp;

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
import java.util.regex.Pattern;

/**
 * Gestore dei warp globali del server, teletrasporti e persistenza.
 */
public class WarpManager {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_-]{1,16}$");

    public enum SetResult {
        SUCCESS,
        UPDATED,
        LIMIT_REACHED,
        ALREADY_EXISTS_OTHER,
        INVALID_NAME
    }

    public enum DeleteResult {
        SUCCESS,
        NOT_FOUND,
        NOT_OWNER
    }

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final File warpsFile;

    private final Map<String, Warp> warps = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Location> initialLocations = new ConcurrentHashMap<>();

    public WarpManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.warpsFile = new File(plugin.getDataFolder(), "data/warps.yml");
        loadWarps();
    }

    public void loadWarps() {
        warps.clear();
        if (!warpsFile.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(warpsFile);
        ConfigurationSection section = yaml.getConfigurationSection("warps");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection warpSec = section.getConfigurationSection(key);
                if (warpSec != null) {
                    Map<String, Object> values = warpSec.getValues(false);
                    warps.put(key.toLowerCase(Locale.ROOT), Warp.fromMap(key, values));
                }
            }
        }
    }

    public synchronized void reload() {
        loadWarps();
    }

    public synchronized void saveWarps() {
        if (!warpsFile.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            warpsFile.getParentFile().mkdirs();
        }

        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("warps");

        for (Map.Entry<String, Warp> entry : warps.entrySet()) {
            section.createSection(entry.getKey(), entry.getValue().toMap());
        }

        try {
            yaml.save(warpsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore durante il salvataggio dei warp in " + warpsFile.getName(), e);
        }
    }

    public Map<String, Warp> getWarps() {
        return Collections.unmodifiableMap(warps);
    }

    public Warp getWarp(String name) {
        if (name == null) {
            return null;
        }
        return warps.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean hasWarp(String name) {
        return name != null && warps.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public int getPlayerWarpCount(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        int count = 0;
        for (Warp warp : warps.values()) {
            if (uuid.equals(warp.getCreatorUuid())) {
                count++;
            }
        }
        return count;
    }

    public int getMaxWarps(Player player) {
        if (player.hasPermission("caciottacore.warp.unlimited")
                || player.hasPermission("caciottacore.warp.admin")
                || player.isOp()) {
            return 9999;
        }

        int max = configManager.getMaxWarpsPerPlayer();
        if (max <= 0) {
            return 9999;
        }

        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            String perm = pai.getPermission().toLowerCase(Locale.ROOT);
            if (perm.startsWith("caciottacore.warp.max.")) {
                try {
                    String numStr = perm.substring("caciottacore.warp.max.".length());
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

    public SetResult setWarp(Player player, String name, Location loc) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            return SetResult.INVALID_NAME;
        }

        String lower = name.toLowerCase(Locale.ROOT);
        Warp existing = warps.get(lower);

        if (existing != null) {
            // Se esiste gia, controlla se il giocatore e il creatore o ha permessi admin
            boolean isOwner = player.getUniqueId().equals(existing.getCreatorUuid());
            boolean isAdmin = player.hasPermission("caciottacore.warp.admin") || player.isOp();

            if (!isOwner && !isAdmin) {
                return SetResult.ALREADY_EXISTS_OTHER;
            }

            Warp updated = Warp.fromLocation(existing.getName(), loc, player);
            warps.put(lower, updated);
            saveWarps();
            return SetResult.UPDATED;
        }

        // Se non esiste, verifica il limite massimo di warp per il giocatore
        int max = getMaxWarps(player);
        int currentCount = getPlayerWarpCount(player.getUniqueId());
        if (currentCount >= max) {
            return SetResult.LIMIT_REACHED;
        }

        Warp newWarp = Warp.fromLocation(name, loc, player);
        warps.put(lower, newWarp);
        saveWarps();
        return SetResult.SUCCESS;
    }

    public DeleteResult deleteWarp(Player player, String name) {
        if (name == null) {
            return DeleteResult.NOT_FOUND;
        }

        String lower = name.toLowerCase(Locale.ROOT);
        Warp existing = warps.get(lower);
        if (existing == null) {
            return DeleteResult.NOT_FOUND;
        }

        if (player != null) {
            boolean isOwner = player.getUniqueId().equals(existing.getCreatorUuid());
            boolean isAdmin = player.hasPermission("caciottacore.warp.admin") || player.isOp();

            if (!isOwner && !isAdmin) {
                return DeleteResult.NOT_OWNER;
            }
        }

        warps.remove(lower);
        saveWarps();
        return DeleteResult.SUCCESS;
    }

    public void teleportToWarp(Player player, String warpName) {
        Warp warp = getWarp(warpName);
        if (warp == null) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.not-found",
                    Map.of("name", warpName)));
            return;
        }

        Location target = warp.toLocation();
        if (target == null) {
            MessageUtil.sendMessage(player, "&cIl mondo '" + warp.getWorldName() + "' non e al momento caricato.");
            return;
        }

        int delay = configManager.getWarpTeleportDelay();
        if (delay <= 0 || player.hasPermission("caciottacore.warp.bypasscooldown")) {
            executeTeleport(player, warp, target);
            return;
        }

        UUID uuid = player.getUniqueId();
        cancelPendingTeleport(uuid, false, null);

        initialLocations.put(uuid, player.getLocation().clone());
        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.teleport-countdown",
                Map.of("seconds", String.valueOf(delay), "name", warp.getName())));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTeleports.remove(uuid);
            initialLocations.remove(uuid);
            if (player.isOnline()) {
                executeTeleport(player, warp, target);
            }
        }, delay * 20L);

        pendingTeleports.put(uuid, task);
    }

    private void executeTeleport(Player player, Warp warp, Location target) {
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("warp.teleporting-now",
                        Map.of("name", warp.getName())));

                if (configManager.isWarpPlaySound()) {
                    try {
                        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    } catch (Exception ignored) {
                    }
                }

                if (configManager.isWarpSpawnParticles()) {
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

    public void cleanup() {
        for (BukkitTask task : pendingTeleports.values()) {
            task.cancel();
        }
        pendingTeleports.clear();
        initialLocations.clear();
        warps.clear();
    }
}
