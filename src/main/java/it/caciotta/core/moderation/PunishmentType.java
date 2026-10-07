package it.caciotta.core.moderation;

/**
 * Tipologie di sanzione supportate dal modulo di moderazione.
 */
public enum PunishmentType {
    BAN("Ban Permanente"),
    TEMPBAN("Ban Temporaneo"),
    MUTE("Mute Permanente"),
    TEMPMUTE("Mute Temporaneo"),
    KICK("Espulsione");

    private final String displayName;

    PunishmentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
