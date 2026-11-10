package it.caciotta.core.auth;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AuthLogoutTest {

    @Test
    public void testLogoutCommandInPluginYaml() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream, "plugin.yml deve essere presente nel jar");

        YamlConfiguration pluginYaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

        assertTrue(pluginYaml.contains("commands.logout"), "plugin.yml deve contenere il comando /logout");
        assertEquals("caciottacore.auth.logout", pluginYaml.getString("commands.logout.permission"));
        assertEquals("/logout", pluginYaml.getString("commands.logout.usage"));

        List<String> aliases = pluginYaml.getStringList("commands.logout.aliases");
        assertTrue(aliases.contains("esci"), "Gli alias di /logout devono contenere 'esci'");
        assertTrue(aliases.contains("sconnect"), "Gli alias di /logout devono contenere 'sconnect'");

        assertTrue(pluginYaml.contains("permissions.caciottacore.auth.logout"), "plugin.yml deve contenere la definizione del permesso caciottacore.auth.logout");
        assertEquals("true", pluginYaml.getString("permissions.caciottacore.auth.logout.default"));
        assertTrue(pluginYaml.getBoolean("permissions.caciottacore.auth.*.children.caciottacore.auth.logout"), "caciottacore.auth.* deve includere caciottacore.auth.logout");
    }

    @Test
    public void testLogoutMessagesInConfigYaml() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream, "config.yml deve essere presente nel jar");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

        assertTrue(config.contains("messages.auth.logout-success"), "config.yml deve contenere messages.auth.logout-success");
        String logoutSuccess = config.getString("messages.auth.logout-success");
        assertNotNull(logoutSuccess);
        assertFalse(logoutSuccess.isBlank());
    }
}

