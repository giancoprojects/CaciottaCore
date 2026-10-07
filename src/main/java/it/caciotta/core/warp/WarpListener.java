package it.caciotta.core.warp;

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
 * Ascoltatore per cancellare il teletrasporto warp se il giocatore si muove o subisce danno.
 */
public class WarpListener implements Listener {

    private final WarpManager warpManager;
    private final ConfigManager configManager;

    public WarpListener(CaciottaCore plugin) {
        this.warpManager = plugin.getWarpManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!configManager.isWarpCancelOnMove()) {
            return;
        }

        if (!warpManager.hasPendingTeleport(player.getUniqueId())) {
            return;
        }

        Location from = warpManager.getInitialLocation(player.getUniqueId());
        Location to = event.getTo();
        if (from == null || to == null) {
            return;
        }

        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            warpManager.cancelPendingTeleport(player.getUniqueId(), true, "warp.teleport-cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!configManager.isWarpCancelOnDamage()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            if (warpManager.hasPendingTeleport(player.getUniqueId())) {
                warpManager.cancelPendingTeleport(player.getUniqueId(), true, "warp.teleport-cancelled-damage");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        warpManager.onPlayerQuit(event.getPlayer());
    }
}
