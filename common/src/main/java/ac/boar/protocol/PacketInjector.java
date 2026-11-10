package ac.boar.protocol;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

/**
 * Platform hook that replays a Bedrock packet inbound as if the client had just sent it.
 *
 * <p>Contract: the call consumes one reference to the packet, and the replay must skip Boar's
 * own listener chain (the check that cancelled the packet has already approved it). Other
 * server-side consumers of the inbound stream should still observe the packet, the same way
 * they would when {@link BoarHandlerAdaptor#injectClientPacket} fires it down the netty
 * pipeline past Boar's handler.
 */
@FunctionalInterface
public interface PacketInjector {
    void injectClientPacket(BedrockPacket packet);
}
