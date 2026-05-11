package ac.boar.protocol;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.ack.BatchAcknowledgmentTransport;
import ac.boar.anticheat.ack.BoarAcknowledgmentTransport;
import ac.boar.anticheat.ack.BoarBatchedAcknowledgmentTransport;
import ac.boar.anticheat.player.BoarPlayer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;

import java.util.List;

@RequiredArgsConstructor
public class BoarBatchAcknowledger extends ChannelOutboundHandlerAdapter {
    public static final String NAME = "boar-batch-acknowledger";

    private final BoarPlayer player;

    // Tracks whether any non-NSL BedrockPacket has been written to BedrockBatchEncoder's queue
    // since the last flush. Reset on every flush we emit. Used so that batches containing actual
    // game packets always end with an NSL — even when the pending ack bucket happens to be empty
    // at flush time (e.g. after multiple keepalives drained it within one onTick).
    private boolean bufferHasNonNsl = false;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof BedrockPacketWrapper wrapper && !(wrapper.getPacket() instanceof NetworkStackLatencyPacket)) {
            this.bufferHasNonNsl = true;
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (this.player.isClosed()) {
            super.flush(ctx);
            return;
        }

        final BoarAcknowledgmentTransport transport = this.player.getAckTransport();
        if (!(transport instanceof BoarBatchedAcknowledgmentTransport batched)) {
            super.flush(ctx);
            return;
        }

        final List<Acknowledgment> snapshot = batched.drainPending();
        final boolean needsClosingNsl = this.bufferHasNonNsl;

        if (snapshot.isEmpty() && !needsClosingNsl) {
            super.flush(ctx);
            return;
        }

        final long id = BatchAcknowledgmentTransport.nextId();
        if (snapshot.isEmpty()) {
            // No acks to dispatch, but the batch holds non-NSL packets — emit a bare NSL so the
            // batch terminates with one. Queue an empty Latency so the response is consumed cleanly.
            this.player.getLatencyUtil().queue(id, true);
        } else {
            this.player.getLatencyUtil().queueWithAcks(id, snapshot);
        }

        final NetworkStackLatencyPacket nsl = new NetworkStackLatencyPacket();
        nsl.setTimestamp(id);
        nsl.setFromServer(true);
        final BedrockPacketWrapper wrapper = BedrockPacketWrapper.create(0, 0, 0, nsl, null);
        ctx.write(wrapper, ctx.voidPromise());

        this.bufferHasNonNsl = false;
        super.flush(ctx);
    }
}
