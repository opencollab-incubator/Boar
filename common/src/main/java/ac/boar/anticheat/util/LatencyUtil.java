package ac.boar.anticheat.util;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.ack.BoarAcknowledgmentRegistry;
import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.check.api.impl.PingBasedCheck;
import ac.boar.anticheat.player.BoarPlayer;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@RequiredArgsConstructor
public final class LatencyUtil {
    private final BoarPlayer player;
    private final Deque<Latency> sentQueue = new ConcurrentLinkedDeque<>();
    public Latency prevAcceptedLatency;
    public long prevAcceptedTime = System.currentTimeMillis();

    public Deque<Latency> sentQueue() {
        return this.sentQueue;
    }

    public void queue(long id, boolean ours) {
        this.sentQueue.add(new Latency(id, System.currentTimeMillis(), System.nanoTime(), ours, ours ? new ArrayList<>() : null));
        Boar.debug("[ack-queue] queued id=" + id + " ours=" + ours + " acks=[] sentQueue=" + this.sentQueue.size(), Boar.DebugMessage.INFO);
    }

    /**
     * Append a Latency that already has acknowledgments attached. Used by the batch acknowledger,
     * which has drained a snapshot of pending acks and assigned them an id atomically before the
     * NSL packet hits the wire.
     */
    public void queueWithAcks(long id, List<Acknowledgment> acks) {
        this.sentQueue.add(new Latency(id, System.currentTimeMillis(), System.nanoTime(), true, new ArrayList<>(acks)));
        Boar.debug("[ack-queue] queued id=" + id + " ours=true acks=" + ackNames(acks) + " sentQueue=" + this.sentQueue.size(), Boar.DebugMessage.INFO);
    }

    /**
     * Attach an acknowledgment to whatever Latency is currently most-recently-in-flight, or
     * dispatch inline if none is pending.
     *
     * <p>The "attach to last in-flight" semantic is preserved for legacy callers, but new code
     * should go through {@code BoarPlayer.queueAcknowledgment} which routes to the pending bucket
     * for batch-time NSL emission.
     *
     * <p>Synchronized on the target Latency to keep the null-check + list-create + add atomic with
     * respect to other writers and with respect to {@link Latency#dispatch} on the channel event
     * loop.
     */
    public void queue(Acknowledgment ack) {
        Latency last = this.sentQueue.peekLast();
        if (last == null) {
            Boar.debug("[ack-queue] dispatch-inline ack=" + ack.getClass().getSimpleName(), Boar.DebugMessage.WARNING);
            Boar.getInstance().getAcknowledgmentRegistry().dispatch(this.player, ack);
            return;
        }

        synchronized (last) {
            if (last.acknowledgments == null) {
                last.acknowledgments = new ArrayList<>();
            }
            last.acknowledgments.add(ack);
            Boar.debug("[ack-queue] attached ack=" + ack.getClass().getSimpleName() + " to id=" + last.id + " ackCount=" + last.acknowledgments.size(), Boar.DebugMessage.INFO);
        }
    }

    public void onLatencyAccepted(Latency latency) {
        for (final Check check : this.player.getCheckHolder().values()) {
            if (!(check instanceof PingBasedCheck pingBasedCheck)) {
                continue;
            }

            pingBasedCheck.onLatencyAccepted(latency);
        }
    }

    @ToString
    @AllArgsConstructor
    public static final class Latency {
        private final long id;
        private final long ms;
        private final long ns;
        private boolean ours;
        private List<Acknowledgment> acknowledgments;

        public boolean ours() {
            return this.ours;
        }

        public long id() {
            return this.id;
        }

        public long ns() {
            return this.ns;
        }

        public long ms() {
            return this.ms;
        }

        public void dispatch(BoarPlayer player) {
            List<Acknowledgment> snapshot;
            synchronized (this) {
                snapshot = this.acknowledgments;
                this.acknowledgments = null;
            }
            Boar.debug("[ack-dispatch] accepted id=" + this.id + " ours=" + this.ours + " acks=" + LatencyUtil.ackNames(snapshot), Boar.DebugMessage.INFO);
            if (snapshot != null) {
                final BoarAcknowledgmentRegistry registry = Boar.getInstance().getAcknowledgmentRegistry();
                for (Acknowledgment ack : snapshot) {
                    registry.dispatch(player, ack);
                }
            }
        }
    }

    private static String ackNames(List<Acknowledgment> acknowledgments) {
        if (acknowledgments == null || acknowledgments.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < acknowledgments.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(acknowledgments.get(i).getClass().getSimpleName());
        }
        return builder.append(']').toString();
    }
}
