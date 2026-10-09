package it.caciotta.core.sleep;

import it.caciotta.core.CaciottaCore;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Listener per la gestione degli eventi legati al sonno (smontaggio, sneak, disconnessione, letto vanilla).
 */
public class SleepListener implements Listener {

    private final CaciottaCore plugin;
    private final SleepManager sleepManager;

    public SleepListener(CaciottaCore plugin) {
        this.plugin = plugin;
        this.sleepManager = plugin.getSleepManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            sleepManager.handleDismount(player, event.getDismounted());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking() && sleepManager.isSleeping(player)) {
            sleepManager.wakeUp(player, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) {
            sleepManager.handleSleepSuccess(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        sleepManager.wakeUp(event.getPlayer(), true, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sleepManager.wakeUp(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        sleepManager.wakeUp(event.getEntity(), true);
    }
}
