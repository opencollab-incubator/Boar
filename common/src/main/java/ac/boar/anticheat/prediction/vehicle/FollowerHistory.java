package ac.boar.anticheat.prediction.vehicle;

import ac.boar.anticheat.util.MathUtil;
import org.cloudburstmc.math.GenericMath;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Boar's best guess of where the client shows a vehicle the server moves: one pose, not a set.
 * It copies the client's interpolation (PredictedMovementComponent, HistoryCache::addHistory, tickNextPosition, Actor::lerpTo),
 * with one guessed stamp per packet (from LatencyUtil).
 * <p>
 * Used for the seat of a rider the server moves (FollowVehicleTicker), the box of a boat someone else drives (CompensatedWorldImpl),
 * and the start pose when the player takes control of a boat (EntityCache). Checks that need a limit use FollowerEnvelope (envelope()).
 * <p>
 * Timing copies: when a stamp has two likely values, the history splits into copies that use the other value.
 * Only the stationary death-exit check (admitsStationaryExitPose) reads them. They never change the main history.
 */
public final class FollowerHistory {
    // Shared with FollowerEnvelope, which copies the same client interpolation.
    static final int MAX_ENTRIES = 64;
    static final int LERP_STEPS = 3;
    static final int SMOOTH_STEPS = 3;
    private static final int BASE_DELAY_COUNT = 2;
    private static final float TICK_MS = 50.0F;
    private static final float MAX_DELAY_FACTOR = 8.0F;
    static final long NANOS_PER_MS = 1_000_000L;
    // Input arrival times we keep to measure how uneven they are (jitter), and the fewest we need before we judge.
    private static final int JITTER_SAMPLES = 40;
    private static final int MIN_JITTER_SAMPLES = 10;
    // The most ticks of jitter we accept. A resync also distrusts the history for this many ticks plus LERP_STEPS.
    private static final int MAX_PHASE_TICKS = 8;
    private static final int MAX_TIMING_COPIES = 8;

    private static final class Entry {
        long timestampNs, sequence;
        boolean move, validStart, onGround, outOfOrder;
        float x, y, z, pitch, yaw;

        Entry copy() {
            final Entry entry = new Entry();
            entry.timestampNs = this.timestampNs;
            entry.sequence = this.sequence;
            entry.move = this.move;
            entry.validStart = this.validStart;
            entry.onGround = this.onGround;
            entry.outOfOrder = this.outOfOrder;
            entry.x = this.x;
            entry.y = this.y;
            entry.z = this.z;
            entry.pitch = this.pitch;
            entry.yaw = this.yaw;
            return entry;
        }
    }

    // The client's smoothed time between move packets (_updateRuntimeData): a new value restarts a 3-step blend from the old one.
    private static final class SmoothedPeriod {
        long old, target, current;
        int counter;

        void set(final long period) {
            if (period != this.target) {
                this.old = this.target;
                this.target = period;
                this.counter = 0;
            }
        }

        void tick() {
            this.counter = Math.min(this.counter + 1, SMOOTH_STEPS);
            this.current = smoothed(this.counter, this.old, this.target);
        }

        void reset() {
            this.old = this.target = this.current = 0;
            this.counter = 0;
        }

        void copyFrom(final SmoothedPeriod from) {
            this.old = from.old;
            this.target = from.target;
            this.current = from.current;
            this.counter = from.counter;
        }

        boolean sameAs(final SmoothedPeriod other) {
            return this.old == other.old && this.target == other.target && this.current == other.current && this.counter == other.counter;
        }
    }

    // The smoothed period after count steps of the blend from oldMs to targetMs. FollowerEnvelope uses it too.
    static long smoothed(final int count, final long oldMs, final long targetMs) {
        return count < SMOOTH_STEPS ? oldMs + (long) ((count / (float) SMOOTH_STEPS) * (float) (targetMs - oldMs)) : targetMs;
    }

    private final List<Entry> entries = new ArrayList<>();
    private long nextSequence;
    private Entry latestMove;
    // The client's last picked pair. It keeps using it when no new pair is found.
    private Entry cachedLower, cachedUpper;
    private boolean locallyPredicted;
    private long addedActorSequence = -1;

    private long lastMoveNs;
    private final SmoothedPeriod movePeriod = new SmoothedPeriod();

    // The shown vehicle: where it is now, the target it moves to, the steps left, and where it was at the start of the tick (render).
    private float x, y, z, pitch, yaw;
    private float targetX, targetY, targetZ, targetPitch, targetYaw;
    private int remaining;
    private float renderX, renderY, renderZ, renderYaw;

