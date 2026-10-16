package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.teleport.data.TeleportData;

public record EntityRemoveAck(long uniqueEntityId, EntityCache entity, TeleportData lastTeleport) implements Acknowledgment {
}
