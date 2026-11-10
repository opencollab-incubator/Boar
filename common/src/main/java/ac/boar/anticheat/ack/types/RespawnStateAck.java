package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;

/**
 * The client received a RespawnPacket for itself. SERVER_SEARCHING marks the client as dead and
 * SERVER_READY marks it as alive again.
 */
public record RespawnStateAck(RespawnPacket.State state) implements Acknowledgment {
}
