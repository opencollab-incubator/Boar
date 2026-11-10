package ac.boar.anticheat.check.impl.inventory;

import ac.boar.api.anticheat.annotations.CheckInfo;
import ac.boar.anticheat.check.api.BaseCheck;
import ac.boar.anticheat.player.BoarPlayer;

@CheckInfo(name = "Inventory")
public final class Inventory extends BaseCheck {
    public Inventory(BoarPlayer player) {
        super(player);
    }
}
