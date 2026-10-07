package it.caciotta.core.moderation;

import io.papermc.paper.event.player.AsyncChatEvent;
import it.caciotta.core.CaciottaCore;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Ascoltatore per il modulo di Moderazione:
 * - Blocca l'accesso ai giocatori bannati al momento del pre-login
 * - Blocca i messaggi in chat e comandi privati ai giocatori mutati
 */
public class ModerationListener implements Listener {

    private final ModerationManager moderationManager;

    private static final Set<String> BLOCKED_MUTE_COMMANDS = Set.of(
            "/msg", "/tell", "/whisper", "/w", "/r", "/reply", "/me"
    );

    public ModerationListener(CaciottaCore plugin) {
        this.moderationManager = plugin.getModerationManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        Punishment ban = moderationManager.getActiveBan(uuid);
        if (ban != null) {
            if (ban.isExpired()) {
                moderationManager.removeBan(uuid);
                return;
            }

            Component screen = MessageUtil.format(moderationManager.formatBanScreen(ban));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, screen);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onModernChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Punishment mute = moderationManager.getActiveMute(player.getUniqueId());
        if (mute != null) {
            if (mute.isExpired()) {
                moderationManager.removeMute(player.getUniqueId());
                return;
            }

            event.setCancelled(true);
            MessageUtil.sendMessage(player, moderationManager.formatMuteBlocked(mute));
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Punishment mute = moderationManager.getActiveMute(player.getUniqueId());
        if (mute != null) {
            if (mute.isExpired()) {
                moderationManager.removeMute(player.getUniqueId());
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Punishment mute = moderationManager.getActiveMute(player.getUniqueId());
        if (mute == null) {
            return;
        }

        if (mute.isExpired()) {
            moderationManager.removeMute(player.getUniqueId());
            return;
        }

        String msg = event.getMessage().trim().toLowerCase(Locale.ROOT);
        String[] parts = msg.split("\\s+");
        if (parts.length > 0 && BLOCKED_MUTE_COMMANDS.contains(parts[0])) {
            event.setCancelled(true);
            MessageUtil.sendMessage(player, moderationManager.formatMuteBlocked(mute));
        }
    }
}
