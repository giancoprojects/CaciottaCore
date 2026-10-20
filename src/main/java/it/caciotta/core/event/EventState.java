package it.caciotta.core.event;

/**
 * Rappresenta lo stato attuale del ciclo di vita di un evento a premi.
 */
public enum EventState {
    SCHEDULED("In attesa", "&e"),
    RUNNING("In corso", "&a"),
    ENDED("Terminato", "&c");

    private final String displayName;
    private final String color;

    EventState(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getFormatted() {
        return color + displayName;
    }
}