    // The main history and its timing copies share one arrival clock per connection. BoarPlayer.tick adds its samples.
    private final ArrivalClock arrivalClock;
    // Ticks left until we trust the history again after a resync.
    private int settleTicks;
    private long lastTick = Long.MIN_VALUE;
    // The tick the client's local control ended, or Long.MIN_VALUE.
    private long controlEndTick = Long.MIN_VALUE;

    // Timing copies. Null inside a copy itself.
    private final List<FollowerHistory> timingCopies;
    private long choiceGroup = Long.MIN_VALUE;
    private boolean choiceEarly;

    // The set limit on the same client state. Main history only, null in a copy.
    private final FollowerEnvelope envelope;
    // The envelope's shown set and yaw range right after the first-control step. The boat takeover checks against these.
    private PositionSet envelopeControlSet;
    private float[] envelopeControlYawOffsets;
    private boolean envelopeControlTrusted;

    public FollowerHistory(final long nowNs, final float x, final float y, final float z, final float pitch, final float yaw, final ArrivalClock arrivalClock) {
        this.arrivalClock = arrivalClock;
        this.timingCopies = new ArrayList<>();
        this.envelope = new FollowerEnvelope(nowNs, x, y, z, yaw);
        this.lastMoveNs = nowNs;
        this.x = this.renderX = x;
        this.y = this.renderY = y;
        this.z = this.renderZ = z;
        this.pitch = pitch;
        this.yaw = this.renderYaw = yaw;
    }

    // A timing copy of the whole state. A cached pair entry can outlive its place in the list, so entries are copied by identity (same object, one copy).
    private FollowerHistory(final FollowerHistory from) {
        this.arrivalClock = from.arrivalClock;
        this.timingCopies = null;
        this.envelope = null;
        final Map<Entry, Entry> copies = new IdentityHashMap<>();
        for (final Entry entry : from.entries) {
            this.entries.add(copies.computeIfAbsent(entry, Entry::copy));
        }
        this.latestMove = from.latestMove == null ? null : copies.computeIfAbsent(from.latestMove, Entry::copy);
        this.cachedLower = from.cachedLower == null ? null : copies.computeIfAbsent(from.cachedLower, Entry::copy);
        this.cachedUpper = from.cachedUpper == null ? null : copies.computeIfAbsent(from.cachedUpper, Entry::copy);
        this.nextSequence = from.nextSequence;
        this.locallyPredicted = from.locallyPredicted;
        this.addedActorSequence = from.addedActorSequence;
        this.lastMoveNs = from.lastMoveNs;
        this.movePeriod.copyFrom(from.movePeriod);
        this.x = from.x;
        this.y = from.y;
        this.z = from.z;
        this.pitch = from.pitch;
        this.yaw = from.yaw;
        this.targetX = from.targetX;
        this.targetY = from.targetY;
        this.targetZ = from.targetZ;
        this.targetPitch = from.targetPitch;
        this.targetYaw = from.targetYaw;
        this.remaining = from.remaining;
        this.renderX = from.renderX;
        this.renderY = from.renderY;
        this.renderZ = from.renderZ;
        this.renderYaw = from.renderYaw;
        this.settleTicks = from.settleTicks;
        // lastTick and controlEndTick are not copied: nothing reads them from a copy.
        this.choiceGroup = from.choiceGroup;
        this.choiceEarly = from.choiceEarly;
    }

    public FollowerEnvelope envelope() {
        return this.envelope;
    }

    public PositionSet envelopeControlSet() {
        return this.envelopeControlSet;
    }

    // The yaws the client can show at the first-control step, as {lo, hi} in degrees from our own yaw at that step. Null when the range is not known.
    public float[] envelopeControlYawOffsets() {
        return this.envelopeControlYawOffsets;
    }

    public boolean envelopeControlTrusted() {
        return this.envelopeControlTrusted;
    }

    public long controlEndTick() {
        return this.controlEndTick;
    }

    // A server move. earlyHandleNs is the other stamp the client may have used. group is the latency list the packet came in.
    // The envelope gets the same move with its time window: sentNs = when we sent it, replyNs = when the reply to the latency packet behind it reached us.
    public void addMove(final long handleNs, final long earlyHandleNs, final long group, final long sentNs, final long replyNs,
            final float x, final float y, final float z, final float pitch, final float yaw, final boolean onGround, final boolean addedActor) {
        this.forkTimingCopies(handleNs, earlyHandleNs, group);
        for (final FollowerHistory timingCopy : this.timingCopies) {
            timingCopy.addMoveEntry(timingCopy.stamp(handleNs, earlyHandleNs, group), x, y, z, pitch, yaw, onGround, addedActor, true);
        }
        this.addMoveEntry(handleNs, x, y, z, pitch, yaw, onGround, addedActor, true);
        this.envelope.addMove(sentNs, replyNs, x, y, z, yaw, onGround);
    }

