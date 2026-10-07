package it.caciotta.core.sit;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestore principale per il sistema /sit e seduta su blocchi/scale.
 */
public class SitManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;

    // Mappa dei giocatori attualmente seduti e relativa sedia ArmorStand
    private final Map<UUID, ArmorStand> chairs = new ConcurrentHashMap<>();
    // Mappa delle posizioni originarie prima di sedersi
    private final Map<UUID, Location> preSitLocations = new ConcurrentHashMap<>();
    // Mappa della posizione del blocco su cui si è seduti
    private final Map<Location, UUID> occupiedBlocks = new ConcurrentHashMap<>();

    public SitManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    /**
     * Verifica se il giocatore è attualmente seduto.
     */
    public boolean isSitting(Player player) {
        if (player == null) return false;
        return chairs.containsKey(player.getUniqueId());
    }

    /**
     * Fa sedere il giocatore nella sua posizione attuale.
     */
    @SuppressWarnings("deprecation")
    public boolean sit(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (isSitting(player)) {
            dismount(player, true);
            return true;
        }

        // Controlli di validità
        if (plugin.getAuthManager() != null && !plugin.getAuthManager().isAuthenticated(player)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.blocked-action"));
            return false;
        }

        if (player.isInsideVehicle()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sit.already-sitting"));
            return false;
        }

        if (player.isGliding() || player.isSwimming() || player.isSleeping() || player.isDead()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sit.cannot-sit"));
            return false;
        }

        Location playerLoc = player.getLocation();
        Block blockBelow = playerLoc.getBlock().getRelative(BlockFace.DOWN);

        // Controllo liquidi
        if (playerLoc.getBlock().isLiquid() || blockBelow.isLiquid()) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sit.cannot-sit-liquid"));
            return false;
        }

        // Controllo aria se non è a terra
        if (!player.isOnGround() && (blockBelow.isEmpty() || !blockBelow.getType().isSolid())) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sit.cannot-sit-air"));
            return false;
        }

        // Salva la posizione prima della seduta
        preSitLocations.put(player.getUniqueId(), playerLoc.clone());

        Location seatLoc = playerLoc.clone();
        // Calcola l'offset per allineare il giocatore al suolo
        spawnChairAndSit(player, seatLoc, null);
        return true;
    }

    /**
     * Fa sedere il giocatore su uno specifico blocco (es. gradino o scala).
     */
    public boolean sitOnBlock(Player player, Block block) {
        if (player == null || !player.isOnline() || block == null) {
            return false;
        }

        if (isSitting(player)) {
            dismount(player, true);
            return true;
        }

        if (plugin.getAuthManager() != null && !plugin.getAuthManager().isAuthenticated(player)) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.blocked-action"));
            return false;
        }

        Location blockCenter = block.getLocation().clone().add(0.5, 0, 0.5);

        // Verifica se il blocco è già occupato
        if (occupiedBlocks.containsKey(block.getLocation())) {
            UUID occupierId = occupiedBlocks.get(block.getLocation());
            Player occupier = Bukkit.getPlayer(occupierId);
            if (occupier != null && occupier.isOnline() && isSitting(occupier)) {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sit.occupied"));
                return false;
            } else {
                occupiedBlocks.remove(block.getLocation());
            }
        }

        Location seatLoc = blockCenter.clone();
        float yaw = player.getLocation().getYaw();

        if (block.getBlockData() instanceof Stairs stairs) {
            if (stairs.getHalf() != Stairs.Half.BOTTOM) {
                return false;
            }
            // Allinea la visuale alla direzione della scala
            yaw = switch (stairs.getFacing()) {
                case NORTH -> 0f;
                case SOUTH -> 180f;
                case WEST -> 270f;
                case EAST -> 90f;
                default -> yaw;
            };
            seatLoc.setY(block.getY() + 0.5);
        } else if (block.getBlockData() instanceof Slab slab) {
            if (slab.getType() == Slab.Type.TOP) {
                return false;
            }
            seatLoc.setY(block.getY() + 0.5);
        } else {
            seatLoc.setY(block.getY() + 1.0);
        }

        seatLoc.setYaw(yaw);
        seatLoc.setPitch(0f);

        preSitLocations.put(player.getUniqueId(), player.getLocation().clone());
        occupiedBlocks.put(block.getLocation(), player.getUniqueId());

        spawnChairAndSit(player, seatLoc, block.getLocation());
        return true;
    }

    private void spawnChairAndSit(Player player, Location seatLoc, Location blockLoc) {
        // Un ArmorStand adulto senza marker ha l'attacco del passeggero a circa 1.7 blocchi di altezza
        Location chairSpawnLoc = seatLoc.clone().subtract(0, 1.7, 0);

        ArmorStand chair = chairSpawnLoc.getWorld().spawn(chairSpawnLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setSmall(false);
            as.setMarker(false);
            as.setArms(false);
            as.setBasePlate(false);
            as.setCanTick(false);
            as.setPersistent(false);
            as.setCustomNameVisible(false);
        });

        chairs.put(player.getUniqueId(), chair);
        chair.addPassenger(player);

        boolean useActionbar = configManager.isSitUseActionbar();
        String msg = configManager.getPrefixedMessage("sit.sit-success");
        if (useActionbar) {
            MessageUtil.sendActionBar(player, configManager.getRawMessage("sit.sit-success"));
        } else {
            MessageUtil.sendMessage(player, msg);
        }
    }

    /**
     * Fa alzare il giocatore e rimuove la sedia temporanea.
     */
    public void dismount(Player player, boolean teleportBack) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        ArmorStand chair = chairs.remove(uuid);
        Location preLoc = preSitLocations.remove(uuid);

        // Rimuovi dai blocchi occupati
        occupiedBlocks.entrySet().removeIf(e -> e.getValue().equals(uuid));

        if (chair != null && chair.isValid()) {
            chair.eject();
            chair.remove();
        }

        if (teleportBack && preLoc != null && player.isOnline() && !player.isDead()) {
            // Teletrasporta il giocatore sopra il blocco o alla posizione iniziale per evitare di incastrarsi
            Location safeLoc = preLoc.clone().add(0, 0.1, 0);
            player.teleportAsync(safeLoc);
        }

        if (player.isOnline()) {
            boolean useActionbar = configManager.isSitUseActionbar();
            if (useActionbar) {
                MessageUtil.sendActionBar(player, configManager.getRawMessage("sit.stand-up"));
            } else {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sit.stand-up"));
            }
        }
    }

    /**
     * Pulisce una specifica sedia (es. chiamata dal listener di dismount).
     */
    public void handleDismountEvent(Player player, Entity vehicle) {
        if (player == null || vehicle == null) return;
        UUID uuid = player.getUniqueId();
        ArmorStand chair = chairs.get(uuid);

        if (chair != null && chair.equals(vehicle)) {
            chairs.remove(uuid);
            Location preLoc = preSitLocations.remove(uuid);
            occupiedBlocks.entrySet().removeIf(e -> e.getValue().equals(uuid));

            chair.remove();

            if (preLoc != null && player.isOnline() && !player.isDead()) {
                Location safeLoc = preLoc.clone().add(0, 0.1, 0);
                // Esegui sul tick successivo per non interrompere l'evento di dismount nativo
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && !player.isDead()) {
                        player.teleportAsync(safeLoc);
                    }
                });
            }

            boolean useActionbar = configManager.isSitUseActionbar();
            if (useActionbar) {
                MessageUtil.sendActionBar(player, configManager.getRawMessage("sit.stand-up"));
            } else {
                MessageUtil.sendMessage(player, configManager.getPrefixedMessage("sit.stand-up"));
            }
        }
    }

    /**
     * Chiamato quando un blocco viene distrutto: fa alzare l'eventuale giocatore seduto sopra.
     */
    public void onBlockBreak(Block block) {
        if (block == null) return;
        UUID occupierId = occupiedBlocks.remove(block.getLocation());
        if (occupierId != null) {
            Player player = Bukkit.getPlayer(occupierId);
            if (player != null && player.isOnline()) {
                dismount(player, true);
            }
        }
    }

    /**
     * Pulisce tutte le sedie attive (al reload o shutdown del plugin).
     */
    public void cleanup() {
        for (Map.Entry<UUID, ArmorStand> entry : chairs.entrySet()) {
            ArmorStand chair = entry.getValue();
            if (chair != null && chair.isValid()) {
                chair.eject();
                chair.remove();
            }
            Player p = Bukkit.getPlayer(entry.getKey());
            Location preLoc = preSitLocations.get(entry.getKey());
            if (p != null && p.isOnline() && preLoc != null) {
                p.teleport(preLoc);
            }
        }
        chairs.clear();
        preSitLocations.clear();
        occupiedBlocks.clear();
    }
}
