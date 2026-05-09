package ac.boar.anticheat.check.api.impl;

import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.util.LatencyUtil;

public interface PingBasedCheck extends Check {

    default void onLatencyAccepted(LatencyUtil.Latency latency) {
    }
}
