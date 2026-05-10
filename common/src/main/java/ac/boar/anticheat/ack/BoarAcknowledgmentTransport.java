package ac.boar.anticheat.ack;

/**
 * Emits and tracks acknowledgment pings (round-trip markers) to the client. Boar uses these to
 * gate server-state updates on client confirmation — chunk loads, block changes, dimension
 * switches, etc.
 *
 * <p>The send side has three operations: {@link #keepalive()} for heartbeats,
 * {@link #send(Acknowledgment)} to emit a fresh ping with an attached acknowledgment, and
 * {@link #attach(Acknowledgment)} to attach an acknowledgment to whatever ping is most recently
 * in flight (saving bandwidth when one is already pending).
 *
 * <p>The wire-observed lifecycle goes through {@link #onPingSent(long, boolean)} and
 * {@link #onPingReceived(long)} — these are called by the netty bridge whenever a
 * NetworkStackLatency packet is observed on the channel.
 *
 * <p>Implementations decide <em>when</em> the underlying network packet is actually emitted — the
 * default sends one packet per call; alternatives may batch (e.g. flush every 10ms), coalesce, or
 * piggyback on a different mechanism the underlying server/proxy provides. They also fully own
 * acknowledgment dispatch on the receive side, so a custom implementation can use its own queue
 * structures without involving Boar's default {@code LatencyUtil}.
 */
public interface BoarAcknowledgmentTransport {

    /**
     * Emit a keepalive ping with no acknowledgment to dispatch. Used to detect timeouts when no
     * server-state change is pending.
     */
    void keepalive();

    /**
     * Emit a fresh ping and bind the acknowledgment to it. The acknowledgment's handler runs when
     * the client confirms this ping.
     */
    void send(Acknowledgment ack);

    /**
     * Bind the acknowledgment to the most recent in-flight ping. If none is pending, dispatch the
     * acknowledgment immediately.
     */
    void attach(Acknowledgment ack);

    /**
     * Notify the transport that an outbound NetworkStackLatency packet was observed on the wire.
     * The {@code ours} flag is true if the ping originated from Boar (vs. forwarded from
     * upstream).
     */
    void onPingSent(long id, boolean ours);

    /**
     * Notify the transport that an inbound NetworkStackLatency response was observed on the wire.
     * The transport dispatches any associated acknowledgments and updates its own bookkeeping.
     * Returns true to cancel the underlying packet event (i.e. the ping was Boar's own and should
     * not be forwarded upstream).
     */
    boolean onPingReceived(long id);
}
