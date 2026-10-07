package it.caciotta.core.sit;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Ascoltatore di eventi per la gestione di /sit, click su scale e dismount.
 */
public class SitListener implements Listener {

    private final CaciottaCore plugin;
    private final SitManager sitManager;
    private final ConfigManager configManager;

    public SitListener(CaciottaCore plugin) {
        this.plugin = plugin;
        this.sitManager = plugin.getSitManager();
        this.configManager = plugin.getConfigManager();
    }

    /**
     * Gestisce lo smontaggio naturale del giocatore (es. premendo Shift/Sneak).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && event.getDismounted() instanceof ArmorStand stand) {
            sitManager.handleDismountEvent(player, stand);
        }
    }

    /**
     * Permette di sedersi cliccando con il tasto destro su scale o gradini.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!configManager.isSitEnabled() || !configManager.isSitClickToSit()) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isSneaking() || sitManager.isSitting(player)) {
            return;
        }

        if (!player.hasPermission("caciottacore.sit") && !player.isOp()) {
            return;
        }

        // Se configurato, richiede la mano vuota
        if (configManager.isSitRequireEmptyHand()) {
            if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
                return;
            }
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        // Controlla se è una scala o un gradino valido
        if (clicked.getBlockData() instanceof Stairs || clicked.getBlockData() instanceof Slab) {
            // Controlla che non ci sia un blocco solido subito sopra la sedia
            Block blockAbove = clicked.getRelative(0, 1, 0);
            if (!blockAbove.isEmpty() && !blockAbove.isPassable()) {
                return;
            }

            event.setCancelled(true);
            sitManager.sitOnBlock(player, clicked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        sitManager.dismount(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        sitManager.dismount(event.getEntity(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (sitManager.isSitting(player)) {
            sitManager.dismount(player, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        sitManager.onBlockBreak(event.getBlock());
    }
}
