package ac.boar.anticheat.check.api.impl;

import ac.boar.anticheat.check.api.Check;

public interface OffsetHandlerCheck extends Check {

    default void onPredictionComplete(float offset) {
    }
}