    private void addMoveEntry(final long handleNs, final float x, final float y, final float z, final float pitch, final float yaw,
            final boolean onGround, final boolean addedActor, final boolean updatePeriod) {
        final Entry entry = this.newEntry(handleNs);
        entry.move = true;
        entry.validStart = true;
        entry.onGround = onGround;
        entry.x = x;
        entry.y = y;
        entry.z = z;
        entry.pitch = pitch;
        entry.yaw = yaw;
        this.latestMove = entry;
        if (addedActor) {
            this.addedActorSequence = entry.sequence;
        }
        this.add(entry, updatePeriod);
    }

    // A motion entry copies the position of the newest move. Sources: SetActorMotion, MotionPredictionHints, and the motion in AddActor.
    // sentNs and replyNs: the envelope's time window, as in addMove.
    public void addMotion(final long handleNs, final long earlyHandleNs, final long group, final long sentNs, final long replyNs, final boolean onGround) {
        this.forkTimingCopies(handleNs, earlyHandleNs, group);
        for (final FollowerHistory timingCopy : this.timingCopies) {
            timingCopy.addMotionEntry(timingCopy.stamp(handleNs, earlyHandleNs, group), onGround);
        }
        this.addMotionEntry(handleNs, onGround);
        this.envelope.addMotion(sentNs, replyNs, onGround);
    }

    private void addMotionEntry(final long handleNs, final boolean onGround) {
        final Entry entry = this.newEntry(handleNs);
        entry.move = false;
        entry.onGround = onGround;
        final Entry move = this.latestMove;
        entry.validStart = move != null;
        if (move != null) {
            entry.x = move.x;
            entry.y = move.y;
            entry.z = move.z;
            entry.pitch = move.pitch;
            entry.yaw = move.yaw;
        }
        // Only a move entry changes the move period (updateMovePeriod).
        this.add(entry, false);
    }

    private List<FollowerHistory> timingCopyList() {
        return this.timingCopies == null ? List.of() : this.timingCopies;
    }

    // The stamp this copy uses for a packet of the group.
    private long stamp(final long handleNs, final long earlyHandleNs, final long group) {
        return this.choiceGroup == group && this.choiceEarly ? earlyHandleNs : handleNs;
    }

    // Stamp of the last entry. An earlier stamp is moved up to it (newEntry), so a choice before it makes no difference.
    private long lastTimestampNs() {
        return this.entries.isEmpty() ? Long.MIN_VALUE : this.entries.get(this.entries.size() - 1).timestampNs;
    }

    // A packet from a new latency list with two possible stamps splits every copy where the two stamps still differ.
    // The first split makes the first copy from this history as it is now.
    // Past the first check earlyHandleNs < handleNs. So the two stamps only end up the same when the last stamp is at or after
    // handleNs: newEntry moves both up to it.
    private void forkTimingCopies(final long handleNs, final long earlyHandleNs, final long group) {
        if (earlyHandleNs >= handleNs) {
            return;
        }
        if (this.timingCopies.isEmpty()) {
            if (this.lastTimestampNs() >= handleNs) {
                return;
            }
            this.timingCopies.add(new FollowerHistory(this));
        }

        final List<FollowerHistory> next = new ArrayList<>();
        for (final FollowerHistory timingCopy : this.timingCopies) {
            next.add(timingCopy);
            if (timingCopy.choiceGroup == group) {
                continue;
            }
            timingCopy.choiceGroup = group;
            timingCopy.choiceEarly = false;
            if (timingCopy.lastTimestampNs() >= handleNs) {
                continue;
            }
            final FollowerHistory early = new FollowerHistory(timingCopy);
            early.choiceEarly = true;
            next.add(early);
        }
        while (next.size() > MAX_TIMING_COPIES) {
            next.remove(next.size() - 1);
        }
        this.timingCopies.clear();
        this.timingCopies.addAll(next);
    }

    // Two copies in the same state show the same thing from now on. One is enough.
    private void mergeTimingCopies() {
        for (int i = 0; i < this.timingCopies.size(); i++) {
            for (int j = this.timingCopies.size() - 1; j > i; j--) {
                if (this.timingCopies.get(i).sameState(this.timingCopies.get(j))) {
                    this.timingCopies.remove(j);
                }
            }
        }
    }

