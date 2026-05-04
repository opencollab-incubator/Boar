package ac.boar.anticheat.teleport.data.rewind;

import ac.boar.anticheat.data.input.PredictionData;
import org.cloudburstmc.math.vector.Vector3f;

public record RewindData(long tick, Vector3f position, PredictionData data) {
}
