package ac.boar.anticheat.util;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.ack.BoarAcknowledgmentRegistry;
import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.check.api.impl.PingBasedCheck;
import ac.boar.anticheat.player.BoarPlayer;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.*;

@RequiredArgsConstructor
public final class LatencyUtil {
    private final BoarPlayer player;
    private final Map<Long, Latency> pending = new LinkedHashMap<>();
    private volatile int inFlight = 0;

    public Latency prevAcceptedLatency;
    public volatile long prevAcceptedTime = System.currentTimeMillis();

    public boolean hasInFlight() {
        return this.inFlight > 0;
    }

    public void queue(long id, boolean ours) {
        add(new Latency(id, System.currentTimeMillis(), System.nanoTime(), ours, ours ? new ArrayList<>() : null));
    }

    public void queueWithAcks(long id, List<Acknowledgment> acks) {
        add(new Latency(id, System.currentTimeMillis(), System.nanoTime(), true, new ArrayList<>(acks)));
    }

    private void add(Latency latency) {
        this.pending.put(latency.id, latency);
        this.inFlight = this.pending.size();
    }

    public void onLatencyAccepted(Latency latency) {
        for (final Check check : this.player.getCheckHolder().values()) {
            if (!(check instanceof PingBasedCheck pingBasedCheck)) {
                continue;
            }

            pingBasedCheck.onLatencyAccepted(latency);
        }
    }

    public boolean onResponse(long id) {
        final Latency match = this.pending.get(id);
        if (match == null) {
            return false;
        }

        final boolean ours = match.ours;
        int released = 0;
        int releasedWithAcks = 0;
        final Iterator<Latency> it = this.pending.values().iterator();
        while (it.hasNext()) {
            final Latency head = it.next();
            it.remove();
            // Snapshot size must be read before dispatch since it nulls the ack list
            if (head.acknowledgments != null && !head.acknowledgments.isEmpty()) {
                releasedWithAcks++;
            }
            head.dispatch(this.player);
            onLatencyAccepted(head);
            this.prevAcceptedTime = System.currentTimeMillis();
            this.prevAcceptedLatency = head;
            released++;
            if (head == match) {
                break;
            }
        }
        this.inFlight = this.pending.size();

        return ours;
    }

    @ToString
    public static final class Latency {
        private final long id;
        private final long ms;
        private final long ns;
        private boolean ours;
        private List<Acknowledgment> acknowledgments;

        public Latency(long id, long ms, long ns, boolean ours, List<Acknowledgment> acknowledgments) {
            this.id = id;
            this.ms = ms;
            this.ns = ns;
            this.ours = ours;
            this.acknowledgments = acknowledgments;
        }

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
            if (snapshot != null) {
                final BoarAcknowledgmentRegistry registry = Boar.getInstance().getAcknowledgmentRegistry();
                for (Acknowledgment ack : snapshot) {
                    // A throwing handler must not skip the rest of the batch — every ack in a
                    // bundle is independent. A failure must not discard later teleport or velocity updates.
                    try {
                        registry.dispatch(player, ack);
                    } catch (Throwable t) {
                    }
                }
            }
        }
    }
}
