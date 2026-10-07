package it.caciotta.core.scoreboard;

import it.caciotta.core.CaciottaCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Ascoltatore di eventi per la gestione automatica della Scoreboard.
 */
public class ScoreboardListener implements Listener {

    private final CaciottaCore plugin;

    public ScoreboardListener(CaciottaCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Attendi 1 tick per consentire a Paper di caricare completamente lo stato del giocatore
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getScoreboardManager().createScoreboard(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getScoreboardManager().removeScoreboard(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Aggiorna istantaneamente la scoreboard per la vittima e per il killer
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (victim.isOnline()) {
                plugin.getScoreboardManager().update(victim);
            }
            if (killer != null && killer.isOnline()) {
                plugin.getScoreboardManager().update(killer);
            }
        }, 1L);
    }
}
