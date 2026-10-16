package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;

// A vehicle motion packet for the follower history, with the time it was sent.
public record FollowerMotionAck(long sentNs, boolean onGround, EntityCache entity) implements Acknowledgment {
}
