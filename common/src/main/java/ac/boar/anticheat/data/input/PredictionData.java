package ac.boar.anticheat.data.input;

import ac.boar.anticheat.util.math.Vec3;

public record PredictionData(Vec3 before, Vec3 after, Vec3 tickEnd) {
}