    private boolean sameState(final FollowerHistory other) {
        if (this.entries.size() != other.entries.size()) {
            return false;
        }
        for (int i = 0; i < this.entries.size(); i++) {
            final Entry a = this.entries.get(i), b = other.entries.get(i);
            if (a.sequence != b.sequence || a.timestampNs != b.timestampNs || a.outOfOrder != b.outOfOrder) {
                return false;
            }
        }
        if (!sameEntry(this.cachedLower, other.cachedLower) || !sameEntry(this.cachedUpper, other.cachedUpper)) {
            return false;
        }
        return this.lastMoveNs == other.lastMoveNs && this.movePeriod.sameAs(other.movePeriod) && this.remaining == other.remaining
                && this.settleTicks == other.settleTicks && this.addedActorSequence == other.addedActorSequence
                && this.x == other.x && this.y == other.y && this.z == other.z && this.pitch == other.pitch && this.yaw == other.yaw
                && this.targetX == other.targetX && this.targetY == other.targetY && this.targetZ == other.targetZ
                && this.targetPitch == other.targetPitch && this.targetYaw == other.targetYaw
                && this.renderX == other.renderX && this.renderY == other.renderY && this.renderZ == other.renderZ && this.renderYaw == other.renderYaw;
    }

    private static boolean sameEntry(final Entry a, final Entry b) {
        return a == null ? b == null : b != null && a.sequence == b.sequence && a.timestampNs == b.timestampNs;
    }

    private Entry newEntry(final long handleNs) {
        final Entry entry = new Entry();
        entry.sequence = this.nextSequence++;
        // Keep the order: a stamp before the last entry is moved up to it.
        final Entry last = this.entries.isEmpty() ? null : this.entries.get(this.entries.size() - 1);
        entry.outOfOrder = last != null && handleNs < last.timestampNs;
        entry.timestampNs = last != null ? Math.max(handleNs, last.timestampNs) : handleNs;
        return entry;
    }

    private void add(final Entry entry, final boolean updatePeriod) {
        this.entries.add(entry);
        while (this.entries.size() > MAX_ENTRIES) {
            this.entries.remove(0);
        }
        if (updatePeriod) {
            this.updateMovePeriod();
        }
    }

    public boolean isLocallyPredicted() {
        return this.locallyPredicted;
    }

    // While the client controls the vehicle we clear the history every tick. On the next tick without control, we add one control-end entry (resumeRemoteTick).
    // nowLowerNs: the lower limit of the client's clock this tick (LatencyUtil.nowLowerBoundNs), for the envelope.
    public void resetForLocalControl(final long nowNs, final long nowLowerNs, final long tick,
            final float x, final float y, final float z, final float pitch, final float yaw) {
        this.envelope.reset(nowLowerNs == Long.MIN_VALUE ? nowNs : nowLowerNs, nowNs, x, y, z, yaw);
        // The client simulates the vehicle itself, so no stamp is in doubt.
        this.timingCopies.clear();
        this.locallyPredicted = true;
        this.controlEndTick = Long.MIN_VALUE;
        this.entries.clear();
        this.cachedLower = this.cachedUpper = this.latestMove = null;
        this.addedActorSequence = -1;
        this.movePeriod.reset();
        this.lastMoveNs = nowNs;
        this.remaining = this.settleTicks = 0;
        this.setShownPose(x, y, z, pitch, yaw);
        this.lastTick = tick;
    }

    // Only resumeRemoteTick calls this, and only while locally predicted.
    private void addControlEndEntry(final long nowNs, final float x, final float y, final float z, final float pitch, final float yaw, final boolean onGround) {
        this.locallyPredicted = false;
        this.setShownPose(x, y, z, pitch, yaw);
        this.addMoveEntry(nowNs, x, y, z, quantizedRotation(pitch), quantizedRotation(yaw), onGround, false, false);
    }

    // The client stores the control-end entry's rotation as a byte: 256 steps per turn.
    private static float quantizedRotation(final float angle) {
        return ((byte) (int) ((MathUtil.wrapYaw(angle) / 360.0F) * 256.0F) / 256.0F) * 360.0F;
    }

    private void setShownPose(final float x, final float y, final float z, final float pitch, final float yaw) {
        this.x = this.renderX = this.targetX = x;
        this.y = this.renderY = this.targetY = y;
        this.z = this.renderZ = this.targetZ = z;
        this.pitch = this.targetPitch = pitch;
        this.yaw = this.renderYaw = this.targetYaw = yaw;
    }

    public record ActorPose(float x, float y, float z, float pitch, float yaw, float headYaw, boolean onGround) {}

    public record ControlAdvance(ActorPose before, ActorPose after, boolean stepped) {}

    public ActorPose currentPose(final float headYaw, final boolean onGround) {
        return new ActorPose(this.x, this.y, this.z, this.pitch, this.yaw, headYaw, onGround);
    }

