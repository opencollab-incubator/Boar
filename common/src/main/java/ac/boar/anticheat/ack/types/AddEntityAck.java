package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;

public record AddEntityAck(EntityCache entity) implements Acknowledgment {
}
