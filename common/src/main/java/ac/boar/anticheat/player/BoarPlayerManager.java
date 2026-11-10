package ac.boar.anticheat.player;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.BoarAcknowledgmentTransport;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.player.accessor.EntityAccessor;
import ac.boar.anticheat.player.accessor.InventoryAccessor;
import ac.boar.anticheat.player.accessor.WorldAccessor;
import ac.boar.anticheat.player.data.BlockMappingInfo;
import ac.boar.api.anticheat.model.NetworkSession;
import ac.boar.mappings.entity.Entity;
import ac.boar.protocol.BoarBatchAcknowledger;
import ac.boar.protocol.BoarConnection;
import ac.boar.protocol.BoarHandlerAdaptor;
import io.netty.channel.Channel;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

public abstract class BoarPlayerManager<T> extends ConcurrentHashMap<T, BoarPlayer> {

    public BoarPlayer add(T session) {
        NetworkSession networkSession = this.createNetworkSession(session);
        BoarConnection connection = this.getConnection(session);

        BoarPlayer player = new BoarPlayer(
                networkSession,
                connection,
                this.getPlayerEntity(session),
                this.getMappingInfo(session),
                this.createWorldAccessor(session),
                this.createEntityAccessor(session),
                this.createInventoryAccessor(session),
                this.createDefaultAttributes(),
                this.shouldDisableMitigations(session)
        );

        player.setAckTransport(this.createAckTransport(player));

        this.installHandlers(player, connection.getChannel());

        this.put(session, player);
        player.future = this.beginTicking(session, player::serverTick);
        return player;
    }

    /**
     * Installs the handlers that feed packets into Boar. The default inserts the bidirectional
     * packet capture and the batch-tail acknowledgment injector directly into the bedrock channel
     * pipeline, just after the {@link BedrockPacketCodec}.
     *
     * <p>A platform that already exposes its own packet-event stream can override this to bridge
     * that stream into {@link ac.boar.protocol.PacketEvents} instead, so it doesn't run a second
     * netty codec on the same channel.
     */
    protected void installHandlers(BoarPlayer player, Channel channel) {
        BoarHandlerAdaptor handlerAdaptor = new BoarHandlerAdaptor(player, (BedrockPacketCodec) channel.pipeline().get(BedrockPacketCodec.NAME));
        channel.pipeline().addAfter(BedrockPacketCodec.NAME, BoarHandlerAdaptor.NAME, handlerAdaptor);
        player.setPacketInjector(handlerAdaptor);
        // Sits between BedrockPacketCodec and BoarHandlerAdaptor in pipeline order — outbound
        // traversal hits us after BoarHandlerAdaptor, so flush() can inject an NSL into the same
        // batch as whatever else is being flushed.
        channel.pipeline().addAfter(BedrockPacketCodec.NAME, BoarBatchAcknowledger.NAME, new BoarBatchAcknowledger(player));
    }

    /**
     * Decides whether the player being added starts with mitigations suppressed — checks still run and
     * violations are still reported, but Boar won't cancel packets, correct blocks, rewind movement or
     * kick. The default reads the global {@code disable-mitigations} config value, so every player gets
     * the same arm.
     *
     * <p>A platform that wants to split players (A/B testing corrections, per-rank exemptions, a staged
     * rollout) can override this to decide per session. The value is only a starting point —
     * {@link BoarPlayer#setDisableMitigations(boolean)} can move a player between arms afterwards.
     */
    protected boolean shouldDisableMitigations(T session) {
        return Boar.getConfig().disableMitigations();
    }

    protected abstract NetworkSession createNetworkSession(T session);

    protected abstract BoarConnection getConnection(T session);

    protected abstract Entity getPlayerEntity(T session);

    protected abstract BlockMappingInfo getMappingInfo(T session);

    protected abstract WorldAccessor createWorldAccessor(T session);

    protected abstract EntityAccessor createEntityAccessor(T session);

    protected abstract InventoryAccessor createInventoryAccessor(T session);

    protected abstract Map<String, AttributeInstance> createDefaultAttributes();

    protected abstract BoarAcknowledgmentTransport createAckTransport(BoarPlayer player);

    protected abstract ScheduledFuture<?> beginTicking(T session, Runnable ticker);
}