    public boolean hasPositionStep() {
        return this.remaining > 0;
    }

    // First tick of local control: finish the current interpolation step. Do not copy the render pose and do not pick a new target.
    // Also keeps the envelope's set and yaw range from this step for the boat takeover check.
    // Timing copies are not stepped: the only caller (EntityCache.captureBoatControlStartPose) is always followed by
    // EntityCache.resetToLocalControl, which clears them.
    public ControlAdvance advanceForControl(final float headYaw, final boolean onGround) {
        final ActorPose before = this.currentPose(headYaw, onGround);
        final boolean stepped = this.remaining > 0;
        this.advance();
        this.envelope.advanceForControl();
        this.envelopeControlSet = this.envelope.shownSet().copy();
        this.envelopeControlTrusted = !this.envelope.assumedNow();
        final FollowerEnvelope.YawRange yaws = this.envelope.shownYaw();
        if (yaws.full()) {
            this.envelopeControlYawOffsets = null;
        } else {
            final float lo = MathUtil.wrapYaw(yaws.lo() - this.yaw);
            this.envelopeControlYawOffsets = new float[]{lo, lo + (yaws.hi() - yaws.lo())};
        }
        return new ControlAdvance(before, this.currentPose(headYaw, onGround), stepped);
    }

    // The ride ended this tick. The normal entity interpolation already took a step before we saw that control changed.
    // Keep the pose from before that step (for collision) and any steps left, until the history can pick a pair.
    // Only called on a boat whose history is locally predicted (BoarPlayer.tickRemoteFollowerHistory). So there are no timing copies:
    // resetForLocalControl cleared them, and the follower acks add nothing to a locally predicted boat (BoarDefaultAcknowledgments.handleFollowerMove
    // and handleFollowerMotion).
    public void resumeRemoteTick(final long nowNs, final long nowLowerNs, final long tick, final ActorPose before, final ActorPose after,
            final ActorPose pendingTarget, final int pendingSteps) {
        // The envelope reads only x, y, z of before. The control-end entry gets the same byte-rounded yaw as ours (addControlEndEntry).
        final float[] envelopeBefore = new float[]{before.x(), before.y(), before.z()};
        final float[] envelopeAfter = new float[]{after.x(), after.y(), after.z(), quantizedRotation(after.yaw())};
        final float[] envelopeTarget = pendingTarget == null ? null : new float[]{pendingTarget.x(), pendingTarget.y(), pendingTarget.z(), pendingTarget.yaw()};
        this.envelope.resumeRemoteTick(nowLowerNs, nowNs, envelopeBefore, envelopeAfter, after.onGround(), envelopeTarget, pendingSteps);
        this.beginTick();
        this.addControlEndEntry(nowNs, after.x(), after.y(), after.z(), after.pitch(), after.yaw(), after.onGround());
        this.controlEndTick = tick;
        this.renderX = before.x();
        this.renderY = before.y();
        this.renderZ = before.z();
        this.renderYaw = before.yaw();
        if (pendingTarget != null && pendingSteps > 0) {
            this.targetX = pendingTarget.x();
            this.targetY = pendingTarget.y();
            this.targetZ = pendingTarget.z();
            this.targetPitch = pendingTarget.pitch();
            this.targetYaw = pendingTarget.yaw();
            this.remaining = pendingSteps;
        }
        this.selectTarget(nowNs);
        this.lastTick = tick;
    }

    private void updateMovePeriod() {
        if (this.entries.size() < 2) {
            return;
        }
        final Entry latest = this.entries.get(this.entries.size() - 1);
        final Entry previous = this.entries.get(this.entries.size() - 2);
        if (latest.outOfOrder || previous.outOfOrder || !latest.move) {
            return;
        }
        this.movePeriod.set((latest.timestampNs - this.lastMoveNs) / NANOS_PER_MS);
        this.lastMoveNs = latest.timestampNs;
    }

    // One client tick, in the client's order: keep the shown pose as the render pose, step towards the target, then pick a new target.
    public void tick(final long nowNs, final long nowLowerNs, final long tick) {
        for (final FollowerHistory timingCopy : this.timingCopyList()) {
            timingCopy.tick(nowNs, Long.MIN_VALUE, tick);
        }
        if (this.timingCopies != null) {
            this.mergeTimingCopies();
        }
        if (this.envelope != null) {
            this.envelope.tick(nowLowerNs, nowNs);
        }
        this.beginTick();
        this.advance();
        this.selectTarget(nowNs);
        this.lastTick = tick;
    }

    private void beginTick() {
        if (this.settleTicks > 0) {
            this.settleTicks--;
        }
        this.renderX = this.x;
        this.renderY = this.y;
        this.renderZ = this.z;
        this.renderYaw = this.yaw;
    }

