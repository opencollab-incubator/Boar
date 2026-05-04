package ac.boar.anticheat.util.block.specific;

import ac.boar.anticheat.data.inventory.BoarItemStack;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.mappings.item.Items;

public class PowderSnowBlock {
    public static boolean canEntityWalkOnPowderSnow(final BoarPlayer player) {
        return BoarItemStack.of(player.getSession(), player.compensatedInventory.armorContainer.get(3).getData()).is(Items.LEATHER_BOOTS);
    }
}
