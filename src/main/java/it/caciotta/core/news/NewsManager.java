package it.caciotta.core.news;

import it.caciotta.core.CaciottaCore;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestore del modulo News / Novità del server.
 * Gestisce la configurazione indipendente da news.yml e la persistenza
 * delle preferenze utente (apertura automatica o notifica chat) in data/news_data.yml.
 */
public class NewsManager {

    private final CaciottaCore plugin;
    private final File newsConfigFile;
    private final File dataFile;
    private YamlConfiguration newsConfig;

    // UUID dei giocatori che hanno scelto di ricevere solo la notifica in chat all'accesso
    private final Set<UUID> chatModeUsers = ConcurrentHashMap.newKeySet();

    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            Map.entry("no-news", "&cAl momento non sono presenti novita o aggiornamenti recenti del server."),
            Map.entry("reloaded", "&aConfigurazione del libro delle novita ricaricata con successo!"),
            Map.entry("usage-admin", "&cUso: &e/news &7(oppure &e/news toggle&7, &e/news reload&7)"),
            Map.entry("toggled-to-chat", "&eD'ora in poi all'accesso riceverai una notifica in chat con il link cliccabile anziche aprire il libro."),
            Map.entry("toggled-to-book", "&aD'ora in poi il libro delle novita si aprira automaticamente a ogni tuo accesso."),
            Map.entry("chat-notice", "&7Ci sono novita recenti sul server! &e&l[CLICCA QUI]&7 per aprirle."),
            Map.entry("chat-notice-hover", "&eClicca per aprire il libro delle novita!"),
            Map.entry("book-button-hide", "&c&l[Non mostrare all'accesso]"),
            Map.entry("book-button-hide-hover", "&7Clicca per ricevere solo un avviso in chat con link all'accesso."),
            Map.entry("book-button-show", "&a&l[Riattiva apertura all'accesso]"),
            Map.entry("book-button-show-hover", "&7Clicca per riattivare l'apertura automatica del libro all'accesso.")
    );

    public NewsManager(CaciottaCore plugin) {
        this.plugin = plugin;
        this.newsConfigFile = new File(plugin.getDataFolder(), "news.yml");

        // Migrazione sicura da data/news.yml legacy a data/news_data.yml
        File oldDataFile = new File(plugin.getDataFolder(), "data/news.yml");
        File targetDataFile = new File(plugin.getDataFolder(), "data/news_data.yml");
        if (oldDataFile.exists() && !targetDataFile.exists()) {
            try {
                if (!targetDataFile.getParentFile().exists()) {
                    targetDataFile.getParentFile().mkdirs();
                }
                boolean renamed = oldDataFile.renameTo(targetDataFile);
                if (renamed) {
                    plugin.getLogger().info("Migrato archivio preferenze news in data/news_data.yml");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Impossibile rinominare il vecchio data/news.yml: " + e.getMessage());
            }
        }
        this.dataFile = targetDataFile.exists() ? targetDataFile : (oldDataFile.exists() ? oldDataFile : targetDataFile);

        loadConfig();
        loadData();
    }

    /**
     * Aggiorna automaticamente news.yml inserendo sezioni, parametri o commenti mancanti.
     */
    public void updateConfigOnDisk() {
        try {
            if (!newsConfigFile.exists()) {
                plugin.saveResource("news.yml", false);
                return;
            }

            InputStream defStream = plugin.getResource("news.yml");
            if (defStream == null) {
                return;
            }

            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(newsConfigFile);

            boolean modified = false;
            for (String key : defConfig.getKeys(true)) {
                if (!defConfig.isConfigurationSection(key)) {
                    if (!currentConfig.contains(key)) {
                        currentConfig.set(key, defConfig.get(key));
                        modified = true;
                    }
                }
                try {
                    List<String> comments = defConfig.getComments(key);
                    if (comments != null && !comments.isEmpty() && currentConfig.getComments(key).isEmpty()) {
                        currentConfig.setComments(key, comments);
                        modified = true;
                    }
                    List<String> inlineComments = defConfig.getInlineComments(key);
                    if (inlineComments != null && !inlineComments.isEmpty() && currentConfig.getInlineComments(key).isEmpty()) {
                        currentConfig.setInlineComments(key, inlineComments);
                        modified = true;
                    }
                } catch (Throwable ignored) {
                }
            }

            if (modified) {
                currentConfig.save(newsConfigFile);
                plugin.getLogger().info("Aggiornato automaticamente news.yml con i nuovi parametri e le sezioni mancanti!");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Impossibile aggiornare automaticamente news.yml: " + e.getMessage(), e);
        }
    }

    /**
     * Carica o ricarica la configurazione del libro delle novità da news.yml.
     */
    public synchronized void loadConfig() {
        updateConfigOnDisk();
        this.newsConfig = YamlConfiguration.loadConfiguration(newsConfigFile);
    }

    /**
     * Recupera un messaggio grezzo da news.yml con fallback di sicurezza.
     */
    public String getRawMessage(String key) {
        if (key == null) return "";
        String normalizedKey = key.startsWith("news.") ? key.substring(5) : key;
        String val = null;
        if (newsConfig != null) {
            val = newsConfig.getString("messages." + normalizedKey);
            if (val == null) {
                val = newsConfig.getString(normalizedKey);
            }
        }
        if (val == null) {
            val = DEFAULT_MESSAGES.get(normalizedKey);
        }
        return val != null ? val : "";
    }

    /**
     * Recupera un messaggio formattato con il prefisso del plugin.
     */
    public String getPrefixedMessage(String key) {
        return getPrefixedMessage(key, Collections.emptyMap());
    }

    /**
     * Recupera un messaggio formattato con il prefisso del plugin e sostituisce i placeholder.
     */
    public String getPrefixedMessage(String key, Map<String, String> placeholders) {
        String msg = getRawMessage(key);
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return plugin.getConfigManager().getPrefix() + msg;
    }

    public boolean isNewsEnabled() {
        if (newsConfig == null) return true;
        return newsConfig.getBoolean("enabled", newsConfig.getBoolean("news.enabled", true));
    }

    public boolean isNewsOpenOnLogin() {
        if (newsConfig == null) return true;
        return newsConfig.getBoolean("open-on-login", newsConfig.getBoolean("news.open-on-login", true));
    }

    public String getNewsBookTitle() {
        if (newsConfig == null) return "&6Novita Server";
        return newsConfig.getString("book-title", newsConfig.getString("news.book-title", "&6Novita Server"));
    }

    public String getNewsBookAuthor() {
        if (newsConfig == null) return "&eCaciottaCore";
        return newsConfig.getString("book-author", newsConfig.getString("news.book-author", "&eCaciottaCore"));
    }

    public boolean isNewsPlaySound() {
        if (newsConfig == null) return true;
        return newsConfig.getBoolean("play-sound", newsConfig.getBoolean("news.play-sound", true));
    }

    public List<String> getNewsPages() {
        if (newsConfig == null) return Collections.emptyList();
        List<String> pages = newsConfig.getStringList("pages");
        if (pages.isEmpty()) {
            pages = newsConfig.getStringList("news.pages");
        }
        return pages;
    }

    /**
     * Verifica se sono presenti novità configurate e attive.
     */
    public boolean hasNews() {
        if (!isNewsEnabled()) {
            return false;
        }
        List<String> pages = getNewsPages();
        return pages != null && !pages.isEmpty();
    }

    /**
     * Costruisce il libro interattivo Kyori Adventure con titolo, autore, pagine
     * e il pulsante per attivare/disattivare l'apertura automatica all'accesso.
     */
    public Book buildBook(Player player) {
        Component title = MessageUtil.format(getNewsBookTitle());
        Component author = MessageUtil.format(getNewsBookAuthor());

        List<String> rawPages = getNewsPages();
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
                    .append(MessageUtil.format(getRawMessage("book-button-show")))
                    .hoverEvent(HoverEvent.showText(MessageUtil.format(getRawMessage("book-button-show-hover"))))
                    .clickEvent(ClickEvent.runCommand("/news toggle"));
        } else {
            toggleBtn = Component.text("\n\n")
                    .append(MessageUtil.format(getRawMessage("book-button-hide")))
                    .hoverEvent(HoverEvent.showText(MessageUtil.format(getRawMessage("book-button-hide-hover"))))
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

        if (isNewsPlaySound()) {
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
        if (!isNewsOpenOnLogin() || !hasNews()) {
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
        String rawMsg = getRawMessage("chat-notice");
        String hoverText = getRawMessage("chat-notice-hover");

        Component component = MessageUtil.format(plugin.getConfigManager().getPrefix() + rawMsg)
                .hoverEvent(HoverEvent.showText(MessageUtil.format(hoverText)))
                .clickEvent(ClickEvent.runCommand("/news"));

        player.sendMessage(component);

        if (isNewsPlaySound()) {
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
        loadConfig();
        loadData();
        plugin.getLogger().info("NewsManager ricaricato con successo (news.yml).");
    }

    public synchronized void cleanup() {
        saveData();
    }
}
