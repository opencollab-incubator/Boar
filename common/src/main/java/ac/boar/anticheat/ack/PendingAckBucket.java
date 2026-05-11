package ac.boar.anticheat.ack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-player bucket that accumulates acknowledgments waiting for the next outbound bedrock batch
 * flush. Drained by {@code BoarBatchAcknowledger} on flush, which generates a single
 * NetworkStackLatency id covering every drained ack.
 *
 * <p>Writers (emitters calling {@code markPending}) may run on any thread — the channel event
 * loop during inbound/outbound packet handling, or the Geyser session tick thread during
 * {@code BoarPlayer.serverTick}. The drain happens on the channel event loop. The lock keeps the
 * compound add and the snapshot+clear atomic so an ack cannot be observed half-published.
 */
public final class PendingAckBucket {
    private final ReentrantLock lock = new ReentrantLock();
    private List<Acknowledgment> pending = new ArrayList<>();

    public void add(Acknowledgment ack) {
        this.lock.lock();
        try {
            this.pending.add(ack);
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Atomically returns the current pending list and replaces it with an empty one. Callers own
     * the returned list; mutate it freely.
     */
    public List<Acknowledgment> drain() {
        this.lock.lock();
        try {
            if (this.pending.isEmpty()) {
                return Collections.emptyList();
            }
            List<Acknowledgment> snapshot = this.pending;
            this.pending = new ArrayList<>();
            return snapshot;
        } finally {
            this.lock.unlock();
        }
    }

    public boolean isEmpty() {
        this.lock.lock();
        try {
            return this.pending.isEmpty();
        } finally {
            this.lock.unlock();
        }
    }
}
