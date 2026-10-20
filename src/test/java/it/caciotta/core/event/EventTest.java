package it.caciotta.core.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class EventTest {

    private GameEvent event;
    private UUID player1;
    private UUID player2;
    private UUID player3;
    private UUID player4;

    @BeforeEach
    public void setUp() {
        player1 = UUID.randomUUID();
        player2 = UUID.randomUUID();
        player3 = UUID.randomUUID();
        player4 = UUID.randomUUID();

        event = new GameEvent(
                "scavatore",
                "&6&lEvento Scavatore",
                "Scava 10.000 blocchi per vincere",
                EventType.BLOCK_BREAK,
                10000,
                "25/10/2026 21:00",
                ZonedDateTime.now().plusDays(1)
        );
    }

    @Test
    public void testEventStateAndParticipants() {
        assertEquals(EventState.SCHEDULED, event.getState());
        assertTrue(event.isScheduled());
        assertFalse(event.isRunning());

        // Aggiungi partecipanti (conferma partecipazione)
        assertTrue(event.addParticipant(player1, "Steve"));
        assertTrue(event.addParticipant(player2, "Alex"));

        // Non può aggiungere di nuovo lo stesso partecipante
        assertFalse(event.addParticipant(player1, "Steve"));

        assertTrue(event.isParticipant(player1));
        assertTrue(event.isParticipant(player2));
        assertFalse(event.isParticipant(player3)); // Non ha confermato la partecipazione!

        assertEquals("Steve", event.getParticipantName(player1));
        assertEquals("Alex", event.getParticipantName(player2));

        // Ritiro / abbandono
        assertTrue(event.removeParticipant(player2));
        assertFalse(event.isParticipant(player2));
    }

    @Test
    public void testEventScoresAndLeaderboard() {
        event.setState(EventState.RUNNING);
        assertTrue(event.isRunning());

        event.addParticipant(player1, "Steve");
        event.addParticipant(player2, "Alex");
        event.addParticipant(player3, "Miner99");

        // Simula progressi scavo blocchi
        event.addScore(player1, 500);
        event.addScore(player2, 1200);
        event.addScore(player3, 300);

        assertEquals(500, event.getScore(player1));
        assertEquals(1200, event.getScore(player2));
        assertEquals(300, event.getScore(player3));

        // Incremento successivo
        event.addScore(player1, 1000); // 1500
        assertEquals(1500, event.getScore(player1));

        // Ordinamento classifica
        List<Map.Entry<UUID, Integer>> entries = new java.util.ArrayList<>(event.getScores().entrySet());
        entries.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));

        assertEquals(player1, entries.get(0).getKey()); // 1500
        assertEquals(player2, entries.get(1).getKey()); // 1200
        assertEquals(player3, entries.get(2).getKey()); // 300
    }

    @Test
    public void testTop3WinnersAndCompletion() {
        event.setState(EventState.RUNNING);
        event.addParticipant(player1, "Steve");
        event.addParticipant(player2, "Alex");
        event.addParticipant(player3, "Miner99");
        event.addParticipant(player4, "Noob");

        assertFalse(event.isTop3Complete());

        // Player 1 raggiunge 10.000 blocchi -> 1° Posto
        event.setScore(player1, 10000);
        assertTrue(event.addWinner(player1));
        assertEquals(1, event.getWinners().size());
        assertEquals(player1, event.getWinners().get(0));
        assertFalse(event.isTop3Complete());

        // Player 3 raggiunge 10.000 blocchi -> 2° Posto
        event.setScore(player3, 10000);
        assertTrue(event.addWinner(player3));
        assertEquals(2, event.getWinners().size());
        assertEquals(player3, event.getWinners().get(1));
        assertFalse(event.isTop3Complete());

        // Player 2 raggiunge 10.000 blocchi -> 3° Posto
        event.setScore(player2, 10000);
        assertTrue(event.addWinner(player2));
        assertEquals(3, event.getWinners().size());
        assertEquals(player2, event.getWinners().get(2));

        // Completamento Top 3 raggiunto! L'evento termina!
        assertTrue(event.isTop3Complete());
    }

    @Test
    public void testResetProgress() {
        event.addParticipant(player1, "Steve");
        event.addParticipant(player2, "Alex");
        event.addScore(player1, 500);
        event.addScore(player2, 10000);
        event.addWinner(player2);

        assertEquals(2, event.getParticipants().size());
        assertEquals(2, event.getScores().size());
        assertEquals(1, event.getWinners().size());

        // Esegue il reset completo
        event.resetProgress();
        event.setState(EventState.SCHEDULED);

        assertEquals(0, event.getParticipants().size());
        assertEquals(0, event.getScores().size());
        assertEquals(0, event.getWinners().size());
        assertEquals(0, event.getScore(player1));
        assertEquals(0, event.getScore(player2));
        assertFalse(event.isParticipant(player1));
        assertFalse(event.isParticipant(player2));
        assertEquals(EventState.SCHEDULED, event.getState());
    }

    @Test
    public void testHotUpdateEventParametersAndDate() {
        assertEquals("25/10/2026 21:00", event.getStartDateRaw());
        assertEquals(10000, event.getTarget());
        assertEquals("&6&lEvento Scavatore", event.getDisplayName());

        // Simula la modifica dei campi da events.yml e reload
        String newDateRaw = "30/10/2026 18:30";
        ZonedDateTime newDate = ZonedDateTime.parse("2026-10-30T18:30:00+01:00[Europe/Rome]");
        event.setStartDateRaw(newDateRaw);
        event.setStartDate(newDate);
        event.setTarget(15000);
        event.setDisplayName("&6&lSuper Scavatore");

        assertEquals("30/10/2026 18:30", event.getStartDateRaw());
        assertEquals(newDate, event.getStartDate());
        assertEquals(15000, event.getTarget());
        assertEquals("&6&lSuper Scavatore", event.getDisplayName());

        // Simula evento terminato che viene riprogrammato nel futuro
        event.setState(EventState.ENDED);
        assertTrue(event.isEnded());

        if (event.getStartDate().isAfter(ZonedDateTime.now())) {
            event.setState(EventState.SCHEDULED);
        }
        assertTrue(event.isScheduled());
    }
}
