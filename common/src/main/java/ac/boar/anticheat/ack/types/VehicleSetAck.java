package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;

public record VehicleSetAck(long vehicleRuntimeId, long vehicleUniqueId, long sentTick, long sentNs) implements Acknowledgment {
}
