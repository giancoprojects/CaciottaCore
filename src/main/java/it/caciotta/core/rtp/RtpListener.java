package it.caciotta.core.rtp;

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
 * Ascoltatore per il modulo /rtp:
 * Annulla il conto alla rovescia del teletrasporto se il giocatore si muove o subisce danno.
 */
public class RtpListener implements Listener {

    private final RtpManager rtpManager;
    private final ConfigManager configManager;

    public RtpListener(CaciottaCore plugin) {
        this.rtpManager = plugin.getRtpManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!configManager.isRtpCancelOnMove()) {
            return;
        }

        if (!rtpManager.hasPendingTeleport(player.getUniqueId())) {
            return;
        }

        Location from = rtpManager.getInitialLocation(player.getUniqueId());
        Location to = event.getTo();
        if (from == null || to == null) {
            return;
        }

        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            rtpManager.cancelPendingTeleport(player.getUniqueId(), true, "rtp.teleport-cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!configManager.isRtpCancelOnDamage()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            if (rtpManager.hasPendingTeleport(player.getUniqueId())) {
                rtpManager.cancelPendingTeleport(player.getUniqueId(), true, "rtp.teleport-cancelled-damage");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rtpManager.onPlayerQuit(event.getPlayer());
    }
}

