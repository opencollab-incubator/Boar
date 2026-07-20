package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.teleport.data.TeleportData;

public record TeleportAcceptAck(TeleportData data) implements Acknowledgment {
}
