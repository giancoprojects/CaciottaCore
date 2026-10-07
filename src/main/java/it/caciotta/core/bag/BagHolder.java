package it.caciotta.core.bag;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Holder personalizzato per identificare in modo univoco l'inventario dello zaino (/bag)
 * e risalire all'UUID e nome del proprietario.
 */
public class BagHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private final String ownerName;
    private final int rows;
    private Inventory inventory;

    public BagHolder(UUID ownerUuid, String ownerName, int rows) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.rows = rows;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getRows() {
        return rows;
    }
}
