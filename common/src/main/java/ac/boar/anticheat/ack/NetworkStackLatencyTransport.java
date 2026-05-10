package ac.boar.anticheat.ack;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.LatencyUtil;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Default {@link BoarAcknowledgmentTransport} implementation. Each call emits one
 * {@link NetworkStackLatencyPacket} immediately through the bedrock session and binds the
 * acknowledgment to {@link LatencyUtil}'s sent queue.
 *
 * <p>The {@code fromServer = false} flag is the convention Boar uses to distinguish its own
 * pings from any that Geyser also sends; the netty bridge flips it back to {@code true} after
 * observing it.
 *
 * <p>Custom transports can subclass this to reuse {@link #emit()} and override only the parts
 * they care about — for example a batching transport that buffers in {@link #send(Acknowledgment)}
 * and {@link #attach(Acknowledgment)} and emits a single ping at flush time.
 */
@RequiredArgsConstructor
public class NetworkStackLatencyTransport implements BoarAcknowledgmentTransport {
    protected final BoarPlayer player;

    @Override
    public void keepalive() {
        emit();
    }

    @Override
    public void send(Acknowledgment ack) {
        emit();
        attach(ack);
    }

    @Override
    public void attach(Acknowledgment ack) {
        this.player.getLatencyUtil().queue(ack);
    }

    @Override
    public void onPingSent(long id, boolean ours) {
        this.player.getLatencyUtil().queue(id, ours);
    }

    @Override
    public boolean onPingReceived(long id) {
        final LatencyUtil latencyUtil = this.player.getLatencyUtil();
        final LatencyUtil.Latency poll = latencyUtil.sentQueue().poll();

        if (poll == null || poll.id() != id) {
            this.player.kick("Invalid latency id, expected=" + (poll == null ? "none" : poll.id()) + ", actual=" + id);
            return true;
        }

        poll.dispatch(this.player);
        latencyUtil.onLatencyAccepted(poll);
        latencyUtil.prevAcceptedTime = System.currentTimeMillis();
        latencyUtil.prevAcceptedLatency = poll;

        return poll.ours();
    }

    /**
     * Build and dispatch one NetworkStackLatency packet on the bedrock session. Subclasses can
     * call this from a flush method or override it to use a different ack mechanism entirely.
     */
    protected void emit() {
        long id = ThreadLocalRandom.current().nextLong(-5000000L, 5000000L);

        final NetworkStackLatencyPacket latencyPacket = new NetworkStackLatencyPacket();
        latencyPacket.setTimestamp(id);
        latencyPacket.setFromServer(false);

        if (!this.player.isClosed()) {
            this.player.getBedrockSession().sendPacketImmediately(latencyPacket);
        }
    }
}
