package it.caciotta.core.tpa;

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
 * Ascoltatore per interrompere il conto alla rovescia TPA se il giocatore si muove o subisce danno.
 */
public class TpaListener implements Listener {

    private final TpaManager tpaManager;
    private final ConfigManager configManager;

    public TpaListener(CaciottaCore plugin) {
        this.tpaManager = plugin.getTpaManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!configManager.isTpaCancelOnMove()) {
            return;
        }

        if (!tpaManager.hasPendingTeleport(player.getUniqueId())) {
            return;
        }

        Location from = tpaManager.getInitialLocation(player.getUniqueId());
        Location to = event.getTo();
        if (from == null || to == null) {
            return;
        }

        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            tpaManager.cancelPendingTeleport(player.getUniqueId(), true, "tpa.teleport-cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!configManager.isTpaCancelOnDamage()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            if (tpaManager.hasPendingTeleport(player.getUniqueId())) {
                tpaManager.cancelPendingTeleport(player.getUniqueId(), true, "tpa.teleport-cancelled-damage");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tpaManager.onPlayerQuit(event.getPlayer());
    }
}
