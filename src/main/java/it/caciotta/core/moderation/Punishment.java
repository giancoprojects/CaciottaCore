package it.caciotta.core.moderation;

import java.util.UUID;

/**
 * Rappresenta una sanzione (Ban o Mute) inflitta a un giocatore.
 */
public class Punishment {

    private final UUID targetId;
    private final String targetName;
    private final String staffName;
    private final PunishmentType type;
    private final String reason;
    private final long createdAt;
    private final long expiresAt; // -1 per sanzione permanente

    public Punishment(UUID targetId, String targetName, String staffName, PunishmentType type, String reason, long createdAt, long expiresAt) {
        this.targetId = targetId;
        this.targetName = targetName;
        this.staffName = staffName != null ? staffName : "Console";
        this.type = type;
        this.reason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Nessun motivo specificato";
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getStaffName() {
        return staffName;
    }

    public PunishmentType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isPermanent() {
        return expiresAt <= 0;
    }

    public boolean isExpired() {
        if (isPermanent()) {
            return false;
        }
        return System.currentTimeMillis() >= expiresAt;
    }

    public long getRemainingMillis() {
        if (isPermanent()) {
            return -1;
        }
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public String getDurationFormatted() {
        if (isPermanent()) {
            return "Permanente";
        }
        return TimeUtil.formatDuration(expiresAt - createdAt);
    }

    public String getRemainingFormatted() {
        if (isPermanent()) {
            return "Permanente";
        }
        return TimeUtil.formatDuration(getRemainingMillis());
    }
}
