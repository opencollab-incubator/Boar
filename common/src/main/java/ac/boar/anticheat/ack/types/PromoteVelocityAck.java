package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;

/** No payload — promote the player's uncertain velocity into a certain one (or clear it). */
public record PromoteVelocityAck() implements Acknowledgment {
}
