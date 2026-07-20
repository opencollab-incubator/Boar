package ac.boar.anticheat.teleport.data;

import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.util.math.Vec3;

public record RewindHistory(long tick, Vec3 position, PredictionData data) {
}
