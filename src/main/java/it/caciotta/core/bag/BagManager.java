package it.caciotta.core.bag;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestore del modulo Zaino (/bag).
 * Gestisce l'apertura, persistenza, dimensionamento per permessi e ispezione degli zaini dei giocatori.
 */
public class BagManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final File bagsFolder;

    // Inventari attualmente aperti o in memoria per ciascun proprietario (evita desync e duplicazioni)
    private final Map<UUID, Inventory> activeInventories = new ConcurrentHashMap<>();

    public BagManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.bagsFolder = new File(plugin.getDataFolder(), "data/bags");
        if (!bagsFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            bagsFolder.mkdirs();
        }
    }

    private File getPlayerBagFile(UUID uuid) {
        return new File(bagsFolder, uuid.toString() + ".yml");
    }

    /**
     * Determina il numero di righe dello zaino per un giocatore (da 1 a 6 righe).
     */
    public int getPlayerBagRows(Player player) {
        if (player.hasPermission("caciottacore.bag.rows.max") || player.hasPermission("caciottacore.bag.rows.6") || player.isOp()) {
            return 6;
        }
        for (int r = 5; r >= 1; r--) {
            if (player.hasPermission("caciottacore.bag.rows." + r)) {
                return r;
            }
        }
        int defaultRows = configManager.getBagDefaultRows();
        return Math.max(1, Math.min(6, defaultRows));
    }

    /**
     * Calcola le righe necessarie salvate su disco per non perdere slot con oggetti se i permessi cambiano.
     */
    private int getSavedRows(UUID uuid, int defaultRows) {
        File file = getPlayerBagFile(uuid);
        if (!file.exists()) {
            return defaultRows;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int savedRows = yaml.getInt("rows", defaultRows);
        ConfigurationSection itemsSec = yaml.getConfigurationSection("items");
        if (itemsSec != null) {
            int maxSlot = 0;
            for (String key : itemsSec.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot > maxSlot) {
                        maxSlot = slot;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            int neededRows = (maxSlot / 9) + 1;
            savedRows = Math.max(savedRows, neededRows);
        }
        return Math.max(1, Math.min(6, savedRows));
    }

    /**
     * Recupera o crea l'inventario condiviso dello zaino del proprietario.
     */
    public Inventory getOrCreateBag(UUID ownerUuid, String ownerName, int rows, boolean isAdminView) {
        Inventory existing = activeInventories.get(ownerUuid);
        if (existing != null) {
            return existing;
        }

        // Calcola titolo
        String rawTitle;
        if (isAdminView) {
            rawTitle = configManager.getBagTitleAdmin().replace("{player}", ownerName);
        } else {
            rawTitle = configManager.getBagTitle().replace("{player}", ownerName);
        }
        Component titleComponent = MessageUtil.format(rawTitle);

        BagHolder holder = new BagHolder(ownerUuid, ownerName, rows);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, titleComponent);
        holder.setInventory(inventory);

        // Carica oggetti dal file se esiste
        File file = getPlayerBagFile(ownerUuid);
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection itemsSec = yaml.getConfigurationSection("items");
            if (itemsSec != null) {
                for (String key : itemsSec.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(key);
                        if (slot >= 0 && slot < inventory.getSize()) {
                            ItemStack item = yaml.getItemStack("items." + key);
                            inventory.setItem(slot, item);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        activeInventories.put(ownerUuid, inventory);
        return inventory;
    }

    /**
     * Apre lo zaino per un giocatore visualizzatore.
     */
    public void openBag(Player viewer, UUID ownerUuid, String ownerName) {
        boolean isSelf = viewer.getUniqueId().equals(ownerUuid);
        int rows;

        if (isSelf) {
            int playerRows = getPlayerBagRows(viewer);
            int savedRows = getSavedRows(ownerUuid, playerRows);
            rows = Math.max(playerRows, savedRows);
        } else {
            Player targetPlayer = Bukkit.getPlayer(ownerUuid);
            int targetRows = targetPlayer != null ? getPlayerBagRows(targetPlayer) : configManager.getBagDefaultRows();
            int savedRows = getSavedRows(ownerUuid, targetRows);
            rows = Math.max(targetRows, savedRows);
        }

        Inventory inventory = getOrCreateBag(ownerUuid, ownerName, rows, !isSelf);
        viewer.openInventory(inventory);

        if (configManager.isBagPlaySound()) {
            playSound(viewer, configManager.getBagSoundOpen(), Sound.ITEM_BUNDLE_DROP_CONTENTS);
        }
    }

    /**
     * Salva i contenuti di un inventario su disco.
     */
    public synchronized void saveBag(UUID ownerUuid, Inventory inventory) {
        if (inventory == null) {
            return;
        }

        if (!bagsFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            bagsFolder.mkdirs();
        }

        File file = getPlayerBagFile(ownerUuid);
        YamlConfiguration yaml = new YamlConfiguration();

        int rows = inventory.getSize() / 9;
        yaml.set("rows", rows);

        String ownerName = "";
        if (inventory.getHolder() instanceof BagHolder holder) {
            ownerName = holder.getOwnerName();
        }
        yaml.set("owner", ownerName);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                yaml.set("items." + slot, item);
            }
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare lo zaino per " + ownerUuid + " in " + file.getName(), e);
        }
    }

    /**
     * Notifica di chiusura inventario da parte di un visualizzatore.
     */
    public void onInventoryClose(Player viewer, Inventory inventory) {
        if (!(inventory.getHolder() instanceof BagHolder holder)) {
            return;
        }

        // Se chiude l'ultimo giocatore che stava guardando l'inventario
        if (inventory.getViewers().size() <= 1) {
            saveBag(holder.getOwnerUuid(), inventory);
            activeInventories.remove(holder.getOwnerUuid());
        }

        if (configManager.isBagPlaySound()) {
            playSound(viewer, configManager.getBagSoundClose(), Sound.ITEM_BUNDLE_REMOVE_ONE);
        }
    }

    /**
     * Salva tutti gli inventari attualmente aperti/attivi su disco.
     */
    public synchronized void saveAll() {
        for (Map.Entry<UUID, Inventory> entry : activeInventories.entrySet()) {
            saveBag(entry.getKey(), entry.getValue());
        }
    }

    public synchronized void reload() {
        saveAll();
        activeInventories.clear();
        plugin.getLogger().info("BagManager ricaricato con successo.");
    }

    public synchronized void cleanup() {
        for (Inventory inv : activeInventories.values()) {
            for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
                viewer.closeInventory();
            }
        }
        saveAll();
        activeInventories.clear();
    }

    private void playSound(Player player, String soundName, Sound fallback) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception e) {
            try {
                player.playSound(player.getLocation(), fallback, 1.0f, 1.0f);
            } catch (Exception ignored) {
            }
        }
    }
}
