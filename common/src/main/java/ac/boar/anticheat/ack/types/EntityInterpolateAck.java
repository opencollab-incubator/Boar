package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.util.math.Vec3;

public record EntityInterpolateAck(long runtimeEntityId, Float posX, Float posY, Float posZ, boolean lerp) implements Acknowledgment {
}
