package it.caciotta.core.cestino;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Ascoltatore per gli eventi legati al Cestino Comune (/cestino):
 * Salvataggio automatico dei contenuti alla chiusura e blocco inserimento
 * oggetti vietati (es. Shulker Box, se configurato).
 */
public class CestinoListener implements Listener {

    private final CestinoManager cestinoManager;
    private final ConfigManager configManager;

    public CestinoListener(CaciottaCore plugin) {
        this.cestinoManager = plugin.getCestinoManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CestinoHolder) {
            cestinoManager.saveData();

            if (event.getPlayer() instanceof Player player && configManager.isCestinoPlaySound()) {
                try {
                    player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CestinoHolder)) {
            return;
        }

        if (!configManager.isCestinoDisallowShulkerBoxes()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Clicca direttamente nel cestino posizionando dal cursore
        if (event.getClickedInventory() == event.getInventory()) {
            ItemStack cursor = event.getCursor();
            if (isShulkerBox(cursor)) {
                event.setCancelled(true);
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("cestino.item-forbidden", Map.of("item", "Shulker Box")));
                return;
            }

            // Tasto rapido numerico (hotbar swap)
            if (event.getClick() == ClickType.NUMBER_KEY) {
                int hotbarSlot = event.getHotbarButton();
                if (hotbarSlot >= 0 && hotbarSlot < 9) {
                    ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                    if (isShulkerBox(hotbarItem)) {
                        event.setCancelled(true);
                        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("cestino.item-forbidden", Map.of("item", "Shulker Box")));
                        return;
                    }
                }
            }

            // Swap offhand
            if (event.getClick() == ClickType.SWAP_OFFHAND) {
                ItemStack offhandItem = player.getInventory().getItemInOffHand();
                if (isShulkerBox(offhandItem)) {
                    event.setCancelled(true);
                    MessageUtil.sendMessage(player, configManager.getPrefixedMessage("cestino.item-forbidden", Map.of("item", "Shulker Box")));
                    return;
                }
            }
        } else if (event.getClickedInventory() == player.getInventory() && event.isShiftClick()) {
            // Shift-click dal proprio inventario verso il cestino
            ItemStack current = event.getCurrentItem();
            if (isShulkerBox(current)) {
                event.setCancelled(true);
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("cestino.item-forbidden", Map.of("item", "Shulker Box")));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof CestinoHolder)) {
            return;
        }

        if (!configManager.isCestinoDisallowShulkerBoxes()) {
            return;
        }

        if (isShulkerBox(event.getOldCursor())) {
            int cestinoSlots = event.getInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < cestinoSlots) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("cestino.item-forbidden", Map.of("item", "Shulker Box")));
                    }
                    return;
                }
            }
        }
    }

    private boolean isShulkerBox(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return item.getType().name().endsWith("SHULKER_BOX");
    }
}

