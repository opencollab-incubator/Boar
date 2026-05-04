package ac.boar.anticheat.player.accessor;

/**
 * Accessor for accessing entity data from the server.
 */
public interface InventoryAccessor {

    void updateSlot(int slot);

    int heldItemSlot();
}
