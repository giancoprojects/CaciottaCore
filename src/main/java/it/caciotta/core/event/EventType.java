package it.caciotta.core.event;

/**
 * Tipi di obbiettivo supportati per gli eventi a premi di CaciottaCore.
 */
public enum EventType {
    BLOCK_BREAK("Scavo Blocchi"),
    MOB_KILL("Uccisione Mostri"),
    FISH_CATCH("Pesca"),
    BLOCK_PLACE("Posizionamento Blocchi"),
    PLAYER_KILL("Uccisioni PvP");

    private final String displayName;

    EventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