    public boolean tickedLast(final long tick) {
        return this.lastTick == tick - 1;
    }

    public boolean hasTicked() {
        return this.lastTick != Long.MIN_VALUE;
    }

    public boolean tickedAt(final long tick) {
        return this.lastTick == tick;
    }

    // For a vehicle we did not tick last tick: jump straight to the target the history gives now. We missed the ticks in between,
    // so the history is not trusted for a few ticks (settleTicks).
    public void resync(final long nowNs, final long nowLowerNs) {
        for (final FollowerHistory timingCopy : this.timingCopyList()) {
            timingCopy.resync(nowNs, Long.MIN_VALUE);
        }
        if (this.envelope != null) {
            this.envelope.resync(nowLowerNs, nowNs);
        }
        this.selectTarget(nowNs);
        if (this.remaining > 0) {
            this.x = this.targetX;
            this.y = this.targetY;
            this.z = this.targetZ;
            this.pitch = this.targetPitch;
            this.yaw = this.targetYaw;
            this.remaining = 0;
        }
        this.settleTicks = MAX_PHASE_TICKS + LERP_STEPS;
    }

    public float distanceSquared(final float x, final float y, final float z) {
        return MathUtil.square(this.x - x) + MathUtil.square(this.y - y) + MathUtil.square(this.z - z);
    }

    // Distance squared from the newest server move (where the server put the vehicle last), or +infinity before any move.
    public float latestMoveDistanceSquared(final float x, final float y, final float z) {
        final Entry move = this.latestMove;
        return move == null ? Float.POSITIVE_INFINITY : MathUtil.square(move.x - x) + MathUtil.square(move.y - y) + MathUtil.square(move.z - z);
    }

    // One interpolation step towards the target. The last step lands on it.
    private void advance() {
        if (this.remaining <= 0) {
            return;
        }
        if (this.remaining == 1) {
            this.x = this.targetX;
            this.y = this.targetY;
            this.z = this.targetZ;
        } else {
            // The client multiplies by 1/remaining (it does not divide).
            final float weight = 1.0F / this.remaining;
            this.x = this.x + (this.targetX - this.x) * weight;
            this.y = this.y + (this.targetY - this.y) * weight;
            this.z = this.z + (this.targetZ - this.z) * weight;
        }
        this.pitch = stepAngle(this.pitch, this.targetPitch, this.remaining);
        this.yaw = stepAngle(this.yaw, this.targetYaw, this.remaining);
        this.remaining--;
    }

    /**
     * One interpolation step of an angle in degrees, with steps the steps left (at least 1). The last step lands on the target.
     * Otherwise the angle turns the short way by 1/steps of the gap (the client multiplies, it does not divide). The result is wrapped to -180..180.
     * Also used for the rotation of an actor the reach interpolator moves (EntityCache.tickFollowerExternalRotation).
     */
    public static float stepAngle(final float current, final float target, final int steps) {
        if (steps == 1) {
            return MathUtil.wrapYaw(target);
        }
        return MathUtil.wrapYaw(current + MathUtil.wrapYaw(target - current) * (1.0F / steps));
    }

    // The two entries a target is made from.
    private record Pair(Entry lower, Entry upper) {}

    // Client: tickNextPosition. Finds the two entries the lookup time falls between, with the client's fallbacks.
    private Pair pick(final long queryNs) {
        // The last valid start at or before the lookup, then the first entry after it that reaches the lookup.
        int lowerIndex = -1;
        for (int i = 0; i < this.entries.size(); i++) {
            final Entry entry = this.entries.get(i);
            if (entry.validStart && entry.timestampNs <= queryNs) {
                lowerIndex = i;
            }
        }

        Entry lower = lowerIndex >= 0 ? this.entries.get(lowerIndex) : null;
        Entry upper = null;
        if (lower == null && this.cachedLower != null && this.cachedLower.timestampNs < queryNs) {
            lower = this.cachedLower;
        } else if (lower == null && this.cachedLower == null && this.cachedUpper == null && this.entries.size() == 2 && this.entries.get(0).validStart) {
            lower = this.entries.get(0);
        }

        if (lower != null) {
            // A cached entry can outlive its place in the list. Compare stamp and sequence, not the list index.
            for (final Entry entry : this.entries) {
                if (entry.timestampNs < lower.timestampNs || entry.timestampNs == lower.timestampNs && entry.sequence <= lower.sequence) {
                    continue;
                }
                upper = entry;
                if (upper.timestampNs >= queryNs) {
                    break;
                }
            }
        }

        // No upper entry found. The client retries in this order:
        // - from the first valid start before the cached upper entry (or the entry that reaches it)
        // - the first two entries of a new history
        // The client's retry with the entry after the lower one is left out: when the lower entry is in the list, the loop above
        // already takes the entry after it (stamps never go down and sequences go up), so that retry never finds an upper entry here.
        if (lower != null && upper == null) {
            if (this.cachedUpper != null && !this.entries.isEmpty()) {
                for (int i = 0; i < this.entries.size(); i++) {
                    final Entry entry = this.entries.get(i);
                    if (entry.validStart || entry.sequence >= this.cachedUpper.sequence) {
                        lower = entry;
                        upper = i + 1 < this.entries.size() ? this.entries.get(i + 1) : null;
                        break;
                    }
                }
            } else if (this.cachedLower == null && this.entries.size() > 1 && this.entries.get(0).validStart) {
                lower = this.entries.get(0);
                upper = this.entries.get(1);
            }
        }

        return lower == null || upper == null ? null : new Pair(lower, upper);
    }

