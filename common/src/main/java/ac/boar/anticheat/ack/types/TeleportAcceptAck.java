package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.teleport.data.TeleportCache;

public record TeleportAcceptAck(TeleportCache cache) implements Acknowledgment {
}
