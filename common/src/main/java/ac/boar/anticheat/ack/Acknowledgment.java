package ac.boar.anticheat.ack;

/**
 * Marker for an acknowledgment payload — typed data describing what should happen when the
 * client confirms a server-side change. The corresponding {@link AcknowledgmentHandler}
 * registered on {@link BoarAcknowledgmentRegistry} performs the actual work.
 *
 * <p>Implementations are plain data carriers and should not contain logic.
 */
public interface Acknowledgment {
}