    // How far between the two entries the lookup time is (0 to 1). Always 1 when the upper entry is on the ground.
    // It can go below 0 when pick falls back to the first two entries of a new history and the lookup is before both.
    private static float alpha(final Pair pair, final long queryNs) {
        final long denominatorMs = (pair.upper().timestampNs - pair.lower().timestampNs) / NANOS_PER_MS;
        final long numeratorMs = (queryNs - pair.lower().timestampNs) / NANOS_PER_MS;
        if (!pair.upper().onGround && denominatorMs >= 1 && numeratorMs <= denominatorMs) {
            return (float) numeratorMs / (float) denominatorMs;
        }
        return 1.0F;
    }

    // The target the pair gives: x, y, z, pitch, yaw.
    private static float[] target(final Pair pair, final float alpha) {
        final Entry lower = pair.lower(), upper = pair.upper();
        if (upper.onGround) {
            return new float[]{upper.x, upper.y, upper.z, upper.pitch, upper.yaw};
        }
        return new float[]{lower.x + (upper.x - lower.x) * alpha, lower.y + (upper.y - lower.y) * alpha, lower.z + (upper.z - lower.z) * alpha,
                lower.pitch + MathUtil.wrapYaw(upper.pitch - lower.pitch) * alpha, lower.yaw + MathUtil.wrapYaw(upper.yaw - lower.yaw) * alpha};
    }

    // Client: tickNextPosition. Picks the next target.
    private void selectTarget(final long nowNs) {
        this.movePeriod.tick();

        // The delay follows the smoothed time between moves: 100 ms, up to 8 times that.
        final long baseMs = (long) (BASE_DELAY_COUNT * TICK_MS);
        final long periodMs = this.movePeriod.current;
        final float factor = periodMs == 0 ? 0.0F : Math.min(MAX_DELAY_FACTOR, periodMs / TICK_MS);
        final long delayMs = Math.max(baseMs, (long) (factor * baseMs));
        long queryNs = nowNs - delayMs * NANOS_PER_MS;
        if (this.cachedLower != null) {
            queryNs = Math.max(queryNs, this.cachedLower.timestampNs);
        }

        // No pair: keep the current interpolation and the cached pair.
        final Pair pair = this.pick(queryNs);
        if (pair == null) {
            return;
        }
        final Entry lower = pair.lower(), upper = pair.upper();
        final float alpha = alpha(pair, queryNs);

        // The first pair that includes the spawn move places the vehicle where it spawned.
        if (this.addedActorSequence >= 0 && lower.sequence <= this.addedActorSequence && this.addedActorSequence <= upper.sequence) {
            this.x = lower.x;
            this.y = lower.y;
            this.z = lower.z;
            this.addedActorSequence = -1;
        }

        final float[] target = target(pair, alpha);
        this.targetX = target[0];
        this.targetY = target[1];
        this.targetZ = target[2];
        this.targetPitch = target[3];
        this.targetYaw = target[4];
        this.remaining = LERP_STEPS;

        this.cachedLower = lower;
        this.cachedUpper = upper;
        final long cutoff = lower.sequence + (alpha + GenericMath.FLT_EPSILON >= 1.0F ? 1 : 0);
        this.entries.removeIf(entry -> entry.sequence < cutoff);
    }

    // The pose at the start of this tick (actor coordinates: Y includes the actor's Y offset).
    public float renderX() {
        return this.renderX;
    }

    public float renderY() {
        return this.renderY;
    }

    public float renderZ() {
        return this.renderZ;
    }

    public float renderYaw() {
        return this.renderYaw;
    }

