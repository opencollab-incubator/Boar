package ac.boar.anticheat.util;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.ack.BoarAcknowledgmentRegistry;
import ac.boar.anticheat.ack.types.EntityInterpolateAck;
import ac.boar.anticheat.ack.types.FollowerMoveAck;
import ac.boar.anticheat.ack.types.PlayerMetadataAck;
import ac.boar.anticheat.ack.types.UpdateAttributesAck;
import ac.boar.anticheat.ack.types.VehicleClearAck;
import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.check.api.impl.PingBasedCheck;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.VehicleData;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.*;

@RequiredArgsConstructor
public final class LatencyUtil {
    private final BoarPlayer player;
    private final Map<Long, Latency> pending = new LinkedHashMap<>();
    private volatile int inFlight = 0;
    // Guesses of when the client handled each latency list (the acks sent with one latency packet). Set only while a list is dispatched.
    // They do NOT decide ack confirmation or release order.
    // Users: the FollowerMove/Motion handlers stamp FollowerHistory with them (the best-guess vehicle pose for rider seats and boat control).
    // The early guess only feeds FollowerHistory's timing copies (stationary death-exit check).
    // FollowerEnvelope uses the send time and the reply time as bounds. The one exception: a new FollowerHistory starts its envelope
    // at estimatedHandleNs (handleFollowerMove in BoarDefaultAcknowledgments).
    //
    // Model (we only see our own times: sends, input arrivals, reply arrivals; never the client's clock):
    // - Base stamp: max(list sent, last input).
    // - Replies-alone mode (repliesAlone): the stamp moves toward the releasing reply, capped at last input + 55 ms.
    // - Early stamp: the base stamp or the input burst before it, never before the list's send time.
    // - All acks of one list share the same stamps.
    // - The 5 ms input bursts and the reply vote are guesses, not measured client frames.
    // Not covered: real client stamps can fall outside these guesses. In-game testing still showed small differences in the boat takeover start position.
    private Long dispatchHandleNs;
    private Long dispatchReplyNs;
    private Long dispatchEarlyHandleNs;
    private long dispatchGroup = -1;
    private static final long INPUT_BURST_GAP_NS = 5_000_000L;
    private long burstLastNs = Long.MIN_VALUE;
    private long previousBurstNs = Long.MIN_VALUE;
    // Replies in the vote (repliesAlone). A reply with no input near it suggests a later stamp. This does not measure FPS.
    private static final int REPLY_SAMPLES = 8;
    // Cap: if replies are held or inputs stop, the stamp cannot keep moving later forever.
    private static final long REPLY_STAMP_LIMIT_NS = 50_000_000L + INPUT_BURST_GAP_NS;
    // Replies not yet known to be alone or next to an input, oldest first.
    private final ArrayDeque<Long> lookAheadReplies = new ArrayDeque<>();
    private int aloneBits;
    private int replySamples;
    // L in FollowerEnvelope: a lower limit of the client's clock in the tick that sent input k.
    // L = the send time (proxy nanoTime) of the newest latency packet whose reply reached us before input k.
    // Why: the client handles packets after a frame's ticks (Minecraft::update 0x108ca1f94), and the reply goes out behind that frame's inputs
    // on the same ordered stream. So that packet was handled in an earlier frame, and the client's "now" in tick k is at or after its send time.
    private long latestAnsweredSendNs = Long.MIN_VALUE;
    private long answeredAtLastInputNs = Long.MIN_VALUE;

    // Used only by follower history: our guess of when the client handled this list. Dispatch order still follows the releasing reply.
    public long estimatedHandleNs() {
        return this.dispatchHandleNs == null ? System.nanoTime() : this.dispatchHandleNs;
    }

    public long acknowledgmentReplyNs() {
        return this.dispatchReplyNs == null ? estimatedHandleNs() : this.dispatchReplyNs;
    }

    // Used only by follower history: an earlier second guess. In replies-alone mode it is the base stamp.
    // Otherwise it is the input burst before the last one, if allowed. With no earlier choice it equals estimatedHandleNs.
    public long estimatedEarlyHandleNs() {
        return this.dispatchEarlyHandleNs == null ? estimatedHandleNs() : this.dispatchEarlyHandleNs;
    }

    // Used only by follower history: the id of the list being dispatched. All acks of one list use the same stamp in each FollowerHistory timing copy.
    public long acknowledgmentGroup() {
        return this.dispatchGroup;
    }

    /** Lower bound (proxy nanoTime) of the client's "now" during the tick that sent the last input, or Long.MIN_VALUE when no list was answered yet. */
    public long nowLowerBoundNs() {
        return this.answeredAtLastInputNs;
    }

    public void observeInputArrival(final long ns) {
        this.answeredAtLastInputNs = this.latestAnsweredSendNs;
        this.resolveReplies(ns);
        while (!this.lookAheadReplies.isEmpty()) {
            this.lookAheadReplies.pollFirst();
            this.recordReply(false);
        }
        if (this.burstLastNs != Long.MIN_VALUE && ns - this.burstLastNs > INPUT_BURST_GAP_NS) {
            this.previousBurstNs = this.burstLastNs;
        }
        this.burstLastNs = ns;
    }

