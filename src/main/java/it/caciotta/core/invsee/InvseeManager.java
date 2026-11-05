package it.caciotta.core.invsee;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestore del modulo /invsee per l'ispezione e la gestione dell'inventario dei giocatori in tempo reale.
 */
public class InvseeManager {

    public static final int INV_SIZE = 54;
    public static final int SLOT_HELMET = 45;
    public static final int SLOT_CHESTPLATE = 46;
    public static final int SLOT_LEGGINGS = 47;
    public static final int SLOT_BOOTS = 48;
    public static final int SLOT_OFFHAND = 50;
    public static final int SLOT_INFO = 52;
    public static final int SLOT_ENDER_CHEST = 53;

    private final CaciottaCore plugin;
    private final ConfigManager configManager;

    // Mappa tra UUID del giocatore ispezionato e insieme dei visualizzatori attivi
    private final Map<UUID, Set<Player>> activeViewers = new ConcurrentHashMap<>();

    private BukkitTask heartbeatTask;

    public InvseeManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        startHeartbeat();
    }

    private void startHeartbeat() {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel();
        }
        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeViewers.isEmpty()) {
                return;
            }

            for (Map.Entry<UUID, Set<Player>> entry : activeViewers.entrySet()) {
                UUID targetUuid = entry.getKey();
                Player target = Bukkit.getPlayer(targetUuid);
                if (target == null || !target.isOnline()) {
                    continue;
                }

                for (Player viewer : entry.getValue()) {
                    if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() instanceof InvseeHolder holder) {
                        if (holder.getTargetUuid().equals(targetUuid)) {
                            updatePlayerInfo(viewer.getOpenInventory().getTopInventory(), target);
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    /**
     * Verifica se uno slot appartiene all'area modificabile dell'inventario.
     */
    public boolean isInteractableSlot(int slot) {
        // 0-35: Storage e Hotbar
        // 45-48: Armatura (Elmo, Pettorina, Gambali, Stivali)
        // 50: Seconda mano
        return (slot >= 0 && slot <= 35) || (slot >= 45 && slot <= 48) || slot == 50;
    }

    /**
     * Verifica se uno slot è un separatore decorativo o un pulsante speciale non posizionabile.
     */
    public boolean isSeparatorSlot(int slot) {
        return (slot >= 36 && slot <= 44) || slot == 49 || slot == 51 || slot == 52 || slot == 53;
    }

    /**
     * Apre il menu di ispezione inventario per il visualizzatore.
     */
    public void openInvsee(Player viewer, Player target, boolean readOnly) {
        InvseeHolder holder = new InvseeHolder(target, readOnly);
        String rawTitle = configManager.getInvseeTitle().replace("{player}", target.getName());
        if (readOnly) {
            rawTitle += " &8[&cLettura&8]";
        }
        Inventory gui = Bukkit.createInventory(holder, INV_SIZE, MessageUtil.format(rawTitle));
        holder.setInventory(gui);

        // Popola i contenuti dell'inventario del giocatore
        syncPlayerToGui(target, gui);

        // Popola i divisori grafici e pulsanti
        setupDecorations(gui, target);

        // Apri l'interfaccia
        viewer.openInventory(gui);

        if (configManager.isInvseePlaySound()) {
            try {
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
            } catch (Exception ignored) {
            }
        }

        activeViewers.computeIfAbsent(target.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(viewer);
    }

    /**
     * Apre l'Ender Chest del giocatore bersaglio per il visualizzatore.
     */
    public void openEnderChest(Player viewer, Player target) {
        viewer.openInventory(target.getEnderChest());
        if (configManager.isInvseePlaySound()) {
            try {
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.0f);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Imposta i separatori grafici e le descrizioni degli slot di equipaggiamento.
     */
    private void setupDecorations(Inventory gui, Player target) {
        ItemStack blackPane = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");

        // Riga 5: divisori e guide per gli slot sottostanti
        gui.setItem(36, createPane(Material.GRAY_STAINED_GLASS_PANE, "&8⬆ &7Inventario &8| &8⬇ &6Elmo"));
        gui.setItem(37, createPane(Material.GRAY_STAINED_GLASS_PANE, "&8⬆ &7Inventario &8| &8⬇ &6Pettorina"));
        gui.setItem(38, createPane(Material.GRAY_STAINED_GLASS_PANE, "&8⬆ &7Inventario &8| &8⬇ &6Gambali"));
        gui.setItem(39, createPane(Material.GRAY_STAINED_GLASS_PANE, "&8⬆ &7Inventario &8| &8⬇ &6Stivali"));
        gui.setItem(40, blackPane);
        gui.setItem(41, createPane(Material.GRAY_STAINED_GLASS_PANE, "&8⬇ &dSeconda Mano (Off-Hand)"));
        gui.setItem(42, blackPane);
        gui.setItem(43, createPane(Material.GRAY_STAINED_GLASS_PANE, "&8⬇ &bInformazioni Giocatore"));
        gui.setItem(44, createPane(Material.PURPLE_STAINED_GLASS_PANE, "&8⬇ &5Ender Chest Personale"));

        // Riga 6: divisori tra equipaggiamento e pulsanti
        gui.setItem(49, blackPane);
        gui.setItem(51, blackPane);

        // Slot 52: Scheda Giocatore
        updatePlayerInfo(gui, target);

        // Slot 53: Icona Ender Chest
        ItemStack ecItem = new ItemStack(Material.ENDER_CHEST);
        ItemMeta ecMeta = ecItem.getItemMeta();
        if (ecMeta != null) {
            ecMeta.displayName(MessageUtil.format("&5&lEnder Chest &7(" + target.getName() + ")"));
            List<Component> lore = new ArrayList<>();
            lore.add(MessageUtil.format("&7Clicca per visualizzare e gestire"));
            lore.add(MessageUtil.format("&7l'Ender Chest personale del giocatore."));
            ecMeta.lore(lore);
            ecItem.setItemMeta(ecMeta);
        }
        gui.setItem(SLOT_ENDER_CHEST, ecItem);
    }

    /**
     * Sincronizza il contenuto del giocatore bersaglio all'interno della GUI del visualizzatore.
     */
    public void syncPlayerToGui(Player target, Inventory gui) {
        // Storage: slots 9-35 -> GUI slots 0-26
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, target.getInventory().getItem(i + 9));
        }

        // Hotbar: slots 0-8 -> GUI slots 27-35
        for (int i = 0; i < 9; i++) {
            gui.setItem(i + 27, target.getInventory().getItem(i));
        }

        // Armatura
        gui.setItem(SLOT_HELMET, target.getInventory().getHelmet());
        gui.setItem(SLOT_CHESTPLATE, target.getInventory().getChestplate());
        gui.setItem(SLOT_LEGGINGS, target.getInventory().getLeggings());
        gui.setItem(SLOT_BOOTS, target.getInventory().getBoots());

        // Seconda mano
        gui.setItem(SLOT_OFFHAND, target.getInventory().getItemInOffHand());

        // Info
        updatePlayerInfo(gui, target);
    }

    /**
     * Sincronizza il contenuto modificato nella GUI direttamente sull'inventario del giocatore bersaglio.
     */
    public void syncGuiToPlayer(Inventory gui, Player target) {
        if (target == null || !target.isOnline()) {
            return;
        }

        // Storage: GUI slots 0-26 -> target slots 9-35
        for (int i = 0; i < 27; i++) {
            target.getInventory().setItem(i + 9, gui.getItem(i));
        }

        // Hotbar: GUI slots 27-35 -> target slots 0-8
        for (int i = 0; i < 9; i++) {
            target.getInventory().setItem(i, gui.getItem(i + 27));
        }

        // Armatura
        target.getInventory().setHelmet(gui.getItem(SLOT_HELMET));
        target.getInventory().setChestplate(gui.getItem(SLOT_CHESTPLATE));
        target.getInventory().setLeggings(gui.getItem(SLOT_LEGGINGS));
        target.getInventory().setBoots(gui.getItem(SLOT_BOOTS));

        // Seconda mano
        target.getInventory().setItemInOffHand(gui.getItem(SLOT_OFFHAND));

        target.updateInventory();
    }

    /**
     * Aggiorna la testa e le statistiche dinamiche del giocatore.
     */
    public void updatePlayerInfo(Inventory gui, Player target) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.displayName(MessageUtil.format("&6&lScheda Giocatore: &e" + target.getName()));
            List<Component> lore = new ArrayList<>();
            lore.add(MessageUtil.format("&7Vita: &a" + (int) Math.ceil(target.getHealth()) + "&7/&a" + (int) target.getMaxHealth() + " &c❤"));
            lore.add(MessageUtil.format("&7Fame: &e" + target.getFoodLevel() + "&7/&e20 &6🍗"));
            lore.add(MessageUtil.format("&7Livello XP: &b" + target.getLevel() + " &a🌟"));
            lore.add(MessageUtil.format("&7Modalita: &e" + target.getGameMode().name()));
            lore.add(MessageUtil.format("&7Mondo: &f" + target.getWorld().getName()));
            lore.add(MessageUtil.format("&7Coordinate: &7X: &e" + target.getLocation().getBlockX() + " &7Y: &e" + target.getLocation().getBlockY() + " &7Z: &e" + target.getLocation().getBlockZ()));
            lore.add(MessageUtil.format("&7Ping: &a" + target.getPing() + "ms"));
            if (target.getAddress() != null) {
                lore.add(MessageUtil.format("&7IP: &8" + target.getAddress().getAddress().getHostAddress()));
            }
            meta.lore(lore);
            skull.setItemMeta(meta);
        }
        gui.setItem(SLOT_INFO, skull);
    }

    private ItemStack createPane(Material material, String name) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.format(name));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    public boolean hasViewers(Player target) {
        if (target == null) return false;
        Set<Player> viewers = activeViewers.get(target.getUniqueId());
        return viewers != null && !viewers.isEmpty();
    }

    public Set<Player> getViewers(Player target) {
        if (target == null) return Collections.emptySet();
        Set<Player> viewers = activeViewers.get(target.getUniqueId());
        return viewers != null ? viewers : Collections.emptySet();
    }

    public void removeViewer(Player viewer) {
        if (viewer == null) return;
        for (Iterator<Map.Entry<UUID, Set<Player>>> it = activeViewers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Set<Player>> entry = it.next();
            entry.getValue().remove(viewer);
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    public void handleTargetQuit(Player target) {
        if (target == null) return;
        Set<Player> viewers = activeViewers.remove(target.getUniqueId());
        if (viewers != null) {
            for (Player viewer : viewers) {
                if (viewer.isOnline()) {
                    viewer.closeInventory();
                    MessageUtil.sendMessage(viewer, configManager.getPrefixedMessage("invsee.target-disconnected", Map.of("target", target.getName())));
                }
            }
        }
    }

    public synchronized void reload() {
        startHeartbeat();
        plugin.getLogger().info("InvseeManager ricaricato con successo.");
    }

    public synchronized void cleanup() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }

        // Chiudi tutti gli inventari di ispezione aperti
        for (Set<Player> viewers : activeViewers.values()) {
            for (Player viewer : viewers) {
                if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() instanceof InvseeHolder) {
                    viewer.closeInventory();
                }
            }
        }
        activeViewers.clear();
    }
}

