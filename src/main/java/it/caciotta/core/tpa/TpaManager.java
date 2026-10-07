package it.caciotta.core.tpa;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestore delle richieste di teletrasporto (TPA) tra giocatori.
 */
public class TpaManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;

    // targetUUID -> (senderUUID -> request)
    private final Map<UUID, Map<UUID, TpaRequest>> incomingRequests = new ConcurrentHashMap<>();
    // senderUUID -> targetUUID
    private final Map<UUID, UUID> outgoingRequests = new ConcurrentHashMap<>();
    // targetUUID -> latest senderUUID
    private final Map<UUID, UUID> latestIncoming = new ConcurrentHashMap<>();

    // senderUUID -> teletrasporto pendente
    private final Map<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Location> initialLocations = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingTeleportTargets = new ConcurrentHashMap<>();

    public TpaManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public void sendRequest(Player sender, Player target) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("tpa.cannot-tpa-self"));
            return;
        }

        if (!plugin.getAuthManager().isAuthenticated(target)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("tpa.player-not-authenticated",
                    Map.of("player", target.getName())));
            return;
        }

        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        Map<UUID, TpaRequest> targetIncoming = incomingRequests.computeIfAbsent(targetId, k -> new ConcurrentHashMap<>());
        if (targetIncoming.containsKey(senderId)) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("tpa.request-already-sent",
                    Map.of("player", target.getName())));
            return;
        }

        TpaRequest request = new TpaRequest(senderId, targetId, System.currentTimeMillis());
        targetIncoming.put(senderId, request);
        outgoingRequests.put(senderId, targetId);
        latestIncoming.put(targetId, senderId);

        int timeout = configManager.getTpaTimeoutSeconds();
        BukkitTask expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (targetIncoming.remove(senderId) != null) {
                outgoingRequests.remove(senderId);
                if (latestIncoming.get(targetId) != null && latestIncoming.get(targetId).equals(senderId)) {
                    latestIncoming.remove(targetId);
                }

                Player s = Bukkit.getPlayer(senderId);
                if (s != null && s.isOnline()) {
                    MessageUtil.sendMessage(s, configManager.getPrefixedMessage("tpa.request-expired-sender",
                            Map.of("player", target.getName())));
                }

                Player t = Bukkit.getPlayer(targetId);
                if (t != null && t.isOnline()) {
                    MessageUtil.sendMessage(t, configManager.getPrefixedMessage("tpa.request-expired-target",
                            Map.of("player", sender.getName())));
                }
            }
        }, timeout * 20L);

        request.setExpiryTask(expiry);

        // Notifica al mittente
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("tpa.request-sent",
                Map.of("player", target.getName(), "timeout", String.valueOf(timeout))));

        // Notifica al destinatario con pulsanti cliccabili
        MessageUtil.sendMessage(target, configManager.getPrefixedMessage("tpa.request-received",
                Map.of("player", sender.getName())));

        String info = configManager.getRawMessage("tpa.request-received-info");
        if (info != null && !info.isEmpty() && !info.startsWith("&cMessaggio non trovato")) {
            MessageUtil.sendMessage(target, configManager.getPrefixedMessage("tpa.request-received-info",
                    Map.of("player", sender.getName())));
        }

        Component acceptBtn = MessageUtil.format(configManager.getRawMessage("tpa.button-accept"))
                .hoverEvent(HoverEvent.showText(MessageUtil.format(configManager.getFormatted("tpa.button-accept-hover",
                        Map.of("player", sender.getName())))))
                .clickEvent(ClickEvent.runCommand("/tpaccept " + sender.getName()));

        Component denyBtn = MessageUtil.format(configManager.getRawMessage("tpa.button-deny"))
                .hoverEvent(HoverEvent.showText(MessageUtil.format(configManager.getFormatted("tpa.button-deny-hover",
                        Map.of("player", sender.getName())))))
                .clickEvent(ClickEvent.runCommand("/tpdeny " + sender.getName()));

        Component actions = Component.text("   ").append(acceptBtn).append(Component.text("   ")).append(denyBtn);
        target.sendMessage(actions);

        // Suono di notifica
        try {
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        } catch (Exception ignored) {
        }
    }

    public void acceptRequest(Player target, String senderName) {
        UUID targetId = target.getUniqueId();
        Map<UUID, TpaRequest> incoming = incomingRequests.get(targetId);
        if (incoming == null || incoming.isEmpty()) {
            MessageUtil.sendMessage(target, configManager.getPrefixedMessage("tpa.no-pending-requests"));
            return;
        }

        UUID senderId = null;
        if (senderName == null || senderName.isEmpty()) {
            senderId = latestIncoming.get(targetId);
            if (senderId == null || !incoming.containsKey(senderId)) {
                senderId = incoming.keySet().iterator().next();
            }
        } else {
            Player p = Bukkit.getPlayerExact(senderName);
            if (p != null) {
                senderId = p.getUniqueId();
            } else {
                for (UUID id : incoming.keySet()) {
                    Player candidate = Bukkit.getPlayer(id);
                    if (candidate != null && candidate.getName().equalsIgnoreCase(senderName)) {
                        senderId = id;
                        break;
                    }
                }
            }
        }

        if (senderId == null || !incoming.containsKey(senderId)) {
            MessageUtil.sendMessage(target, configManager.getPrefixedMessage("tpa.no-request-from-player",
                    Map.of("player", senderName != null ? senderName : "")));
            return;
        }

        TpaRequest req = incoming.remove(senderId);
        outgoingRequests.remove(senderId);
        latestIncoming.remove(targetId);
        if (req != null) {
            req.cancelTask();
        }

        Player sender = Bukkit.getPlayer(senderId);
        if (sender == null || !sender.isOnline()) {
            MessageUtil.sendMessage(target, configManager.getPrefixedMessage("tpa.player-not-found",
                    Map.of("player", senderName != null ? senderName : "giocatore")));
            return;
        }

        MessageUtil.sendMessage(target, configManager.getPrefixedMessage("tpa.request-accepted-target",
                Map.of("player", sender.getName())));
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("tpa.request-accepted-sender",
                Map.of("player", target.getName())));

        startTeleportCountdown(sender, target);
    }

    public void denyRequest(Player target, String senderName) {
        UUID targetId = target.getUniqueId();
        Map<UUID, TpaRequest> incoming = incomingRequests.get(targetId);
        if (incoming == null || incoming.isEmpty()) {
            MessageUtil.sendMessage(target, configManager.getPrefixedMessage("tpa.no-pending-requests"));
            return;
        }

        UUID senderId = null;
        if (senderName == null || senderName.isEmpty()) {
            senderId = latestIncoming.get(targetId);
            if (senderId == null || !incoming.containsKey(senderId)) {
                senderId = incoming.keySet().iterator().next();
            }
        } else {
            for (UUID id : incoming.keySet()) {
                Player candidate = Bukkit.getPlayer(id);
                if (candidate != null && candidate.getName().equalsIgnoreCase(senderName)) {
                    senderId = id;
                    break;
                }
            }
        }

        if (senderId == null || !incoming.containsKey(senderId)) {
            MessageUtil.sendMessage(target, configManager.getPrefixedMessage("tpa.no-request-from-player",
                    Map.of("player", senderName != null ? senderName : "")));
            return;
        }

        TpaRequest req = incoming.remove(senderId);
        outgoingRequests.remove(senderId);
        latestIncoming.remove(targetId);
        if (req != null) {
            req.cancelTask();
        }

        Player sender = Bukkit.getPlayer(senderId);
        MessageUtil.sendMessage(target, configManager.getPrefixedMessage("tpa.request-denied-target",
                Map.of("player", sender != null ? sender.getName() : "giocatore")));
        if (sender != null && sender.isOnline()) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("tpa.request-denied-sender",
                    Map.of("player", target.getName())));
        }
    }

    public void cancelOutgoing(Player sender) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = outgoingRequests.remove(senderId);
        if (targetId == null) {
            MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("tpa.no-request-to-cancel"));
            return;
        }

        Map<UUID, TpaRequest> targetIncoming = incomingRequests.get(targetId);
        if (targetIncoming != null) {
            TpaRequest req = targetIncoming.remove(senderId);
            if (req != null) {
                req.cancelTask();
            }
        }

        Player target = Bukkit.getPlayer(targetId);
        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("tpa.request-cancelled",
                Map.of("player", target != null ? target.getName() : "giocatore")));
    }

    private void startTeleportCountdown(Player sender, Player target) {
        UUID senderId = sender.getUniqueId();
        int delay = configManager.getTpaTeleportDelay();

        if (delay <= 0 || sender.hasPermission("caciottacore.tpa.bypasscooldown")) {
            executeTeleport(sender, target);
            return;
        }

        cancelPendingTeleport(senderId, false, null);

        initialLocations.put(senderId, sender.getLocation().clone());
        pendingTeleportTargets.put(senderId, target.getUniqueId());

        MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("tpa.teleport-countdown",
                Map.of("player", target.getName(), "seconds", String.valueOf(delay))));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTeleports.remove(senderId);
            initialLocations.remove(senderId);
            pendingTeleportTargets.remove(senderId);

            if (sender.isOnline() && target.isOnline()) {
                executeTeleport(sender, target);
            } else if (sender.isOnline()) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("tpa.player-not-found"));
            }
        }, delay * 20L);

        pendingTeleports.put(senderId, task);
    }

    private void executeTeleport(Player sender, Player target) {
        Location dest = target.getLocation();
        sender.teleportAsync(dest).thenAccept(success -> {
            if (success) {
                MessageUtil.sendMessage(sender, configManager.getPrefixedMessage("tpa.teleport-success",
                        Map.of("player", target.getName())));

                if (configManager.isTpaPlaySound()) {
                    try {
                        sender.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        target.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    } catch (Exception ignored) {
                    }
                }

                if (configManager.isTpaSpawnParticles()) {
                    try {
                        dest.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    public boolean hasPendingTeleport(UUID uuid) {
        return pendingTeleports.containsKey(uuid);
    }

    public Location getInitialLocation(UUID uuid) {
        return initialLocations.get(uuid);
    }

    public void cancelPendingTeleport(UUID senderId, boolean notify, String messageKey) {
        BukkitTask task = pendingTeleports.remove(senderId);
        initialLocations.remove(senderId);
        UUID targetId = pendingTeleportTargets.remove(senderId);
        if (task != null) {
            task.cancel();
            if (notify && messageKey != null) {
                Player sender = Bukkit.getPlayer(senderId);
                if (sender != null && sender.isOnline()) {
                    Player target = targetId != null ? Bukkit.getPlayer(targetId) : null;
                    MessageUtil.sendMessage(sender, configManager.getPrefixedMessage(messageKey,
                            Map.of("player", target != null ? target.getName() : "giocatore")));
                }
            }
        }
    }

    public List<String> getPendingSenderNames(UUID targetId) {
        Map<UUID, TpaRequest> map = incomingRequests.get(targetId);
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (UUID sId : map.keySet()) {
            Player p = Bukkit.getPlayer(sId);
            if (p != null) {
                list.add(p.getName());
            }
        }
        return list;
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        cancelPendingTeleport(uuid, false, null);

        // Se era un mittente, rimuovi la richiesta verso il target
        UUID targetId = outgoingRequests.remove(uuid);
        if (targetId != null) {
            Map<UUID, TpaRequest> map = incomingRequests.get(targetId);
            if (map != null) {
                TpaRequest req = map.remove(uuid);
                if (req != null) req.cancelTask();
            }
        }

        // Se era un target, rimuovi tutte le richieste in entrata
        Map<UUID, TpaRequest> incoming = incomingRequests.remove(uuid);
        if (incoming != null) {
            for (TpaRequest req : incoming.values()) {
                req.cancelTask();
                outgoingRequests.remove(req.getSenderId());
            }
        }
        latestIncoming.remove(uuid);
    }

    public void cleanup() {
        for (BukkitTask task : pendingTeleports.values()) {
            task.cancel();
        }
        pendingTeleports.clear();
        initialLocations.clear();
        pendingTeleportTargets.clear();

        for (Map<UUID, TpaRequest> map : incomingRequests.values()) {
            for (TpaRequest req : map.values()) {
                req.cancelTask();
            }
        }
        incomingRequests.clear();
        outgoingRequests.clear();
        latestIncoming.clear();
    }
}