    /** True if one timing copy that is still alive (or this history, when there are no copies) shows the vehicle resting exactly at this pose. */
    public boolean admitsStationaryExitPose(final float x, final float y, final float z, final float yaw) {
        if (this.locallyPredicted || !this.clockTrusted()) {
            return false;
        }
        // Only called on a main history (VehicleData.freezeStationaryCamelExitCandidate), so timingCopies is never null here.
        if (!this.timingCopies.isEmpty()) {
            for (final FollowerHistory timingCopy : this.timingCopies) {
                if (timingCopy.stationaryExitPoseMatches(x, y, z, yaw)) {
                    return true;
                }
            }
            return false;
        }
        return this.stationaryExitPoseMatches(x, y, z, yaw);
    }

    // admitsStationaryExitPose already checked that the main history is not locally predicted and not settling (settleTicks).
    // A timing copy then matches it in both. A copy is never locally predicted while the main history is not: resetForLocalControl
    // clears the copies, and the follower acks add nothing to a locally predicted boat. Copies tick and resync together with the main history.
    private boolean stationaryExitPoseMatches(final float x, final float y, final float z, final float yaw) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && Float.isFinite(yaw)
                && this.x == x && this.y == y && this.z == z
                && this.targetX == x && this.targetY == y && this.targetZ == z
                && this.renderX == x && this.renderY == y && this.renderZ == z
                && this.yaw == MathUtil.wrapYaw(yaw) && MathUtil.wrapYaw(this.targetYaw) == MathUtil.wrapYaw(yaw) && this.renderYaw == MathUtil.wrapYaw(yaw);
    }

    // How many ticks earlier or later the client can be showing the vehicle, from how uneven input arrivals are.
    // With fewer than 2 samples jitterNs() is 0, so this is 1.
    private int phaseTicks() {
        return (int) Math.ceil((double) this.arrivalClock.jitterNs() / (TICK_MS * NANOS_PER_MS)) + 1;
    }

    private boolean clockTrusted() {
        return this.untrustedClockReason() == null;
    }

    // Why the best-guess pose is not trusted right now, or null when it is: right after a resync, too few input arrivals, or arrivals too uneven.
    public String untrustedClockReason() {
        if (this.settleTicks > 0) {
            return "resync";
        }
        if (this.arrivalClock.count < MIN_JITTER_SAMPLES) {
            return "arrival-samples";
        }
        if (this.phaseTicks() > MAX_PHASE_TICKS) {
            return "arrival-jitter";
        }
        return null;
    }

    /**
     * How uneven input arrivals are (jitter). Shared by all histories of one connection, so a new history does not need new samples.
     * We fit a straight line to (tick, arrival time - tick * 50 ms) and take the spread around it. The line removes a steady drift in tick rate.
     */
    public static final class ArrivalClock {
        private final long[] offsets = new long[JITTER_SAMPLES];
        private final long[] ticks = new long[JITTER_SAMPLES];
        private int count, index;
        private long lastTick = Long.MIN_VALUE;

        public void observe(final long nowNs, final long tick) {
            if (this.lastTick == tick) {
                return;
            }
            this.lastTick = tick;
            this.offsets[this.index] = nowNs - tick * (long) (TICK_MS * NANOS_PER_MS);
            this.ticks[this.index] = tick;
            this.index = (this.index + 1) % JITTER_SAMPLES;
            this.count = Math.min(this.count + 1, JITTER_SAMPLES);
        }

        private long jitterNs() {
            if (this.count < 2) {
                return 0;
            }
            // Numbers are relative to the first sample, so nanosecond precision survives large uptimes and tick numbers.
            // Real tick numbers are used, so skipped inputs do not look like a change in tick rate.
            final long originTick = this.ticks[0], originOffset = this.offsets[0];
            double sumTicks = 0, sumOffsets = 0;
            for (int i = 0; i < this.count; i++) {
                sumTicks += this.ticks[i] - originTick;
                sumOffsets += this.offsets[i] - originOffset;
            }
            final double meanTick = sumTicks / this.count, meanOffset = sumOffsets / this.count;
            double covariance = 0, variance = 0;
            for (int i = 0; i < this.count; i++) {
                final double tick = this.ticks[i] - originTick - meanTick;
                covariance += tick * (this.offsets[i] - originOffset - meanOffset);
                variance += tick * tick;
            }
            final double slope = variance == 0 ? 0 : covariance / variance;
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < this.count; i++) {
                final double residual = this.offsets[i] - originOffset - meanOffset - slope * (this.ticks[i] - originTick - meanTick);
                min = Math.min(min, residual);
                max = Math.max(max, residual);
            }
            // Round to whole nanoseconds, so a perfect line does not make up 1 ns of jitter.
            return Math.max(0L, Math.round(max - min));
        }
    }
}
