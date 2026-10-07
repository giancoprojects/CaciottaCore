package it.caciotta.core.tpback;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Informazioni relative all'ultima morte di un giocatore.
 */
public class DeathInfo {

    private final UUID playerId;
    private final Location deathLocation;
    private final long deathTimestamp;
    private boolean used;

    public DeathInfo(UUID playerId, Location deathLocation, long deathTimestamp) {
        this.playerId = playerId;
        this.deathLocation = deathLocation;
        this.deathTimestamp = deathTimestamp;
        this.used = false;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Location getDeathLocation() {
        return deathLocation;
    }

    public long getDeathTimestamp() {
        return deathTimestamp;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }
}
