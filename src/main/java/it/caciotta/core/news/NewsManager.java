package it.caciotta.core.news;

import it.caciotta.core.CaciottaCore;
import it.caciotta.core.config.ConfigManager;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestore del modulo News / Novità del server.
 * Costruisce e mostra il libro interattivo delle novità al login o tramite comando,
 * supportando la preferenza di notifica testuale in chat invece dell'apertura del libro.
 */
public class NewsManager {

    private final CaciottaCore plugin;
    private final ConfigManager configManager;
    private final File dataFile;

    // UUID dei giocatori che hanno scelto di ricevere solo la notifica in chat all'accesso
    private final Set<UUID> chatModeUsers = ConcurrentHashMap.newKeySet();

    public NewsManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.dataFile = new File(plugin.getDataFolder(), "data/news.yml");
        loadData();
    }

    /**
     * Verifica se sono presenti novità configurate e attive.
     */
    public boolean hasNews() {
        if (!configManager.isNewsEnabled()) {
            return false;
        }
        List<String> pages = configManager.getNewsPages();
        return pages != null && !pages.isEmpty();
    }

    /**
     * Costruisce il libro interattivo Kyori Adventure con titolo, autore, pagine
     * e il pulsante per attivare/disattivare l'apertura automatica all'accesso.
     */
    public Book buildBook(Player player) {
        Component title = MessageUtil.format(configManager.getNewsBookTitle());
        Component author = MessageUtil.format(configManager.getNewsBookAuthor());

        List<String> rawPages = configManager.getNewsPages();
        List<Component> pages = new ArrayList<>();

        if (rawPages != null) {
            for (String rawPage : rawPages) {
                pages.add(MessageUtil.format(rawPage));
            }
        }

        if (pages.isEmpty()) {
            pages.add(MessageUtil.format("&7Nessuna novita disponibile."));
        }

        // Aggiungi il pulsante di preferenza accesso all'ultima pagina
        int lastIndex = pages.size() - 1;
        Component lastPage = pages.get(lastIndex);

        boolean isChatMode = player != null && isChatNoticePreferred(player.getUniqueId());
        Component toggleBtn;

        if (isChatMode) {
            toggleBtn = Component.text("\n\n")
                    .append(MessageUtil.format(configManager.getRawMessage("news.book-button-show")))
                    .hoverEvent(HoverEvent.showText(MessageUtil.format(configManager.getRawMessage("news.book-button-show-hover"))))
                    .clickEvent(ClickEvent.runCommand("/news toggle"));
        } else {
            toggleBtn = Component.text("\n\n")
                    .append(MessageUtil.format(configManager.getRawMessage("news.book-button-hide")))
                    .hoverEvent(HoverEvent.showText(MessageUtil.format(configManager.getRawMessage("news.book-button-hide-hover"))))
                    .clickEvent(ClickEvent.runCommand("/news toggle"));
        }

        pages.set(lastIndex, lastPage.append(toggleBtn));

        return Book.book(title, author, pages);
    }

    /**
     * Apre il libro delle novità per il giocatore.
     */
    public boolean openNewsBook(Player player) {
        if (!hasNews()) {
            return false;
        }

        Book book = buildBook(player);
        player.openBook(book);

        if (configManager.isNewsPlaySound()) {
            try {
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            } catch (Exception ignored) {
            }
        }

        return true;
    }

    /**
     * Mostra il libro o la notifica chat al login/registrazione se abilitato e vi sono novità.
     */
    public void openNewsBookIfAvailable(Player player) {
        if (!configManager.isNewsOpenOnLogin() || !hasNews()) {
            return;
        }

        if (isChatNoticePreferred(player.getUniqueId())) {
            sendChatNotice(player);
        } else {
            openNewsBook(player);
        }
    }

    /**
     * Invia la notifica interattiva in chat con il link cliccabile per aprire le novità.
     */
    public void sendChatNotice(Player player) {
        String rawMsg = configManager.getRawMessage("news.chat-notice");
        String hoverText = configManager.getRawMessage("news.chat-notice-hover");

        Component component = MessageUtil.format(configManager.getPrefix() + rawMsg)
                .hoverEvent(HoverEvent.showText(MessageUtil.format(hoverText)))
                .clickEvent(ClickEvent.runCommand("/news"));

        player.sendMessage(component);

        if (configManager.isNewsPlaySound()) {
            try {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Alterna la preferenza del giocatore tra apertura automatica del libro e notifica chat.
     *
     * @return true se ora e in modalita chat, false se e tornato all'apertura del libro
     */
    public synchronized boolean toggleChatMode(UUID uuid) {
        boolean nowChatMode;
        if (chatModeUsers.contains(uuid)) {
            chatModeUsers.remove(uuid);
            nowChatMode = false;
        } else {
            chatModeUsers.add(uuid);
            nowChatMode = true;
        }
        saveData();
        return nowChatMode;
    }

    public boolean isChatNoticePreferred(UUID uuid) {
        return chatModeUsers.contains(uuid);
    }

    public synchronized void loadData() {
        chatModeUsers.clear();
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        List<String> list = config.getStringList("chat_mode_users");
        for (String idStr : list) {
            try {
                chatModeUsers.add(UUID.fromString(idStr));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void saveData() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }

            YamlConfiguration config = new YamlConfiguration();
            List<String> list = new ArrayList<>();
            for (UUID uuid : chatModeUsers) {
                list.add(uuid.toString());
            }
            config.set("chat_mode_users", list);
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare i dati delle preferenze news in " + dataFile.getName(), e);
        }
    }

    public synchronized void reload() {
        loadData();
        plugin.getLogger().info("NewsManager ricaricato con successo.");
    }

    public synchronized void cleanup() {
        saveData();
    }
}
