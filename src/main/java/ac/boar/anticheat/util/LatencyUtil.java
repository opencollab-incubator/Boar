package ac.boar.anticheat.util;

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

    public void queue(Runnable runnable) {
        if (this.sentQueue.isEmpty()) {
            runnable.run();
            return;
        }

        if (this.sentQueue.getLast().tasks == null) {
            this.sentQueue.getLast().tasks = new ArrayList<>();
        }

        this.sentQueue.getLast().tasks.add(runnable);
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
        private List<Runnable> tasks;

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

        public void run() {
            if (this.tasks != null) {
                this.tasks.forEach(Runnable::run);
            }
            this.tasks = null;
        }
    }
}
