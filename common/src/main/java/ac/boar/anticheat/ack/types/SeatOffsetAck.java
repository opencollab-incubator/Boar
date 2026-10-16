package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.math.vector.Vector3f;

public record SeatOffsetAck(Vector3f seatOffset) implements Acknowledgment {
}
