package it.caciotta.core.invsee;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Holder personalizzato per l'inventario del comando /invsee.
 * Mantiene i riferimenti al giocatore bersaglio e alla modalità di visualizzazione.
 */
public class InvseeHolder implements InventoryHolder {

    private final UUID targetUuid;
    private final String targetName;
    private final boolean readOnly;
    private Inventory inventory;

    public InvseeHolder(Player target, boolean readOnly) {
        this.targetUuid = target.getUniqueId();
        this.targetName = target.getName();
        this.readOnly = readOnly;
    }

    public InvseeHolder(UUID targetUuid, String targetName, boolean readOnly) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.readOnly = readOnly;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

