package it.caciotta.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Utility per la formattazione dei messaggi con supporto Adventure e colori legacy (&).
 */
public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private MessageUtil() {
    }

    /**
     * Converte una stringa contenente codici colore (& o §) o tag MiniMessage in un Component.
     */
    public static Component format(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        String text = message.replace('§', '&');
        // Se contiene codici legacy '&', usiamo il deserializzatore legacy con supporto hex
        if (text.contains("&")) {
            return LEGACY.deserialize(text);
        }
        // Altrimenti proviamo MiniMessage per tag come <gradient:...>, <green>, ecc.
        try {
            return MINI_MESSAGE.deserialize(text);
        } catch (Exception ignored) {
            return Component.text(text);
        }
    }

    /**
     * Invia un messaggio formattato al sender.
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(format(message));
    }

    /**
     * Invia un'actionbar al giocatore.
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }
        player.sendActionBar(format(message));
    }

    /**
     * Invia un titolo e un sottotitolo al giocatore.
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (player == null) {
            return;
        }
        Component titleComp = title != null ? format(title) : Component.empty();
        Component subComp = subtitle != null ? format(subtitle) : Component.empty();
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeInTicks * 50L),
                Duration.ofMillis(stayTicks * 50L),
                Duration.ofMillis(fadeOutTicks * 50L)
        );
        player.showTitle(Title.title(titleComp, subComp, times));
    }

    /**
     * Rimuove immediatamente il titolo e resetta i tempi.
     */
    public static void clearTitle(Player player) {
        if (player == null) {
            return;
        }
        player.clearTitle();
        player.resetTitle();
    }
}
