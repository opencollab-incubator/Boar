package ac.boar.anticheat.violation;

import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.player.BoarPlayer;

public record Violation(BoarPlayer player, Check check, int vl, String verbose) {
}
