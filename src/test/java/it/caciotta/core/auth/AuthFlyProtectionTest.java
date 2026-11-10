package it.caciotta.core.auth;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class AuthFlyProtectionTest {

    @Test
    public void testAuthFlyConfigKeys() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream, "config.yml deve essere presente nel jar");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

        assertTrue(config.contains("auth.temporary-flight"), "config.yml deve contenere auth.temporary-flight");
        assertTrue(config.getBoolean("auth.temporary-flight"), "temporary-flight deve essere true di default");

        assertTrue(config.contains("auth.fall-protection-seconds"), "config.yml deve contenere auth.fall-protection-seconds");
        assertEquals(5, config.getInt("auth.fall-protection-seconds"), "fall-protection-seconds deve essere 5 di default");
    }
}

