package it.caciotta.core.afk;

import io.papermc.paper.event.player.AsyncChatEvent;
import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Listener per il rilevamento del movimento tastiera e il blocco totale delle azioni durante l'AFK.
 */
public class AfkListener implements Listener {

    private final CaciottaCore plugin;
    private final AfkManager afkManager;
    private final ConfigManager configManager;

    public AfkListener(CaciottaCore plugin) {
        this.plugin = plugin;
        this.afkManager = plugin.getAfkManager();
        this.configManager = plugin.getConfigManager();
    }

    /**
     * Rileva qualsiasi movimento con la tastiera (variazione coordinate X, Y o Z)
     * e rimuove il giocatore dall'AFK ripristinando gamemode e posizione.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!afkManager.isAfk(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double dx = Math.abs(from.getX() - to.getX());
        double dy = Math.abs(from.getY() - to.getY());
        double dz = Math.abs(from.getZ() - to.getZ());

        // Se le coordinate di posizione cambiano (input tastiera WASD, salto, shift)
        if (dx > 0.001 || dy > 0.001 || dz > 0.001) {
            event.setCancelled(true);
            afkManager.removeAfk(player, true);
        }
    }

    /**
     * Blocca la chat durante l'AFK.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (afkManager.isAfk(player)) {
            event.setCancelled(true);
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("afk.blocked-action"));
        }
    }

    /**
     * Blocca qualsiasi comando tranne /afk durante l'AFK.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (afkManager.isAfk(player)) {
            String message = event.getMessage().toLowerCase().trim();
            if (message.startsWith("/afk") || message.startsWith("/away")) {
                return; // Consenti il comando per uscire manualmente
            }
            event.setCancelled(true);
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("afk.blocked-action"));
        }
    }

    /**
     * Blocca il teletrasporto spectator (clic su altri giocatori o tasti 1-9).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (afkManager.isAfk(player)) {
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Blocca le interazioni con blocchi.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (afkManager.isAfk(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocca le interazioni con entita.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (afkManager.isAfk(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (afkManager.isAfk(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocca il drop di oggetti.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (afkManager.isAfk(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocca la raccolta di oggetti.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && afkManager.isAfk(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocca l'apertura e l'interazione con inventari.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && afkManager.isAfk(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && afkManager.isAfk(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Blocca lo scambio di oggetti tra le mani (tasto F).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (afkManager.isAfk(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Ripristina la gamemode e posizione se il giocatore si disconnette.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (afkManager.isAfk(player)) {
            afkManager.removeAfk(player, false);
        }
    }
}
