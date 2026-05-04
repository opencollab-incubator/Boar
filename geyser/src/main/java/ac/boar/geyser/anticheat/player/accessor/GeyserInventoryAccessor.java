package ac.boar.geyser.anticheat.player.accessor;

import ac.boar.anticheat.player.accessor.InventoryAccessor;
import org.geysermc.geyser.session.GeyserSession;

public record GeyserInventoryAccessor(GeyserSession session) implements InventoryAccessor {

    @Override
    public void updateSlot(int slot) {
        this.session.getPlayerInventoryHolder().updateSlot(slot);
    }

    @Override
    public int heldItemSlot() {
        return this.session.getPlayerInventory().getHeldItemSlot();
    }
}
