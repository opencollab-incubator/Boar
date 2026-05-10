package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;

public record EntityClearPastAck(long runtimeEntityId) implements Acknowledgment {
}
