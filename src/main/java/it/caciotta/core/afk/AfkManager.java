package it.caciotta.core.afk;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestore principale per il sistema /afk:
 * passaggio in modalita Spettatore, blocco totale delle azioni, titolo permanente a schermo,
 * suffisso nella Tablist e ripristino al movimento con la tastiera.
 */
public class AfkManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;

    public record AfkData(GameMode previousGameMode, Location originalLocation, long startTime) {}

    // Mappa dei giocatori attualmente in AFK
    private final Map<UUID, AfkData> afkPlayers = new ConcurrentHashMap<>();

    // Task per mantenere fisso il titolo a schermo per i giocatori in AFK
    private BukkitTask titleTask;

    public AfkManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        startTitleTask();
    }

    /**
     * Avvia il task periodico per mantenere il titolo fisso a schermo.
     */
    public synchronized void startTitleTask() {
        if (titleTask != null && !titleTask.isCancelled()) {
            titleTask.cancel();
        }

        titleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (afkPlayers.isEmpty()) return;
            String title = configManager.getAfkTitle();
            String subtitle = configManager.getAfkSubtitle();
            for (UUID uuid : afkPlayers.keySet()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    MessageUtil.sendTitle(p, title, subtitle, 0, 35, 10);
                }
            }
        }, 0L, 20L);
    }

    /**
     * Verifica se il giocatore e attualmente in modalita AFK.
     */
    public boolean isAfk(Player player) {
        if (player == null) return false;
        return afkPlayers.containsKey(player.getUniqueId());
    }

    /**
     * Mappa non modificabile dei giocatori in AFK.
     */
    public Map<UUID, AfkData> getAfkPlayers() {
        return Collections.unmodifiableMap(afkPlayers);
    }

    /**
     * Alterna lo stato AFK per un giocatore.
     */
    public boolean toggleAfk(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (isAfk(player)) {
            removeAfk(player, false);
            return true;
        } else {
            return setAfk(player);
        }
    }

    /**
     * Mette il giocatore in modalita AFK (Spettatore, titolo fisso, suffisso tab e blocco azioni).
     */
    public boolean setAfk(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (!configManager.isAfkEnabled()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("afk.disabled"));
            return false;
        }

        // Controllo autenticazione
        if (plugin.getAuthManager() != null && !plugin.getAuthManager().isAuthenticated(player)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.blocked-action"));
            return false;
        }

        if (player.isDead()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("afk.cannot-afk-dead"));
            return false;
        }

        // Se seduto, fallo prima alzare
        if (plugin.getSitManager() != null && plugin.getSitManager().isSitting(player)) {
            plugin.getSitManager().dismount(player, false);
        }

        // Se addormentato, fallo svegliare
        if (plugin.getSleepManager() != null && plugin.getSleepManager().isSleeping(player)) {
            plugin.getSleepManager().wakeUp(player, false);
        }

        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }

        UUID uuid = player.getUniqueId();
        GameMode currentMode = player.getGameMode();
        Location currentLoc = player.getLocation().clone();

        afkPlayers.put(uuid, new AfkData(currentMode, currentLoc, System.currentTimeMillis()));

        // Imposta modalita spettatore
        player.setGameMode(GameMode.SPECTATOR);

        // Mostra titolo fisso a schermo intero
        MessageUtil.sendTitle(player, configManager.getAfkTitle(), configManager.getAfkSubtitle(), 10, 35, 10);

        // Aggiorna il suffisso nella tablist
        if (plugin.getRankManager() != null) {
            plugin.getRankManager().updatePlayerTab(player);
        } else {
            player.playerListName(MessageUtil.format("&f" + player.getName() + configManager.getAfkTabSuffix()));
        }

        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("afk.enter"));

        if (configManager.isAfkPlaySound()) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.8f);
        }

        if (configManager.isAfkBroadcast()) {
            String broadcastMsg = configManager.getPrefixedMessage("afk.broadcast-enter", Map.of("player", player.getName()));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    MessageUtil.sendMessage(online, broadcastMsg);
                }
            }
        }

        return true;
    }

    /**
     * Rimuove il giocatore dall'AFK ripristinando la sua gamemode, posizione e tablist, rimuovendo il titolo.
     */
    public boolean removeAfk(Player player, boolean byMovement) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();

        AfkData data = afkPlayers.remove(uuid);
        if (data == null) {
            return false;
        }

        if (player.isOnline()) {
            // Rimuovi immediatamente il titolo a schermo
            MessageUtil.clearTitle(player);

            // Ripristina la gamemode originale
            player.setGameMode(data.previousGameMode());

            // Teletrasporta alla posizione originaria per evitare no-clip o posizioni anomale
            if (data.originalLocation() != null) {
                player.teleport(data.originalLocation());
            }

            // Ripristina la visualizzazione normale in tablist
            if (plugin.getRankManager() != null) {
                plugin.getRankManager().updatePlayerTab(player);
            } else {
                player.playerListName(null);
            }

            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("afk.exit"));

            if (configManager.isAfkPlaySound()) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            }

            if (configManager.isAfkBroadcast()) {
                String broadcastMsg = configManager.getPrefixedMessage("afk.broadcast-exit", Map.of("player", player.getName()));
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player)) {
                        MessageUtil.sendMessage(online, broadcastMsg);
                    }
                }
            }
        }

        return true;
    }

    /**
     * Ripristina tutti i giocatori in AFK alla loro gamemode, tablist e posizione precedente (shutdown o reload).
     */
    public synchronized void cleanup() {
        if (titleTask != null && !titleTask.isCancelled()) {
            titleTask.cancel();
            titleTask = null;
        }

        for (Map.Entry<UUID, AfkData> entry : afkPlayers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                MessageUtil.clearTitle(player);
                player.setGameMode(entry.getValue().previousGameMode());
                if (entry.getValue().originalLocation() != null) {
                    player.teleport(entry.getValue().originalLocation());
                }
                if (plugin.getRankManager() != null) {
                    plugin.getRankManager().updatePlayerTab(player);
                } else {
                    player.playerListName(null);
                }
            }
        }
        afkPlayers.clear();
    }

    /**
     * Ricarica il gestore AFK.
     */
    public synchronized void reload() {
        startTitleTask();
        plugin.getLogger().info("AfkManager ricaricato con successo.");
    }
}
