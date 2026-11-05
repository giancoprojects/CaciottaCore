package it.caciotta.core.news;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NewsConfigTest {

    @Test
    public void testNewsConfigResourceExistsAndValid() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("news.yml");
        assertNotNull(stream, "Il file news.yml deve esistere come risorsa nel jar!");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertTrue(config.getBoolean("enabled", false), "News deve essere abilitato di default");
        assertTrue(config.getBoolean("open-on-login", false), "open-on-login deve essere true di default");
        assertEquals("&6Novita Server", config.getString("book-title"), "Il titolo del libro predefinito non corrisponde");
        assertEquals("&eCaciottaCore", config.getString("book-author"), "L'autore del libro predefinito non corrisponde");
        assertTrue(config.getBoolean("play-sound", false), "play-sound deve essere true di default");

        List<String> pages = config.getStringList("pages");
        assertNotNull(pages, "La lista delle pagine non deve essere nulla");
        assertFalse(pages.isEmpty(), "La lista delle pagine predefinite non deve essere vuota");

        // Verifica messaggi di default
        assertNotNull(config.getString("messages.no-news"), "Messaggio no-news mancante in news.yml");
        assertNotNull(config.getString("messages.reloaded"), "Messaggio reloaded mancante in news.yml");
        assertNotNull(config.getString("messages.toggled-to-chat"), "Messaggio toggled-to-chat mancante in news.yml");
        assertNotNull(config.getString("messages.toggled-to-book"), "Messaggio toggled-to-book mancante in news.yml");
        assertNotNull(config.getString("messages.chat-notice"), "Messaggio chat-notice mancante in news.yml");
    }

    @Test
    public void testNewsConfigAutoUpdate() throws Exception {
        // Simula un file news.yml parziale su disco creato dall'utente
        String userNewsContent = "enabled: true\nbook-title: \"&bMio Server\"\npages:\n  - \"&aBenvenuto!\"\n";
        File tempFile = File.createTempFile("test_news", ".yml");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), userNewsContent);

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(tempFile);
        InputStream defStream = getClass().getClassLoader().getResourceAsStream("news.yml");
        assertNotNull(defStream);
        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));

        boolean modified = false;
        for (String key : defConfig.getKeys(true)) {
            if (!defConfig.isConfigurationSection(key)) {
                if (!currentConfig.contains(key)) {
                    currentConfig.set(key, defConfig.get(key));
                    modified = true;
                }
            }
        }

        assertTrue(modified, "Dovrebbe rilevare parametri mancanti come i messaggi");
        currentConfig.save(tempFile);

        String updatedContent = Files.readString(tempFile.toPath());
        // Preserva le modifiche dell'utente
        assertTrue(updatedContent.contains("&bMio Server"), "Il titolo personalizzato deve essere preservato");
        // Aggiunge i messaggi mancanti
        assertTrue(updatedContent.contains("no-news:"), "Il file deve contenere la sezione messaggi aggiunta");
    }
}

