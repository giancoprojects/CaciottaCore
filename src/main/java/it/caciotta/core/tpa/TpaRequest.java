package it.caciotta.core.tpa;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Rappresenta una richiesta di teletrasporto attiva tra due giocatori.
 */
public class TpaRequest {

    private final UUID senderId;
    private final UUID targetId;
    private final long createdAt;
    private BukkitTask expiryTask;

    public TpaRequest(UUID senderId, UUID targetId, long createdAt) {
        this.senderId = senderId;
        this.targetId = targetId;
        this.createdAt = createdAt;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public BukkitTask getExpiryTask() {
        return expiryTask;
    }

    public void setExpiryTask(BukkitTask expiryTask) {
        this.expiryTask = expiryTask;
    }

    public void cancelTask() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }
}
