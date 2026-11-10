package it.caciotta.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigUpdateTest {

    @Test
    public void testConfigUpdate() throws Exception {
        // Simula un vecchio config.yml con residuo di news e senza tpback/rtp/cestino
        String oldConfigContent = "prefix: \"&8[&6Old&8] \"\nhome:\n  teleport-delay: 5\nnews:\n  enabled: false\n";
        File tempFile = File.createTempFile("old_config", ".yml");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), oldConfigContent);

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(tempFile);
        InputStream defStream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(defStream, "Resource config.yml non trovata nel jar!");

        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));

        boolean updated = false;
        for (String key : defConfig.getKeys(true)) {
            if (!defConfig.isConfigurationSection(key)) {
                if (!currentConfig.contains(key)) {
                    currentConfig.set(key, defConfig.get(key));
                    updated = true;
                }
            }
            try {
                List<String> comments = defConfig.getComments(key);
                if (comments != null && !comments.isEmpty() && currentConfig.getComments(key).isEmpty()) {
                    currentConfig.setComments(key, comments);
                    updated = true;
                }
                List<String> inlineComments = defConfig.getInlineComments(key);
                if (inlineComments != null && !inlineComments.isEmpty() && currentConfig.getInlineComments(key).isEmpty()) {
                    currentConfig.setInlineComments(key, inlineComments);
                    updated = true;
                }
            } catch (Throwable ignored) {
            }
        }

        // Rimuovi tracce legacy di news (spostate nel file separato news.yml)
        if (currentConfig.contains("news")) {
            currentConfig.set("news", null);
            updated = true;
        }
        if (currentConfig.contains("messages.news")) {
            currentConfig.set("messages.news", null);
            updated = true;
        }

        assertTrue(updated, "Dovrebbe aver rilevato chiavi mancanti!");
        currentConfig.save(tempFile);

        String savedContent = Files.readString(tempFile.toPath());
        System.out.println("=== SAVED CONFIG CONTENT ===");
        System.out.println(savedContent);

        assertFalse(savedContent.contains("news:"), "Il file salvato NON deve contenere la sezione news (spostata in news.yml)");
        assertTrue(savedContent.contains("tpback:"), "Il file salvato deve contenere la sezione tpback");
        assertTrue(savedContent.contains("rtp:"), "Il file salvato deve contenere la sezione rtp");
        assertTrue(savedContent.contains("cestino:"), "Il file salvato deve contenere la sezione cestino");
        assertTrue(savedContent.contains("invsee:"), "Il file salvato deve contenere la sezione invsee");
        assertTrue(savedContent.contains("temporary-flight:"), "Il file salvato deve contenere temporary-flight");
        assertTrue(savedContent.contains("teleport-delay: 5"), "Il valore personalizzato dell'utente (5) deve essere preservato");
        assertTrue(savedContent.contains("Old"), "Il prefisso personalizzato dell'utente deve essere preservato");
    }
}
