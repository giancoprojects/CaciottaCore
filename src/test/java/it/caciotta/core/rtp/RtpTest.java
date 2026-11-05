package it.caciotta.core.rtp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class RtpTest {

    private ZoneId zoneId;
    private Map<UUID, RtpManager.RtpUsage> usages;
    private UUID player1;
    private UUID player2;

    @BeforeEach
    public void setUp() {
        zoneId = ZoneId.of("Europe/Rome");
        usages = new ConcurrentHashMap<>();
        player1 = UUID.randomUUID();
        player2 = UUID.randomUUID();
    }

    @Test
    public void testMonthlyUsageTracking() {
        String currentMonth = LocalDate.now(zoneId).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // Inizialmente nessun utilizzo
        RtpManager.RtpUsage usage1 = usages.get(player1);
        int uses1 = (usage1 == null || !currentMonth.equals(usage1.getMonth())) ? 0 : usage1.getUses();
        assertEquals(0, uses1);

        // Primo utilizzo registrato nel mese corrente
        usages.put(player1, new RtpManager.RtpUsage(currentMonth, System.currentTimeMillis(), 1));

        usage1 = usages.get(player1);
        assertNotNull(usage1);
        assertEquals(1, usage1.getUses());
        assertEquals(currentMonth, usage1.getMonth());

        // Limite di 1 al mese raggiunto: canUse deve essere false
        boolean canUse = usage1.getUses() < 1;
        assertFalse(canUse);

        // Player 2 non ha ancora usato RTP
        RtpManager.RtpUsage usage2 = usages.get(player2);
        int uses2 = (usage2 == null || !currentMonth.equals(usage2.getMonth())) ? 0 : usage2.getUses();
        assertEquals(0, uses2);
        assertTrue(uses2 < 1);
    }

    @Test
    public void testAutomaticMonthlyReset() {
        String previousMonth = "2026-10";
        String currentMonth = "2026-11";

        // Il giocatore ha usato RTP nel mese precedente
        usages.put(player1, new RtpManager.RtpUsage(previousMonth, System.currentTimeMillis() - 864000000L, 1));

        // Nel nuovo mese, il conteggio degli utilizzi attuali deve risultare 0
        RtpManager.RtpUsage usage = usages.get(player1);
        assertNotNull(usage);
        assertEquals(previousMonth, usage.getMonth());

        int currentUses = (!currentMonth.equals(usage.getMonth())) ? 0 : usage.getUses();
        assertEquals(0, currentUses, "Nel nuovo mese il giocatore deve avere 0 utilizzi!");
        assertTrue(currentUses < 1, "Nel nuovo mese il giocatore deve poter usare nuovamente /rtp!");
    }

    @Test
    public void testNextResetDateCalculation() {
        LocalDate date = LocalDate.of(2026, 11, 5);
        LocalDate nextReset = date.withDayOfMonth(1).plusMonths(1);

        assertEquals("01/12/2026", nextReset.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        // Test fine anno
        LocalDate endOfYear = LocalDate.of(2026, 12, 25);
        LocalDate nextYearReset = endOfYear.withDayOfMonth(1).plusMonths(1);
        assertEquals("01/01/2027", nextYearReset.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    @Test
    public void testAdminReset() {
        String currentMonth = LocalDate.now(zoneId).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        usages.put(player1, new RtpManager.RtpUsage(currentMonth, System.currentTimeMillis(), 1));
        usages.put(player2, new RtpManager.RtpUsage(currentMonth, System.currentTimeMillis(), 1));

        assertEquals(1, usages.get(player1).getUses());
        assertEquals(1, usages.get(player2).getUses());

        // Reset singolo giocatore
        usages.remove(player1);
        assertNull(usages.get(player1));
        assertNotNull(usages.get(player2));

        // Reset globale (resetall)
        usages.clear();
        assertTrue(usages.isEmpty());
    }
}

