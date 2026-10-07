package it.caciotta.core.home;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Ascoltatore per interrompere il conto alla rovescia di teletrasporto se il giocatore si muove o subisce danno.
 */
public class HomeListener implements Listener {

    private final HomeManager homeManager;
    private final ConfigManager configManager;

    public HomeListener(CaciottaCore plugin) {
        this.homeManager = plugin.getHomeManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!configManager.isCancelOnMove()) {
            return;
        }

        if (!homeManager.hasPendingTeleport(player.getUniqueId())) {
            return;
        }

        Location from = homeManager.getInitialLocation(player.getUniqueId());
        Location to = event.getTo();
        if (from == null || to == null) {
            return;
        }

        // Se il giocatore si e spostato di almeno un blocco intero
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            homeManager.cancelPendingTeleport(player.getUniqueId(), true, "home.teleport-cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!configManager.isCancelOnDamage()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            if (homeManager.hasPendingTeleport(player.getUniqueId())) {
                homeManager.cancelPendingTeleport(player.getUniqueId(), true, "home.teleport-cancelled-damage");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        homeManager.onPlayerQuit(event.getPlayer());
    }
}
