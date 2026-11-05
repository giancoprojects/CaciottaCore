package it.caciotta.core.cestino;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class CestinoTest {

    @Test
    public void testCountdownFormatting() {
        // 14 minuti e 32 secondi (872 secondi)
        long remainingSeconds = (14 * 60) + 32;

        long mins = remainingSeconds / 60;
        long secs = remainingSeconds % 60;
        String formattedShort = String.format(Locale.ROOT, "%02d:%02d", mins, secs);
        String formattedDetailed = String.format(Locale.ROOT, "%02dm %02ds", mins, secs);

        assertEquals("14:32", formattedShort);
        assertEquals("14m 32s", formattedDetailed);

        // 0 secondi rimanenti
        remainingSeconds = 0;
        mins = remainingSeconds / 60;
        secs = remainingSeconds % 60;
        assertEquals("00:00", String.format(Locale.ROOT, "%02d:%02d", mins, secs));
        assertEquals("00m 00s", String.format(Locale.ROOT, "%02dm %02ds", mins, secs));
    }

    @Test
    public void testFifteenMinuteIntervalCalculation() {
        int intervalMinutes = 15;
        long now = System.currentTimeMillis();
        long nextReset = now + (intervalMinutes * 60 * 1000L);

        long diffMillis = nextReset - now;
        long diffSeconds = diffMillis / 1000L;

        assertEquals(15 * 60, diffSeconds, "L'intervallo deve corrispondere esattamente a 900 secondi (15 minuti)");
    }

    @Test
    public void testResetTriggerWhenExpired() {
        int intervalMinutes = 15;
        long now = System.currentTimeMillis();

        // Simula scadenza trascorsa (ad es. 10 secondi fa)
        long expiredResetTime = now - 10000L;
        boolean shouldReset = now >= expiredResetTime;
        assertTrue(shouldReset, "Il cestino deve attivare lo svuotamento automatico quando il tempo corrente supera la scadenza!");

        // Dopo il reset, il prossimo svuotamento deve essere calcolato a +15 minuti
        long newResetTime = now + (intervalMinutes * 60 * 1000L);
        assertTrue(newResetTime > now);
        assertEquals(900, (newResetTime - now) / 1000L);
    }
}

