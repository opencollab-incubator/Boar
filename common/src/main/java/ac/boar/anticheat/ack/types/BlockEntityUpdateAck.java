package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;

public record BlockEntityUpdateAck(Vector3i position, NbtMap data) implements Acknowledgment {
}
