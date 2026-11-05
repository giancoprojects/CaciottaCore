package it.caciotta.core.rtp;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Gestore del modulo /rtp (Random Teleport):
 * Teletrasporto casuale sicuro nel mondo, generazione asincrona preventiva dei chunk,
 * limite rigido di 1 utilizzo mensile per tutti gli utenti a prescindere dal rank
 * e sincronizzazione con il fuso orario italiano (Europe/Rome).
 */
public class RtpManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final File dataFile;

    // Utilizzi mensili dei giocatori (UUID -> RtpUsage)
    private final Map<UUID, RtpUsage> usages = new ConcurrentHashMap<>();

    // Teletrasporti con conto alla rovescia in corso
    private final Map<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Location> initialLocations = new ConcurrentHashMap<>();

    // Giocatori con ricerca/generazione chunk in corso
    private final Set<UUID> searchingPlayers = ConcurrentHashMap.newKeySet();

    public static class RtpUsage {
        private String month;
        private long lastUsed;
        private int uses;

        public RtpUsage(String month, long lastUsed, int uses) {
            this.month = month;
            this.lastUsed = lastUsed;
            this.uses = uses;
        }

        public String getMonth() {
            return month;
        }

        public void setMonth(String month) {
            this.month = month;
        }

        public long getLastUsed() {
            return lastUsed;
        }

        public void setLastUsed(long lastUsed) {
            this.lastUsed = lastUsed;
        }

        public int getUses() {
            return uses;
        }

        public void setUses(int uses) {
            this.uses = uses;
        }
    }

    public RtpManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.dataFile = new File(plugin.getDataFolder(), "data/rtp.yml");
        loadData();
    }

    /**
     * Restituisce la chiave del mese corrente nel fuso orario configurato (es: "2026-11").
     */
    public String getCurrentMonthKey() {
        return LocalDate.now(configManager.getZoneId()).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    /**
     * Restituisce il nome formattato del mese corrente in lingua italiana (es: "Novembre 2026").
     */
    public String getCurrentMonthFormatted() {
        LocalDate now = LocalDate.now(configManager.getZoneId());
        String monthName = now.getMonth().getDisplayName(TextStyle.FULL, Locale.ITALIAN);
        if (monthName != null && !monthName.isEmpty()) {
            monthName = Character.toUpperCase(monthName.charAt(0)) + monthName.substring(1);
        }
        return monthName + " " + now.getYear();
    }

    /**
     * Restituisce la data di inizio del mese successivo (es: "01/12/2026").
     */
    public String getNextResetDateFormatted() {
        LocalDate today = LocalDate.now(configManager.getZoneId());
        LocalDate nextReset = today.withDayOfMonth(1).plusMonths(1);
        return nextReset.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /**
     * Restituisce il numero di utilizzi effettuati dal giocatore nel mese corrente.
     */
    public int getMonthlyUses(UUID uuid) {
        String currentMonth = getCurrentMonthKey();
        RtpUsage usage = usages.get(uuid);
        if (usage == null || !currentMonth.equals(usage.getMonth())) {
            return 0;
        }
        return usage.getUses();
    }

    /**
     * Verifica se il giocatore può utilizzare il comando /rtp.
     * Tutti gli utenti, a prescindere dal rank, possono usarlo al massimo 1 volta al mese.
     * Solo chi possiede esplicitamente il permesso bypass (o OP) può ignorare il limite per test.
     */
    public boolean canUseRtp(Player player) {
        if (player.hasPermission("caciottacore.rtp.bypasslimit")) {
            return true;
        }
        return getMonthlyUses(player.getUniqueId()) < 1;
    }

    /**
     * Registra l'utilizzo mensile del comando /rtp per il giocatore e salva su disco.
     */
    public synchronized void recordUsage(UUID uuid) {
        String currentMonth = getCurrentMonthKey();
        long now = System.currentTimeMillis();

        usages.compute(uuid, (id, current) -> {
            if (current == null || !currentMonth.equals(current.getMonth())) {
                return new RtpUsage(currentMonth, now, 1);
            }
            current.setUses(current.getUses() + 1);
            current.setLastUsed(now);
            return current;
        });

        saveData();
    }

    /**
     * Resetta l'utilizzo mensile per un singolo giocatore.
     */
    public synchronized void resetUsage(UUID uuid) {
        usages.remove(uuid);
        saveData();
    }

    /**
     * Resetta l'utilizzo mensile per tutti i giocatori.
     */
    public synchronized void resetAllUsages() {
        usages.clear();
        saveData();
    }

    /**
     * Avvia la procedura di teletrasporto casuale /rtp.
     */
    public void startRtp(Player player) {
        if (!configManager.isRtpEnabled()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.disabled"));
            return;
        }

        World world = player.getWorld();
        List<String> allowedWorlds = configManager.getRtpAllowedWorlds();
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(world.getName())) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.world-disabled",
                    Map.of("world", world.getName())));
            return;
        }

        UUID uuid = player.getUniqueId();

        if (hasPendingTeleport(uuid) || searchingPlayers.contains(uuid)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.already-in-progress"));
            return;
        }

        if (!canUseRtp(player)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.monthly-limit-reached",
                    Map.of(
                            "month", getCurrentMonthFormatted(),
                            "reset_date", getNextResetDateFormatted()
                    )));
            return;
        }

        int delay = configManager.getRtpTeleportDelay();
        if (delay <= 0 || player.hasPermission("caciottacore.rtp.bypasscooldown")) {
            executeRtpSearchAndTeleport(player);
            return;
        }

        // Annulla eventuale countdown precedente
        cancelPendingTeleport(uuid, false, null);

        initialLocations.put(uuid, player.getLocation().clone());
        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.teleport-countdown",
                Map.of("seconds", String.valueOf(delay))));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTeleports.remove(uuid);
            initialLocations.remove(uuid);
            if (player.isOnline() && !player.isDead()) {
                executeRtpSearchAndTeleport(player);
            }
        }, delay * 20L);

        pendingTeleports.put(uuid, task);
    }

    /**
     * Esegue la ricerca della posizione sicura con pre-generazione asincrona del chunk.
     */
    private void executeRtpSearchAndTeleport(Player player) {
        UUID uuid = player.getUniqueId();
        searchingPlayers.add(uuid);

        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.searching"));
        findSafeLocationAndTeleport(player, player.getWorld(), 1);
    }

    /**
     * Genera asincronamente il chunk prima del teletrasporto e verifica la sicurezza del blocco.
     * Riprova fino a max-attempts se il blocco individuato è pericoloso o non sicuro.
     */
    private void findSafeLocationAndTeleport(Player player, World world, int attempt) {
        if (!player.isOnline() || player.isDead()) {
            searchingPlayers.remove(player.getUniqueId());
            return;
        }

        int maxAttempts = configManager.getRtpMaxAttempts();
        if (attempt > maxAttempts) {
            searchingPlayers.remove(player.getUniqueId());
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.no-safe-location"));
            return;
        }

        int minRadius = configManager.getRtpMinRadius();
        int maxRadius = configManager.getRtpMaxRadius();
        int centerX = configManager.getRtpCenterX();
        int centerZ = configManager.getRtpCenterZ();

        WorldBorder border = world.getWorldBorder();
        double borderSize = border.getSize() / 2.0;
        double effectiveMax = Math.min(maxRadius, borderSize - 64.0);
        if (effectiveMax <= minRadius) {
            effectiveMax = minRadius + 500;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * 2.0 * Math.PI;
        double distance = minRadius + (random.nextDouble() * (effectiveMax - minRadius));

        int targetX = (int) Math.round(centerX + (distance * Math.cos(angle)));
        int targetZ = (int) Math.round(centerZ + (distance * Math.sin(angle)));

        int chunkX = targetX >> 4;
        int chunkZ = targetZ >> 4;

        // Generazione e caricamento asincrono del chunk tramite Paper API (gen = true)
        CompletableFuture<Chunk> chunkFuture = world.getChunkAtAsync(chunkX, chunkZ, true);
        chunkFuture.thenAccept(chunk -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || player.isDead()) {
                    searchingPlayers.remove(player.getUniqueId());
                    return;
                }

                Location safeLoc = findSafeLocationInChunk(chunk, targetX, targetZ);
                if (safeLoc != null) {
                    executeTeleport(player, safeLoc);
                } else {
                    findSafeLocationAndTeleport(player, world, attempt + 1);
                }
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().log(Level.WARNING, "Errore nella generazione del chunk RTP (" + chunkX + ", " + chunkZ + ")", ex);
                findSafeLocationAndTeleport(player, world, attempt + 1);
            });
            return null;
        });
    }

    /**
     * Esamina il chunk generato alla ricerca di un blocco di atterraggio solido e sicuro.
     */
    private Location findSafeLocationInChunk(Chunk chunk, int preferredX, int preferredZ) {
        World world = chunk.getWorld();
        int chunkMinX = chunk.getX() << 4;
        int chunkMinZ = chunk.getZ() << 4;

        if (isInsideChunk(chunk, preferredX, preferredZ)) {
            Location loc = checkSafeSpot(world, preferredX, preferredZ);
            if (loc != null) {
                return loc;
            }
        }

        // Punti di campionamento interni al chunk già generato
        int[][] offsets = {
                {8, 8}, {4, 4}, {12, 12}, {4, 12}, {12, 4},
                {2, 8}, {14, 8}, {8, 2}, {8, 14}
        };

        for (int[] offset : offsets) {
            int x = chunkMinX + offset[0];
            int z = chunkMinZ + offset[1];
            Location loc = checkSafeSpot(world, x, z);
            if (loc != null) {
                return loc;
            }
        }

        return null;
    }

    private boolean isInsideChunk(Chunk chunk, int x, int z) {
        return (x >> 4) == chunk.getX() && (z >> 4) == chunk.getZ();
    }

    /**
     * Verifica se le coordinate specificate rappresentano una superficie solida,
     * non pericolosa e con spazio sufficiente per il corpo del giocatore.
     */
    private Location checkSafeSpot(World world, int x, int z) {
        Location checkLoc = new Location(world, x, 64, z);
        if (!world.getWorldBorder().isInside(checkLoc)) {
            return null;
        }

        Block highest = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (highest == null) {
            return null;
        }

        int y = highest.getY();
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) {
            return null;
        }

        Material groundType = highest.getType();
        if (!groundType.isSolid()) {
            return null;
        }

        // Blocchi di terreno pericolosi
        if (groundType == Material.LAVA || groundType == Material.WATER ||
                groundType == Material.MAGMA_BLOCK || groundType == Material.CACTUS ||
                groundType == Material.SWEET_BERRY_BUSH || groundType == Material.FIRE ||
                groundType == Material.SOUL_FIRE || groundType == Material.CAMPFIRE ||
                groundType == Material.SOUL_CAMPFIRE || groundType == Material.WITHER_ROSE ||
                groundType == Material.POWDER_SNOW || groundType.name().endsWith("_LEAVES")) {
            return null;
        }

        Block feet = highest.getRelative(BlockFace.UP);
        Block head = feet.getRelative(BlockFace.UP);

        if (!feet.isPassable() || feet.isLiquid() || isHarmful(feet.getType())) {
            return null;
        }

        if (!head.isPassable() || head.isLiquid() || isHarmful(head.getType())) {
            return null;
        }

        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
    }

    private boolean isHarmful(Material material) {
        return material == Material.FIRE || material == Material.SOUL_FIRE ||
                material == Material.LAVA || material == Material.WATER ||
                material == Material.SWEET_BERRY_BUSH || material == Material.WITHER_ROSE ||
                material == Material.POWDER_SNOW || material == Material.CACTUS;
    }

    /**
     * Esegue il teletrasporto asincrono verso la destinazione precedentemente generata.
     */
    private void executeTeleport(Player player, Location target) {
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());

        player.teleportAsync(target).thenAccept(success -> {
            searchingPlayers.remove(player.getUniqueId());

            if (success) {
                // Registra l'utilizzo mensile consumato
                recordUsage(player.getUniqueId());

                String nextReset = getNextResetDateFormatted();
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.teleport-success",
                        Map.of(
                                "x", String.valueOf(target.getBlockX()),
                                "y", String.valueOf(target.getBlockY()),
                                "z", String.valueOf(target.getBlockZ()),
                                "world", target.getWorld().getName(),
                                "reset_date", nextReset
                        )));

                if (configManager.isRtpPlaySound()) {
                    try {
                        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    } catch (Exception ignored) {
                    }
                }

                if (configManager.isRtpSpawnParticles()) {
                    try {
                        target.getWorld().spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
                    } catch (Exception ignored) {
                    }
                }
            } else {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.no-safe-location"));
            }
        }).exceptionally(ex -> {
            searchingPlayers.remove(player.getUniqueId());
            plugin.getLogger().log(Level.WARNING, "Errore durante il teletrasporto RTP", ex);
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("rtp.no-safe-location"));
            return null;
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
        searchingPlayers.remove(uuid);

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
        usages.clear();
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section == null) {
            return;
        }

        String currentMonth = getCurrentMonthKey();
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String month = section.getString(key + ".month", currentMonth);
                long lastUsed = section.getLong(key + ".last-used", 0L);
                int uses = section.getInt(key + ".uses", 0);

                usages.put(uuid, new RtpUsage(month, lastUsed, uses));
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
            for (Map.Entry<UUID, RtpUsage> entry : usages.entrySet()) {
                String key = entry.getKey().toString();
                section.set(key + ".month", entry.getValue().getMonth());
                section.set(key + ".last-used", entry.getValue().getLastUsed());
                section.set(key + ".uses", entry.getValue().getUses());
            }

            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare i dati di rtp in " + dataFile.getName(), e);
        }
    }

    public synchronized void reload() {
        for (BukkitTask task : pendingTeleports.values()) {
            task.cancel();
        }
        pendingTeleports.clear();
        initialLocations.clear();
        searchingPlayers.clear();

        loadData();
        plugin.getLogger().info("RtpManager ricaricato con successo.");
    }

    public synchronized void cleanup() {
        for (BukkitTask task : pendingTeleports.values()) {
            task.cancel();
        }
        pendingTeleports.clear();
        initialLocations.clear();
        searchingPlayers.clear();

        saveData();
    }
}

