package ac.boar.anticheat.check.api.impl;

import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.player.BoarPlayer;

public class OffsetHandlerCheck extends Check {
    public OffsetHandlerCheck(BoarPlayer player) {
        super(player);
    }

    public void onPredictionComplete(float offset) {
    }
}
