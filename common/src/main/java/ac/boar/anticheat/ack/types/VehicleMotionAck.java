package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.util.math.Vec3;

// A server motion (SetActorMotion, or the motion in AddActor) for this one actor. A respawned actor with the same id gets its own.
public record VehicleMotionAck(EntityCache entity, Vec3 motion) implements Acknowledgment {
}
