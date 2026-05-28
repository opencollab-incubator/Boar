package ac.boar.anticheat.ack;

import ac.boar.anticheat.player.BoarPlayer;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
public class BatchAcknowledgmentTransport implements BoarBatchedAcknowledgmentTransport {
    protected final BoarPlayer player;
    private final PendingAckBucket bucket = new PendingAckBucket();

    @Override
    public void markPending(Acknowledgment ack) {
        this.bucket.add(ack);
    }

    @Override
    public List<Acknowledgment> drainPending() {
        return this.bucket.drain();
    }

    @Override
    public boolean hasPending() {
        return !this.bucket.isEmpty();
    }

    @Override
    public void keepalive() {
        if (this.player.isClosed()) {
            return;
        }

        // Drain any pending acks first via a flush — BoarBatchAcknowledger.flush emits NSL_batch
        // at the end of whatever's currently buffered. Without this pre-flush, the inline
        // writeAndFlush below would land the keepalive NSL in BedrockBatchEncoder's queue *before*
        // BoarBatchAcknowledger.flush appends NSL_batch, leaving the keepalive mid-batch.
        this.player.getBedrockSession().getPeer().getChannel().flush();

        // fromServer=false marks this NSL as Boar's own for the NetworkLatencyPackets listener observation - the listener flips it to true before it leaves the wire.
        final NetworkStackLatencyPacket packet = new NetworkStackLatencyPacket();
        packet.setTimestamp(nextId());
        packet.setFromServer(false);
        emitImmediate(packet);
    }

    @Override
    public void send(Acknowledgment ack) {
        markPending(ack);
    }

    @Override
    public void attach(Acknowledgment ack) {
        markPending(ack);
    }

    @Override
    public void onPingSent(long id, boolean ours) {
        this.player.getLatencyUtil().queue(id, ours);
    }

    @Override
    public boolean onPingReceived(long id) {
        // Let the latency util handle this so that out-of-order NSL responses can still be handled.
        return this.player.getLatencyUtil().onResponse(id);
    }

    public static long nextId() {
        return ThreadLocalRandom.current().nextLong(-5000000L, 5000000L);
    }

    protected void emitImmediate(NetworkStackLatencyPacket packet) {
        if (!this.player.isClosed()) {
            this.player.getBedrockSession().sendPacketImmediately(packet);
        }
    }
}
