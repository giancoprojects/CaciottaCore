package it.caciotta.core.event;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rappresenta un evento a premi configurato e il suo stato di esecuzione in tempo reale.
 */
public class GameEvent {

    private final String id;
    private String displayName;
    private String description;
    private EventType type;
    private int target;
    private String startDateRaw;
    private ZonedDateTime startDate;
    private EventState state = EventState.SCHEDULED;

    // Partecipanti che hanno confermato la partecipazione
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> participantNames = new ConcurrentHashMap<>();

    // Progressi/Punteggi in tempo reale
    private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();

    // Vincitori della top 3 che hanno completato l'obbiettivo
    private final List<UUID> winners = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, Long> winnerTimes = new ConcurrentHashMap<>();

    public GameEvent(String id, String displayName, String description, EventType type, int target, String startDateRaw, ZonedDateTime startDate) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.displayName = displayName != null ? displayName : id;
        this.description = description != null ? description : "";
        this.type = type != null ? type : EventType.BLOCK_BREAK;
        this.target = Math.max(1, target);
        this.startDateRaw = startDateRaw;
        this.startDate = startDate;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = Math.max(1, target);
    }

    public String getStartDateRaw() {
        return startDateRaw;
    }

    public void setStartDateRaw(String startDateRaw) {
        this.startDateRaw = startDateRaw;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public EventState getState() {
        return state;
    }

    public void setState(EventState state) {
        this.state = state;
    }

    public boolean isRunning() {
        return state == EventState.RUNNING;
    }

    public boolean isScheduled() {
        return state == EventState.SCHEDULED;
    }

    public boolean isEnded() {
        return state == EventState.ENDED;
    }

    public boolean addParticipant(UUID uuid, String name) {
        if (uuid == null) return false;
        if (name != null) {
            participantNames.put(uuid, name);
        }
        return participants.add(uuid);
    }

    public boolean removeParticipant(UUID uuid) {
        if (uuid == null) return false;
        return participants.remove(uuid);
    }

    public boolean isParticipant(UUID uuid) {
        return uuid != null && participants.contains(uuid);
    }

    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    public String getParticipantName(UUID uuid) {
        return participantNames.getOrDefault(uuid, "Sconosciuto");
    }

    public int getScore(UUID uuid) {
        if (uuid == null) return 0;
        return scores.getOrDefault(uuid, 0);
    }

    public int addScore(UUID uuid, int amount) {
        if (uuid == null) return 0;
        return scores.compute(uuid, (id, current) -> (current == null ? 0 : current) + amount);
    }

    public void setScore(UUID uuid, int score) {
        if (uuid != null) {
            scores.put(uuid, Math.max(0, score));
        }
    }

    public Map<UUID, Integer> getScores() {
        return Collections.unmodifiableMap(scores);
    }

    public boolean isWinner(UUID uuid) {
        return winners.contains(uuid);
    }

    public synchronized boolean addWinner(UUID uuid) {
        if (uuid == null || winners.contains(uuid)) {
            return false;
        }
        winners.add(uuid);
        winnerTimes.put(uuid, System.currentTimeMillis());
        return true;
    }

    public List<UUID> getWinners() {
        synchronized (winners) {
            return new ArrayList<>(winners);
        }
    }

    public boolean isTop3Complete() {
        return winners.size() >= 3;
    }

    public void resetProgress() {
        scores.clear();
        winners.clear();
        winnerTimes.clear();
        participants.clear();
        participantNames.clear();
    }
}
