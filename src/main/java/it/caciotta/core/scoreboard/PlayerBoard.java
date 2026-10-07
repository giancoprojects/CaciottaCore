package it.caciotta.core.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import it.caciotta.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestisce la scoreboard laterale per un singolo giocatore.
 * Utilizza l'API moderna di Paper per aggiornamenti flicker-free e supporto NumberFormat.
 */
public class PlayerBoard {

    private static final int MAX_LINES = 15;
    private static final String[] ENTRIES = new String[MAX_LINES];

    static {
        for (int i = 0; i < MAX_LINES; i++) {
            ENTRIES[i] = "§" + Integer.toHexString(i) + "§r";
        }
    }

    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<String> lastLines = new ArrayList<>();
    private String lastTitle = "";
    private int previousCount = 0;

    public PlayerBoard(Player player, String initialTitle, boolean showNumbers) {
        this.player = player;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("caciotta_sb", Criteria.DUMMY, MessageUtil.format(initialTitle != null ? initialTitle : ""));
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        if (!showNumbers) {
            this.objective.numberFormat(NumberFormat.blank());
        }

        this.lastTitle = initialTitle != null ? initialTitle : "";
        player.setScoreboard(this.scoreboard);
    }

    /**
     * Aggiorna il titolo se è cambiato rispetto al precedente.
     */
    public void updateTitle(String newTitle) {
        if (newTitle == null || newTitle.equals(lastTitle)) {
            return;
        }
        this.lastTitle = newTitle;
        this.objective.displayName(MessageUtil.format(newTitle));
    }

    /**
     * Aggiorna le righe della scoreboard con gestione dinamica e flicker-free.
     */
    public void updateLines(List<String> newLines, boolean showNumbers) {
        if (newLines == null) {
            newLines = List.of();
        }

        int count = Math.min(newLines.size(), MAX_LINES);

        // Se il numero di righe si è ridotto, rimuovi i punteggi in eccesso
        if (previousCount > count) {
            for (int i = count; i < previousCount; i++) {
                scoreboard.resetScores(ENTRIES[i]);
            }
        }

        // Aggiorna ciascuna riga
        boolean countChanged = (count != previousCount);
        for (int i = 0; i < count; i++) {
            String line = newLines.get(i);
            int scoreValue = count - i;

            boolean lineChanged = i >= lastLines.size() || !line.equals(lastLines.get(i));

            if (lineChanged || countChanged) {
                Score score = objective.getScore(ENTRIES[i]);
                score.setScore(scoreValue);
                Component comp = (line.isEmpty() || line.isBlank()) ? Component.text(" ") : MessageUtil.format(line);
                score.customName(comp);

                if (!showNumbers) {
                    score.numberFormat(NumberFormat.blank());
                }
            }
        }

        // Salva lo stato attuale
        lastLines.clear();
        for (int i = 0; i < count; i++) {
            lastLines.add(newLines.get(i));
        }
        this.previousCount = count;
    }

    /**
     * Rimuove la scoreboard e ripristina quella principale di default.
     */
    public void destroy() {
        try {
            objective.unregister();
        } catch (Exception ignored) {
        }

        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public Player getPlayer() {
        return player;
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }
}
