package it.caciotta.core.auth;

import io.papermc.paper.event.player.AsyncChatEvent;
import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

import java.util.Locale;

/**
 * Ascoltatore di eventi per impedire qualsiasi azione finche l'utente non ha effettuato il login/register.
 */
public class AuthListener implements Listener {

    private final AuthManager authManager;
    private final ConfigManager configManager;

    public AuthListener(CaciottaCore plugin) {
        this.authManager = plugin.getAuthManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        authManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        authManager.onPlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!authManager.isAuthenticated(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;

            // Permetti di girare la visuale (yaw/pitch), ma blocca lo spostamento delle coordinate
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location lock = from.clone();
                lock.setYaw(to.getYaw());
                lock.setPitch(to.getPitch());
                event.setTo(lock);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!authManager.isAuthenticated(player)) {
            event.setCancelled(true);
            sendReminder(player);
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!authManager.isAuthenticated(player)) {
            event.setCancelled(true);
            sendReminder(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (authManager.isAuthenticated(player)) {
            return;
        }

        String message = event.getMessage().trim().toLowerCase(Locale.ROOT);
        String[] parts = message.split("\\s+");
        String baseCommand = parts[0];

        // Permetti solo i comandi di login e register
        if (baseCommand.equals("/login") || baseCommand.equals("/l")
                || baseCommand.equals("/register") || baseCommand.equals("/reg")) {
            return;
        }

        event.setCancelled(true);
        sendReminder(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!authManager.isAuthenticated(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (!authManager.isAuthenticated(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDropItem(PlayerDropItemEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!authManager.isAuthenticated(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (!authManager.isAuthenticated(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (!authManager.isAuthenticated(player)) {
                event.setCancelled(true);
            }
        }
    }

    private void sendReminder(Player player) {
        if (authManager.isRegistered(player.getUniqueId())) {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.not-logged-in"));
        } else {
            MessageUtil.sendMessage(player, configManager.getPrefixedMessage("auth.must-register-first"));
        }
    }
}
