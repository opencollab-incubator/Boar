package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;

// A rider joining or leaving a vehicle, for the passenger order the client keeps.
public record PassengerLinkAck(long vehicleRuntimeId, long riderUniqueId, boolean riderIsPlayer, boolean remove, float angularVelocity) implements Acknowledgment {
}
