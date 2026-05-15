package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.util.math.Vec3;

public record VelocityAck(Vec3 motion) implements Acknowledgment {
}
