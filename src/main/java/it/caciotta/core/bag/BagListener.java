package it.caciotta.core.bag;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Listener per gli eventi legati allo zaino (/bag):
 * salvataggio automatico alla chiusura e blocco inserimento oggetti vietati (es. Shulker Box).
 */
public class BagListener implements Listener {

    private final CaciottaCore plugin;
    private final BagManager bagManager;
    private final ConfigManager configManager;

    public BagListener(CaciottaCore plugin) {
        this.plugin = plugin;
        this.bagManager = plugin.getBagManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && event.getInventory().getHolder() instanceof BagHolder) {
            bagManager.onInventoryClose(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BagHolder)) {
            return;
        }

        if (!configManager.isBagDisallowShulkerBoxes()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Clicca direttamente nello zaino posizionando l'oggetto dal cursore
        if (event.getClickedInventory() == event.getInventory()) {
            ItemStack cursor = event.getCursor();
            if (isShulkerBox(cursor)) {
                event.setCancelled(true);
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("bag.item-forbidden", Map.of("item", "Shulker Box")));
                return;
            }

            // Tasto rapido numerico (hotbar swap)
            if (event.getClick() == ClickType.NUMBER_KEY) {
                int hotbarSlot = event.getHotbarButton();
                if (hotbarSlot >= 0 && hotbarSlot < 9) {
                    ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                    if (isShulkerBox(hotbarItem)) {
                        event.setCancelled(true);
                        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("bag.item-forbidden", Map.of("item", "Shulker Box")));
                        return;
                    }
                }
            }

            // Swap offhand
            if (event.getClick() == ClickType.SWAP_OFFHAND) {
                ItemStack offhandItem = player.getInventory().getItemInOffHand();
                if (isShulkerBox(offhandItem)) {
                    event.setCancelled(true);
                    MessageUtil.sendMessage(player, configManager.getPrefixedMessage("bag.item-forbidden", Map.of("item", "Shulker Box")));
                    return;
                }
            }
        } else if (event.getClickedInventory() == player.getInventory() && event.isShiftClick()) {
            // Shift-click dal proprio inventario verso lo zaino
            ItemStack current = event.getCurrentItem();
            if (isShulkerBox(current)) {
                event.setCancelled(true);
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("bag.item-forbidden", Map.of("item", "Shulker Box")));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof BagHolder)) {
            return;
        }

        if (!configManager.isBagDisallowShulkerBoxes()) {
            return;
        }

        ItemStack oldCursor = event.getOldCursor();
        if (isShulkerBox(oldCursor)) {
            int topSize = event.getInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < topSize) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("bag.item-forbidden", Map.of("item", "Shulker Box")));
                    }
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof BagHolder) {
            event.getPlayer().closeInventory();
        }
    }

    private boolean isShulkerBox(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return item.getType().name().endsWith("SHULKER_BOX");
    }
}
