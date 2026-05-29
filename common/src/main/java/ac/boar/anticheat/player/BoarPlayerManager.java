package ac.boar.anticheat.player;

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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

public abstract class BoarPlayerManager<T> extends HashMap<T, BoarPlayer> {

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
                this.createDefaultAttributes()
        );

        player.setAckTransport(this.createAckTransport(player));

        Channel channel = connection.getChannel();
        channel.pipeline().addAfter(BedrockPacketCodec.NAME, BoarHandlerAdaptor.NAME, new BoarHandlerAdaptor(player, (BedrockPacketCodec) channel.pipeline().get(BedrockPacketCodec.NAME)));
        // Sits between BedrockPacketCodec and BoarHandlerAdaptor in pipeline order — outbound
        // traversal hits us after BoarHandlerAdaptor, so flush() can inject an NSL into the same
        // batch as whatever else is being flushed.
        channel.pipeline().addAfter(BedrockPacketCodec.NAME, BoarBatchAcknowledger.NAME, new BoarBatchAcknowledger(player));

        this.put(session, player);
        player.future = this.beginTicking(session, player::serverTick);
        return player;
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
