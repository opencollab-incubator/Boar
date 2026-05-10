package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.util.math.Vec3;

public record EntityInterpolateAck(long runtimeEntityId, Vec3 position, boolean lerp) implements Acknowledgment {
}
