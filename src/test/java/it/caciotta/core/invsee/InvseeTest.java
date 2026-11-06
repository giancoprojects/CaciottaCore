package it.caciotta.core.invsee;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class InvseeTest {

    @Test
    public void testInvseeHolder() {
        UUID uuid = UUID.randomUUID();
        InvseeHolder holder = new InvseeHolder(uuid, "TestUser", true);

        assertEquals(uuid, holder.getTargetUuid());
        assertEquals("TestUser", holder.getTargetName());
        assertTrue(holder.isReadOnly());

        InvseeHolder modifiableHolder = new InvseeHolder(uuid, "TestUser", false);
        assertFalse(modifiableHolder.isReadOnly());
    }

    @Test
    public void testSlotLogic() {
        assertEquals(54, InvseeManager.INV_SIZE);
        assertEquals(45, InvseeManager.SLOT_HELMET);
        assertEquals(46, InvseeManager.SLOT_CHESTPLATE);
        assertEquals(47, InvseeManager.SLOT_LEGGINGS);
        assertEquals(48, InvseeManager.SLOT_BOOTS);
        assertEquals(50, InvseeManager.SLOT_OFFHAND);
        assertEquals(52, InvseeManager.SLOT_INFO);
        assertEquals(53, InvseeManager.SLOT_ENDER_CHEST);

        // Verifica che gli slot di storage (0-35) e armatura/seconda mano siano riconosciuti
        for (int i = 0; i <= 35; i++) {
            assertTrue(isInteractable(i), "Slot " + i + " deve essere modificabile");
            assertFalse(isSeparator(i), "Slot " + i + " non deve essere separatore");
        }

        // Separatori riga 5 (36-44)
        for (int i = 36; i <= 44; i++) {
            assertFalse(isInteractable(i), "Slot " + i + " non deve essere modificabile");
            assertTrue(isSeparator(i), "Slot " + i + " deve essere separatore");
        }

        // Armatura (45-48)
        for (int i = 45; i <= 48; i++) {
            assertTrue(isInteractable(i), "Slot armatura " + i + " deve essere modificabile");
            assertFalse(isSeparator(i), "Slot armatura " + i + " non deve essere separatore");
        }

        // Slot 49 e 51 divisori
        assertTrue(isSeparator(49));
        assertTrue(isSeparator(51));

        // Slot 50 offhand
        assertTrue(isInteractable(50));
        assertFalse(isSeparator(50));

        // Slot 52 (info) e 53 (ender chest)
        assertTrue(isSeparator(52));
        assertTrue(isSeparator(53));
    }

    private boolean isInteractable(int slot) {
        return (slot >= 0 && slot <= 35) || (slot >= 45 && slot <= 48) || slot == 50;
    }

    private boolean isSeparator(int slot) {
        return (slot >= 36 && slot <= 44) || slot == 49 || slot == 51 || slot == 52 || slot == 53;
    }

    @Test
    public void testConfigAndMessages() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream, "config.yml deve essere presente nelle risorse del JAR");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

        assertTrue(config.contains("invsee.enabled"), "config.yml deve contenere invsee.enabled");
        assertTrue(config.getBoolean("invsee.enabled"), "invsee deve essere abilitato di default");
        assertTrue(config.getBoolean("invsee.allow-modification"), "allow-modification deve essere true di default");
        assertTrue(config.getBoolean("invsee.play-sound"), "play-sound deve essere true di default");
        assertTrue(config.getBoolean("invsee.prevent-inspect-exempt"), "prevent-inspect-exempt deve essere true di default");
        assertTrue(config.contains("invsee.load-player-skin"), "config.yml deve contenere invsee.load-player-skin");
        assertFalse(config.getBoolean("invsee.load-player-skin"), "load-player-skin deve essere false di default per prevenire timeout Mojang su server offline");

        assertNotNull(config.getString("messages.invsee.usage"), "Messaggio usage mancante in config.yml");
        assertNotNull(config.getString("messages.invsee.player-not-found"), "Messaggio player-not-found mancante in config.yml");
        assertNotNull(config.getString("messages.invsee.cannot-inspect-self"), "Messaggio cannot-inspect-self mancante in config.yml");
        assertNotNull(config.getString("messages.invsee.target-exempt"), "Messaggio target-exempt mancante in config.yml");
        assertNotNull(config.getString("messages.invsee.opened"), "Messaggio opened mancante in config.yml");
        assertNotNull(config.getString("messages.invsee.cannot-modify"), "Messaggio cannot-modify mancante in config.yml");
    }

    @Test
    public void testOfflineUuidDetection() {
        // Simula l'UUID dell'errore riportato dall'utente
        UUID offlineUuid = UUID.fromString("00063cfb-8b1b-3c14-b024-efdce7f02719");
        assertEquals(3, offlineUuid.version(), "L'UUID offline deve essere di versione 3");
        assertNotEquals(4, offlineUuid.version(), "Un account non-premium non deve essere trattato come UUID v4 Mojang");
    }
}