    // A reply is alone if no input came within INPUT_BURST_GAP_NS after it.
    private void resolveReplies(final long ns) {
        while (!this.lookAheadReplies.isEmpty() && ns - this.lookAheadReplies.peekFirst() > INPUT_BURST_GAP_NS) {
            this.lookAheadReplies.pollFirst();
            this.recordReply(true);
        }
    }

    private void recordReply(final boolean alone) {
        this.aloneBits = (this.aloneBits << 1 | (alone ? 1 : 0)) & ((1 << REPLY_SAMPLES) - 1);
        this.replySamples = Math.min(REPLY_SAMPLES, this.replySamples + 1);
    }

    // True when most recent replies came alone. A guess of a high frame rate, not a measured client FPS.
    private boolean repliesAlone() {
        return this.replySamples >= REPLY_SAMPLES / 2 && Integer.bitCount(this.aloneBits) * 2 > this.replySamples;
    }

    public Latency prevAcceptedLatency;
    public volatile long prevAcceptedTime = System.currentTimeMillis();

    public boolean hasInFlight() {
        return this.inFlight > 0;
    }

    public void queue(long id, boolean ours) {
        add(new Latency(id, System.currentTimeMillis(), System.nanoTime(), ours, ours ? new ArrayList<>() : null));
    }

    public void queueWithAcks(long id, List<Acknowledgment> acks) {
        final Latency latency = new Latency(id, System.currentTimeMillis(), System.nanoTime(), true, new ArrayList<>(acks));
        latency.metaSummary = metadataSummary(acks);
        if (latency.metaSummary != null) {
            ackDebug("nsl=" + id + " SENT (end of batch) meta=" + latency.metaSummary);
            this.player.ackDebugUntilTick = this.player.tick + 40;
        }
        add(latency);
    }

    private String metadataSummary(final List<Acknowledgment> acks) {
        if (!Boar.getConfig().debugMode()) {
            return null;
        }

        StringBuilder builder = null;
        for (final Acknowledgment ack : acks) {
            if (ack instanceof PlayerMetadataAck meta && meta.flags() != null) {
                builder = builder == null ? new StringBuilder("[") : builder.append(", ");
                builder.append("{sentAtTick=").append(meta.sentTick())
                        .append(" sprint=").append(meta.flags().contains(EntityFlag.SPRINTING))
                        .append(" using=").append(meta.flags().contains(EntityFlag.USING_ITEM))
                        .append(" sneak=").append(meta.flags().contains(EntityFlag.SNEAKING)).append('}');
            } else if (ack instanceof UpdateAttributesAck attributes) {
                // The speed the client gets in this batch, as we send it (after stripModifiers).
                for (final AttributeData data : attributes.attributes()) {
                    if (!data.getName().equals("minecraft:movement")) {
                        continue;
                    }
                    builder = builder == null ? new StringBuilder("[") : builder.append(", ");
                    builder.append("{speed value=").append(data.getValue())
                            .append(" base=").append(data.getDefaultValue())
                            .append(" mods=").append(data.getModifiers().size()).append('}');
                }
            }
        }
        return builder == null ? null : builder.append(']').toString();
    }

