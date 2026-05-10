package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.math.vector.Vector3i;

public record BlockUpdateAck(Vector3i position, int layer, int runtimeId) implements Acknowledgment {
}
