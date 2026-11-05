package it.caciotta.core.cestino;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Gestore del Cestino Comune (/cestino):
 * Inventario condiviso tra tutti i giocatori sul server in cui è possibile
 * depositare o prelevare liberamente oggetti.
 * Si svuota automaticamente ogni 15 minuti (configurabile).
 */
public class CestinoManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final File dataFile;

    private CestinoHolder holder;
    private Inventory inventory;
    private long nextResetTime;
    private BukkitTask timerTask;

    public CestinoManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.dataFile = new File(plugin.getDataFolder(), "data/cestino.yml");

        initializeInventory();
        loadData();
        startTimer();
    }

    private void initializeInventory() {
        int rows = Math.max(1, Math.min(6, configManager.getCestinoRows()));
        String title = configManager.getCestinoTitle();

        this.holder = new CestinoHolder();
        this.inventory = Bukkit.createInventory(holder, rows * 9, MessageUtil.format(title));
        this.holder.setInventory(this.inventory);
    }

    /**
     * Avvia il task periodico che controlla ogni secondo il tempo rimanente allo svuotamento.
     */
    private void startTimer() {
        if (timerTask != null) {
            timerTask.cancel();
        }

        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() >= nextResetTime) {
                clearCestino(configManager.isCestinoBroadcastOnClear(), false);
            }
        }, 20L, 20L);
    }

    /**
     * Apre il cestino comune per il giocatore specificato.
     */
    public void openCestino(Player player) {
        if (!configManager.isCestinoEnabled()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("cestino.disabled"));
            return;
        }

        if (player.isDead()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("cestino.cannot-open-dead"));
            return;
        }

        player.openInventory(inventory);
        MessageUtil.sendMessage(player, configManager.getPrefixedMessage("cestino.opened"));

        if (configManager.isCestinoPlaySound()) {
            try {
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Svuota completamente il cestino comune e riavvia il conto alla rovescia.
     *
     * @param broadcast se inviare l'annuncio globale in chat a tutti i giocatori
     * @param manual    se lo svuotamento è avvenuto manualmente tramite comando staff
     */
    public synchronized void clearCestino(boolean broadcast, boolean manual) {
        // Notifica e suono per tutti i giocatori che hanno l'inventario aperto in questo momento
        List<HumanEntity> viewers = new ArrayList<>(inventory.getViewers());
        for (HumanEntity viewer : viewers) {
            if (viewer instanceof Player player) {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("cestino.cleared-viewer"));
                if (configManager.isCestinoPlaySound()) {
                    try {
                        player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // Svuota gli slot
        inventory.clear();

        // Ricalcola la prossima scadenza
        int minutes = configManager.getCestinoResetIntervalMinutes();
        this.nextResetTime = System.currentTimeMillis() + (minutes * 60 * 1000L);

        saveData();

        // Annuncio broadcast globale se abilitato
        if (broadcast && !manual) {
            MessageUtil.broadcast(configManager.getPrefixedMessage("cestino.cleared-broadcast",
                    Map.of("minutes", String.valueOf(minutes))));
        }
    }

    /**
     * Restituisce i secondi rimanenti prima del prossimo svuotamento.
     */
    public long getRemainingSeconds() {
        long diff = nextResetTime - System.currentTimeMillis();
        return Math.max(0, diff / 1000L);
    }

    /**
     * Restituisce il countdown formattato come mm:ss (es. "14:32").
     */
    public String getFormattedCountdown() {
        long sec = getRemainingSeconds();
        long mins = sec / 60;
        long remainingSec = sec % 60;
        return String.format(Locale.ROOT, "%02d:%02d", mins, remainingSec);
    }

    /**
     * Restituisce il countdown formattato dettagliato (es. "14m 32s").
     */
    public String getFormattedCountdownDetailed() {
        long sec = getRemainingSeconds();
        long mins = sec / 60;
        long remainingSec = sec % 60;
        return String.format(Locale.ROOT, "%02dm %02ds", mins, remainingSec);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public synchronized void loadData() {
        int intervalMinutes = configManager.getCestinoResetIntervalMinutes();
        long defaultNext = System.currentTimeMillis() + (intervalMinutes * 60 * 1000L);

        if (!dataFile.exists()) {
            this.nextResetTime = defaultNext;
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        this.nextResetTime = config.getLong("next-reset-time", defaultNext);

        // Se la scadenza era già passata a server spento, azzera e riprogramma
        if (System.currentTimeMillis() >= this.nextResetTime) {
            this.nextResetTime = defaultNext;
            inventory.clear();
            saveData();
            return;
        }

        // Ripristina gli oggetti negli slot
        inventory.clear();
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot >= 0 && slot < inventory.getSize()) {
                        ItemStack item = itemsSection.getItemStack(key);
                        if (item != null) {
                            inventory.setItem(slot, item);
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    public synchronized void saveData() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }

            YamlConfiguration config = new YamlConfiguration();
            config.set("next-reset-time", nextResetTime);

            ConfigurationSection itemsSection = config.createSection("items");
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && !item.getType().isAir()) {
                    itemsSection.set(String.valueOf(i), item);
                }
            }

            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare i dati del cestino comune in " + dataFile.getName(), e);
        }
    }

    public synchronized void reload() {
        if (timerTask != null) {
            timerTask.cancel();
        }

        saveData();

        int newRows = Math.max(1, Math.min(6, configManager.getCestinoRows()));
        String newTitle = configManager.getCestinoTitle();
        int currentSize = inventory.getSize();
        int newSize = newRows * 9;

        // Se dimensione o titolo sono cambiati, aggiorna l'inventario preservando gli item
        if (currentSize != newSize) {
            ItemStack[] oldContents = inventory.getContents();
            this.holder = new CestinoHolder();
            this.inventory = Bukkit.createInventory(holder, newSize, MessageUtil.format(newTitle));
            this.holder.setInventory(this.inventory);

            for (int i = 0; i < Math.min(oldContents.length, newSize); i++) {
                inventory.setItem(i, oldContents[i]);
            }
        }

        startTimer();
        plugin.getLogger().info("CestinoManager ricaricato con successo.");
    }

    public synchronized void cleanup() {
        if (timerTask != null) {
            timerTask.cancel();
        }
        saveData();
    }
}

