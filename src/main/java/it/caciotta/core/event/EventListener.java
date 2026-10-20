package it.caciotta.core.event;

import it.caciotta.core.CaciottaCore;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Ascoltatore di eventi per il modulo Eventi a Premi.
 * Monitora lo scavo dei blocchi per l'evento Scavatore e l'interazione con gli oggetti premio.
 */
public class EventListener implements Listener {

    private final CaciottaCore plugin;
    private final EventManager eventManager;

    public EventListener(CaciottaCore plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Non conteggiare se il giocatore è in creativa o spettatore
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        GameEvent current = eventManager.getCurrentEvent();
        if (current == null || !current.isRunning() || current.getType() != EventType.BLOCK_BREAK) {
            return;
        }

        // Obbligo di conferma partecipazione: gestito internamente in recordProgress
        eventManager.recordProgress(player, 1);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.RECOVERY_COMPASS) {
            return;
        }

        Player player = event.getPlayer();
        if (!eventManager.hasPendingDungeon(player.getUniqueId())) {
            return;
        }

        // Il giocatore sta usando la pergamena per teletrasportarsi al dungeon
        event.setCancelled(true);
        boolean success = eventManager.executeDungeonTeleport(player);
        if (success) {
            item.subtract(1);
        }
    }
}
