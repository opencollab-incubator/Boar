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
    }

    public void queue(Acknowledgment ack) {
        if (this.sentQueue.isEmpty()) {
            Boar.getInstance().getAcknowledgmentRegistry().dispatch(this.player, ack);
            return;
        }

        if (this.sentQueue.getLast().acknowledgments == null) {
            this.sentQueue.getLast().acknowledgments = new ArrayList<>();
        }

        this.sentQueue.getLast().acknowledgments.add(ack);
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
            if (this.acknowledgments != null) {
                final BoarAcknowledgmentRegistry registry = Boar.getInstance().getAcknowledgmentRegistry();
                for (Acknowledgment ack : this.acknowledgments) {
                    registry.dispatch(player, ack);
                }
            }
            this.acknowledgments = null;
        }
    }
}
