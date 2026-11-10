package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.teleport.data.TeleportData;

// The last teleport the server sent before removing the link, null if none.
public record VehicleClearAck(TeleportData lastTeleport) implements Acknowledgment {
}