    private void ackDebug(final String message) {
        Boar.debug(this.player.getSession().name() + ": [ack-debug] tick=" + this.player.tick + " t=" + (System.nanoTime() / 1_000_000L % 100_000L) + "ms " + message, Boar.DebugMessage.INFO);
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
        final long replyNs = System.nanoTime();
        final Latency match = this.pending.get(id);
        if (match == null) {
            return false;
        }
        this.resolveReplies(replyNs);
        // Keep at most REPLY_SAMPLES pending replies; drop the oldest. A dropped reply is never counted, so the vote can differ from an unbounded queue.
        if (this.lookAheadReplies.size() >= REPLY_SAMPLES) {
            this.lookAheadReplies.pollFirst();
        }
        this.lookAheadReplies.addLast(replyNs);

        final boolean ours = match.ours;
        final long lastInputNs = this.player.inputArrivalNs;
        final Iterator<Latency> it = this.pending.values().iterator();
        while (it.hasNext()) {
            final Latency head = it.next();
            it.remove();
            if (head.metaSummary != null) {
                ackDebug("nsl=" + head.id + " REPLY rtt=" + (System.currentTimeMillis() - head.ms) + "ms, applying meta=" + head.metaSummary
                        + (head == match ? "" : " (released by later reply nsl=" + id + ")"));
            }
            head.dispatch(this.player, replyNs, lastInputNs);
            onLatencyAccepted(head);
            this.prevAcceptedTime = head.ms;
            this.prevAcceptedLatency = head;
            this.latestAnsweredSendNs = Math.max(this.latestAnsweredSendNs, head.ns);
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
        private String metaSummary; // debug only

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

        private void dispatch(BoarPlayer player, long replyNs, long lastInputNs) {
            List<Acknowledgment> snapshot;
            synchronized (this) {
                snapshot = this.acknowledgments;
                this.acknowledgments = null;
            }
            if (snapshot != null) {
                final BoarAcknowledgmentRegistry registry = Boar.getInstance().getAcknowledgmentRegistry();
                final VehicleData rideAtListStart = player.vehicleData;
                final EntityCache boatAtListStart = boatWithPlayerFirstPassenger(player, rideAtListStart);
                boolean rideClearedInList = false;
                final LatencyUtil latency = player.getLatencyUtil();
                final Long previousHandleNs = latency.dispatchHandleNs;
                final Long previousReply = latency.dispatchReplyNs;
                final Long previousEarly = latency.dispatchEarlyHandleNs;
                final long previousGroup = latency.dispatchGroup;
                // Base stamp: the last input, but never before this list's send time. A guess, not a measured time.
                // The real releasing reply time is kept too (acknowledgmentReplyNs). FollowerHistory.addMove and addMotion pass it to FollowerEnvelope.
                latency.dispatchHandleNs = Math.max(this.ns, lastInputNs);
                latency.dispatchReplyNs = replyNs;
                if (latency.repliesAlone()) {
                    // High frame rate: the client stamped about one frame before the reply.
                    // The early choice is the base stamp (a frame that also sent an input).
                    latency.dispatchEarlyHandleNs = latency.dispatchHandleNs;
                    latency.dispatchHandleNs = Math.max(latency.dispatchHandleNs, Math.min(replyNs, lastInputNs + REPLY_STAMP_LIMIT_NS));
                } else {
                    // Early choice: the input burst before the last one (the frame before).
                    // If that burst arrived before the list was sent, the list could not reach that frame, so there is no earlier choice.
                    final boolean noEarlierChoice = latency.previousBurstNs == Long.MIN_VALUE || latency.burstLastNs != lastInputNs
                            || latency.previousBurstNs <= this.ns;
                    latency.dispatchEarlyHandleNs = noEarlierChoice ? latency.dispatchHandleNs : Math.min(latency.dispatchHandleNs, latency.previousBurstNs);
                }
                latency.dispatchGroup = this.id;
                try {
                    for (Acknowledgment ack : snapshot) {
                        final VehicleData rideBeforeAck = player.vehicleData;
                        Acknowledgment dispatched = ack;
                        // Act as if the client handled these acks back to back, but only inside this list. Never carry that over to another list.
                        if (rideClearedInList && ack instanceof EntityInterpolateAck movement
                                && movesBoatAtListStart(player, boatAtListStart, movement.ordinaryActorMove(), movement.boat())) {
                            dispatched = movement.withRideClearedInList();
                        }
                        if (rideClearedInList && ack instanceof FollowerMoveAck movement
                                && movesBoatAtListStart(player, boatAtListStart, movement.ordinaryActorMove(), movement.boat())) {
                            dispatched = movement.withRideClearedInList();
                        }
                        // A handler that throws must not skip the rest of the list. Each ack is independent; later teleport or velocity updates must still run.
                        try {
                            registry.dispatch(player, dispatched);
                        } catch (Throwable t) {
                            Boar.getInstance().getPlatform().logger().error(player.getSession().name() + ": acknowledgment "
                                    + ack.getClass().getSimpleName() + " threw", t);
                        }
                        if (boatAtListStart != null && ack instanceof VehicleClearAck && rideBeforeAck == rideAtListStart
                                && player.vehicleData != rideAtListStart) {
                            rideClearedInList = true;
                        }
                    }
                } finally {
                    latency.dispatchHandleNs = previousHandleNs;
                    latency.dispatchReplyNs = previousReply;
                    latency.dispatchEarlyHandleNs = previousEarly;
                    latency.dispatchGroup = previousGroup;
                }
            }
        }

        // An ordinary move of the boat the player rode at the list start, while that boat is still the actor for its runtime ID.
        // Comparing the boat object is enough: an actor's runtime ID is final and its unique ID is set once (CompensatedWorld.addToCache).
        private static boolean movesBoatAtListStart(final BoarPlayer player, final EntityCache boatAtListStart, final boolean ordinaryActorMove,
                final EntityCache boatEntity) {
            return ordinaryActorMove && boatEntity == boatAtListStart && player.compensatedWorld.getEntity(boatAtListStart.getRuntimeId()) == boatAtListStart;
        }

        private static EntityCache boatWithPlayerFirstPassenger(BoarPlayer player, VehicleData ride) {
            final EntityCache boat = ride == null ? null : player.compensatedWorld.getEntity(ride.vehicleRuntimeId);
            return boat != null && VehicleData.isFirstPassengerOfBoat(player, ride, boat) ? boat : null;
        }
    }
}
