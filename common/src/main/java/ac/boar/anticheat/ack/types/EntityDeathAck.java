package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;

/** ActorEvent DEATH. Tied to the actor instance we saw when the packet was sent. */
public record EntityDeathAck(EntityCache entity) implements Acknowledgment {
}
