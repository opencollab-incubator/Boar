package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;

public record HotbarSlotAck(int slot) implements Acknowledgment {
}
