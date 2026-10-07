package it.caciotta.core.tpback;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

/**
 * Ascoltatore di eventi per il modulo /tpback: registra la morte del giocatore
 * e gestisce l'interruzione del teletrasporto su movimento o danno.
 */
public class TpBackListener implements Listener {

    private final TpBackManager tpBackManager;
    private final ConfigManager configManager;

    public TpBackListener(CaciottaCore plugin) {
        this.tpBackManager = plugin.getTpBackManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Location deathLoc = victim.getLocation().clone();

        tpBackManager.recordDeath(victim, deathLoc);

        if (configManager.isTpbackNotifyOnDeath() && victim.hasPermission("caciottacore.tpback.use")) {
            if (!tpBackManager.hasReachedDailyLimit(victim)) {
                int timeout = configManager.getTpbackTimeoutSeconds();
                int remaining = tpBackManager.getRemainingDailyUses(victim);
                int max = tpBackManager.getMaxDailyUses(victim);

                String rawNotice = configManager.getPrefixedMessage("tpback.death-notice", Map.of(
                        "seconds", String.valueOf(timeout),
                        "remaining", remaining == Integer.MAX_VALUE ? "∞" : String.valueOf(remaining),
                        "max", max == Integer.MAX_VALUE ? "∞" : String.valueOf(max)
                ));

                Component notice = MessageUtil.format(rawNotice)
                        .hoverEvent(HoverEvent.showText(MessageUtil.format("&eClicca qui per usare &6/tpback&e!")))
                        .clickEvent(ClickEvent.runCommand("/tpback"));

                victim.sendMessage(notice);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!configManager.isTpbackCancelOnMove()) {
            return;
        }

        if (!tpBackManager.hasPendingTeleport(player.getUniqueId())) {
            return;
        }

        Location from = tpBackManager.getInitialLocation(player.getUniqueId());
        Location to = event.getTo();
        if (from == null || to == null) {
            return;
        }

        // Se il giocatore si è spostato di almeno un blocco intero
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            tpBackManager.cancelPendingTeleport(player.getUniqueId(), true, "tpback.teleport-cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!configManager.isTpbackCancelOnDamage()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            if (tpBackManager.hasPendingTeleport(player.getUniqueId())) {
                tpBackManager.cancelPendingTeleport(player.getUniqueId(), true, "tpback.teleport-cancelled-damage");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tpBackManager.onPlayerQuit(event.getPlayer());
    }
}
