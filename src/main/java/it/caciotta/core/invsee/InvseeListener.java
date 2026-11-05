package it.caciotta.core.invsee;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Listener per la gestione degli eventi dell'interfaccia /invsee e sincronizzazione bidirezionale in tempo reale.
 */
public class InvseeListener implements Listener {

    private final CaciottaCore plugin;
    private final InvseeManager invseeManager;
    private final ConfigManager configManager;

    public InvseeListener(CaciottaCore plugin, InvseeManager invseeManager) {
        this.plugin = plugin;
        this.invseeManager = invseeManager;
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvseeHolder holder)) {
            // Se l'evento è causato da un giocatore che ha dei visualizzatori attivi sul suo inventario
            if (event.getWhoClicked() instanceof Player target && invseeManager.hasViewers(target)) {
                scheduleSyncToViewers(target);
            }
            return;
        }

        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }

        int rawSlot = event.getRawSlot();

        // Click nella parte superiore (la GUI dell'inventario del bersaglio)
        if (rawSlot >= 0 && rawSlot < InvseeManager.INV_SIZE) {
            if (invseeManager.isSeparatorSlot(rawSlot)) {
                event.setCancelled(true);

                // Clic sull'icona Ender Chest
                if (rawSlot == InvseeManager.SLOT_ENDER_CHEST) {
                    Player target = Bukkit.getPlayer(holder.getTargetUuid());
                    if (target != null && target.isOnline()) {
                        invseeManager.openEnderChest(viewer, target);
                    } else {
                        MessageUtil.sendMessage(viewer, configManager.getPrefixedMessage("invsee.player-not-found", java.util.Map.of("target", holder.getTargetName())));
                    }
                }
                return;
            }

            // Slot dell'inventario / armatura / seconda mano cliccato
            boolean canModify = !holder.isReadOnly()
                    && configManager.isInvseeAllowModification()
                    && (viewer.hasPermission("caciottacore.invsee.modify") || viewer.hasPermission("caciottacore.admin") || viewer.isOp());

            if (!canModify) {
                event.setCancelled(true);
                MessageUtil.sendMessage(viewer, configManager.getPrefixedMessage("invsee.cannot-modify"));
                return;
            }

            // Modifica permessa: sincronizza con il giocatore bersaglio
            scheduleSyncToPlayer(event.getInventory(), holder.getTargetUuid());
        } else if (rawSlot >= InvseeManager.INV_SIZE) {
            // Click nell'inventario personale del visualizzatore (in basso)
            if (event.isShiftClick()) {
                boolean canModify = !holder.isReadOnly()
                        && configManager.isInvseeAllowModification()
                        && (viewer.hasPermission("caciottacore.invsee.modify") || viewer.hasPermission("caciottacore.admin") || viewer.isOp());

                if (!canModify) {
                    event.setCancelled(true);
                    MessageUtil.sendMessage(viewer, configManager.getPrefixedMessage("invsee.cannot-modify"));
                    return;
                }

                scheduleSyncToPlayer(event.getInventory(), holder.getTargetUuid());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvseeHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }

        // Verifica se uno dei punti trascinati è nella GUI
        boolean affectedTop = false;
        for (int slot : event.getRawSlots()) {
            if (slot < InvseeManager.INV_SIZE) {
                affectedTop = true;
                if (invseeManager.isSeparatorSlot(slot)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (affectedTop) {
            boolean canModify = !holder.isReadOnly()
                    && configManager.isInvseeAllowModification()
                    && (viewer.hasPermission("caciottacore.invsee.modify") || viewer.hasPermission("caciottacore.admin") || viewer.isOp());

            if (!canModify) {
                event.setCancelled(true);
                MessageUtil.sendMessage(viewer, configManager.getPrefixedMessage("invsee.cannot-modify"));
                return;
            }

            scheduleSyncToPlayer(event.getInventory(), holder.getTargetUuid());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof InvseeHolder holder) {
            if (event.getPlayer() instanceof Player viewer) {
                invseeManager.removeViewer(viewer);

                boolean canModify = !holder.isReadOnly()
                        && configManager.isInvseeAllowModification()
                        && (viewer.hasPermission("caciottacore.invsee.modify") || viewer.hasPermission("caciottacore.admin") || viewer.isOp());

                if (canModify) {
                    Player target = Bukkit.getPlayer(holder.getTargetUuid());
                    if (target != null && target.isOnline()) {
                        invseeManager.syncGuiToPlayer(event.getInventory(), target);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (invseeManager.hasViewers(event.getPlayer())) {
            scheduleSyncToViewers(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player target && invseeManager.hasViewers(target)) {
            scheduleSyncToViewers(target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (invseeManager.hasViewers(event.getPlayer())) {
            scheduleSyncToViewers(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (invseeManager.hasViewers(event.getPlayer())) {
            scheduleSyncToViewers(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        invseeManager.handleTargetQuit(player);
        invseeManager.removeViewer(player);
    }

    private void scheduleSyncToPlayer(org.bukkit.inventory.Inventory gui, java.util.UUID targetUuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null && target.isOnline()) {
                invseeManager.syncGuiToPlayer(gui, target);
            }
        });
    }

    private void scheduleSyncToViewers(Player target) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (target.isOnline()) {
                for (Player viewer : invseeManager.getViewers(target)) {
                    if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() instanceof InvseeHolder holder) {
                        if (holder.getTargetUuid().equals(target.getUniqueId())) {
                            invseeManager.syncPlayerToGui(target, viewer.getOpenInventory().getTopInventory());
                        }
                    }
                }
            }
        });
    }
}

