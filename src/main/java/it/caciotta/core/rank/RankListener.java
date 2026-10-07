package it.caciotta.core.rank;

import io.papermc.paper.event.player.AsyncChatEvent;
import it.caciotta.core.CaciottaCore;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Ascoltatore di eventi per il sistema di Rank:
 * - Applica permessi e aggiorna tablist al join
 * - Pulisce gli attachments al quit
 * - Formatta la chat con prefisso, colore del rank e supporto colori
 */
public class RankListener implements Listener {

    private final CaciottaCore plugin;
    private final RankManager rankManager;

    public RankListener(CaciottaCore plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        rankManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        rankManager.onPlayerQuit(event.getPlayer());
    }

    /**
     * Gestione della chat moderna Paper con supporto Kyori Adventure.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onModernChat(AsyncChatEvent event) {
        if (!rankManager.isChatFormattingEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (plugin.getAuthManager() != null && !plugin.getAuthManager().isAuthenticated(player)) {
            return;
        }

        Rank rank = rankManager.getPlayerRank(player);
        if (rank == null) {
            return;
        }

        // Estrai il messaggio testuale
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Se il giocatore ha il permesso, permette i codici colore '&'
        boolean canColor = player.hasPermission("caciottacore.chat.color") || player.isOp();
        String messageColor = rank.getChatColor();

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            String formatPattern = rankManager.getChatFormat();
            String prefix = rank.getPrefix();
            String suffix = rank.getSuffix();
            String rankName = rank.getDisplayName();

            String template = formatPattern
                    .replace("{prefix}", prefix)
                    .replace("{player}", player.getName())
                    .replace("{suffix}", suffix)
                    .replace("{rank}", rankName)
                    .replace("{chat_color}", messageColor);

            // Costruisci il componente del prefisso/nome
            int msgIndex = template.indexOf("{message}");
            if (msgIndex != -1) {
                String beforeMsg = template.substring(0, msgIndex);
                String afterMsg = template.substring(msgIndex + "{message}".length());

                Component beforeComp = MessageUtil.format(beforeMsg);
                Component bodyComp = canColor ? MessageUtil.format(messageColor + rawMessage) : Component.text(rawMessage);
                Component afterComp = afterMsg.isEmpty() ? Component.empty() : MessageUtil.format(afterMsg);

                return beforeComp.append(bodyComp).append(afterComp);
            }

            return MessageUtil.format(template.replace("{message}", rawMessage));
        });
    }

    /**
     * Fallback per la chat legacy (se un plugin legacy inoltra chat via AsyncPlayerChatEvent).
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!rankManager.isChatFormattingEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (plugin.getAuthManager() != null && !plugin.getAuthManager().isAuthenticated(player)) {
            return;
        }

        Rank rank = rankManager.getPlayerRank(player);
        if (rank == null) {
            return;
        }

        String format = rankManager.getChatFormat()
                .replace("{prefix}", rank.getPrefix())
                .replace("{player}", "%1$s")
                .replace("{suffix}", rank.getSuffix())
                .replace("{rank}", rank.getDisplayName())
                .replace("{chat_color}", rank.getChatColor())
                .replace("{message}", "%2$s");

        // Converti i codici colore '&' nel formato legacy
        event.setFormat(format.replace('&', '§'));
    }
}
