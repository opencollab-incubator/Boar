package ac.boar.anticheat.prediction.vehicle;

import ac.boar.anticheat.util.MathUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Every place (and yaw) the client can be showing a vehicle the server moves, as a set. Checks use this set as their limit:
 * the boat takeover (UncertainRunner), the boat top retry (BoatTopRetry) and the horse mount (HorseTicker.mountNear).
 * <p>
 * Why a set: the client shows the vehicle a little in the past, on a clock we never see. So we run the client's interpolation
 * for every timing our send and arrival times allow. The set can be bigger than what the client shows, never smaller, while the two windows below hold.
 * <p>
 * Client model (PredictedMovementComponent::tickNextPosition 0x1047ff9f0, HistoryCache::addHistory 0x1047fef70):
 * - Each server move is an entry, stamped with the client's clock when it handled the packet.
 * - Each tick: delay = 100 ms * clamp(smoothed time between moves / 50 ms, 1, 8). lookup = now - delay.
 * - The target is the point on the entry line at the lookup time (the newer entry itself when it is on the ground).
 * - The shown position moves 1/3 of the way to the target (1/remaining when no new target was picked).
 * <p>
 * The two windows, in our own nanoTime (the client's clock is assumed to be ours plus a fixed offset):
 * - An entry's stamp: from when we sent the move, to when the reply to the next latency packet reached us.
 *   The client handles the move before it answers that latency packet.
 * - The client's "now" for input k: from when we sent the newest latency packet whose reply came before input k (LatencyUtil.nowLowerBoundNs),
 *   to when input k reached us. The client handles packets after a frame's ticks (Minecraft::update 0x108ca1f94),
 *   so that reply was handled in an earlier frame.
 * Not covered: the clocks drifting apart over a few seconds, and a client that handles packets out of order.
 */
public final class FollowerEnvelope {
    private static final long BASE_DELAY_MS = 100, MAX_DELAY_MS = 800;
    // Before any latency reply came back we have no lower limit for the client's "now". We then use 1 second before the input arrived,
    // and mark the result as assumed (assumedNow). Checks do not trust an assumed set.
    private static final long ASSUMED_NOW_SPAN_NS = 1_000_000_000L;

    private static final class Entry {
        long sequence, loNs, hiNs;
        float x, y, z, yaw;
        boolean onGround, validStart;
    }

    // One pick of the client's entry pair.
    private record Pick(Entry lower, Entry upper, float[] point, float alpha) {}

    /**
     * A range of yaws in degrees. lo and hi are not wrapped, so hi - lo is the width. A yaw y is inside when wrap(y - lo) is between 0 and the width.
     * full = every yaw. It is used when the range would be 180 degrees or wider: the client always turns the short way, so its blend of two yaws that far
     * apart is no longer between them.
     */
    public record YawRange(float lo, float hi, boolean full) {
        static final YawRange FULL = new YawRange(0, 0, true);

        static YawRange point(final float yaw) {
            return new YawRange(yaw, yaw, false);
        }

        // The yaws along a path of points (x, y, z, yaw). Each step turns the short way, like the client's blend of two entries.
        static YawRange path(final List<float[]> points) {
            float current = points.get(0)[3], lo = current, hi = current;
            for (int i = 1; i < points.size(); i++) {
                current += MathUtil.wrapYaw(points.get(i)[3] - points.get(i - 1)[3]);
                lo = Math.min(lo, current);
                hi = Math.max(hi, current);
            }
            return checked(lo, hi);
        }

        private static YawRange checked(final float lo, final float hi) {
            return hi - lo >= 180.0F || !Float.isFinite(lo) || !Float.isFinite(hi) ? FULL : new YawRange(lo, hi, false);
        }

        // The other range moved by whole turns (360 degrees) so its middle is as close as it can be to this one's.
        private YawRange near(final YawRange other) {
            final float shift = (this.lo + this.hi) * .5F - (other.lo + other.hi) * .5F;
            final float turns = 360.0F * Math.round(shift / 360.0F);
            return new YawRange(other.lo + turns, other.hi + turns, false);
        }

        public YawRange hull(final YawRange other) {
            if (this.full || other.full) {
                return FULL;
            }
            final YawRange o = this.near(other);
            return checked(Math.min(this.lo, o.lo), Math.max(this.hi, o.hi));
        }

        // (1 - w) * this + w * other. The client step is wrap(shown + wrap(target - shown) * w). While all yaws of both ranges are less than
        // 180 degrees apart, the inner wrap changes nothing, so the step is a plain blend and the result lies in the blended range.
        public YawRange mix(final YawRange other, final float w) {
            if (this.full || other.full || this.hull(other).full) {
                return FULL;
            }
            final YawRange o = this.near(other);
            final float keep = 1.0F - w;
            return checked(this.lo * keep + o.lo * w, this.hi * keep + o.hi * w);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private long nextSequence;
    private Entry latestMove;
    // When the last move could have been stamped (the period counts from it). Before the first move: the reset time.
    private long lastMoveLoNs, lastMoveHiNs;
    // The client's smoothed time between moves, as ranges in ms: the value it smooths from, the value it smooths to, and its step count.
    private long oldLoMs, oldHiMs, targetLoMs, targetHiMs;
    private int counter = FollowerHistory.SMOOTH_STEPS;
    private boolean havePeriod;

    private PositionSet target, shown, render;
    private YawRange targetYaw, shownYaw;
    // Bit r is set when the client may have r interpolation steps left (0 to 3).
    private int remainingMask = 1;
    private boolean assumedNow;
    // The lower entry of the last pick, for the earliest and the latest timing. The client never looks up before its last lower entry.
    private Entry earliestFrom, latestFrom;
    // True once some timing picked a pair since the last reset. Before that, a history of exactly two entries is picked even when the lookup is
    // before both, and the step then goes below zero (the client moves the vehicle backwards along the line).
    private boolean pickedSinceReset;
    // The next entry number at the last tick. After missed ticks, only entries from here (or from the earliest pick) can have been picked.
    private long tickedSequence;

    public FollowerEnvelope(final long nowNs, final float x, final float y, final float z, final float yaw) {
        this.reset(nowNs, nowNs, x, y, z, yaw);
    }

    // The client controls the vehicle: nothing is in doubt. The move period starts over from this tick.
    // nowLowerNs and nowNs (here and below): the earliest and the latest the client's "now" can be (the second window above).
    // FollowerHistory passes the same two values, but its own methods take them the other way round (nowNs, nowLowerNs).
    public void reset(final long nowLowerNs, final long nowNs, final float x, final float y, final float z, final float yaw) {
        this.entries.clear();
        this.latestMove = null;
        this.lastMoveLoNs = nowLowerNs;
        this.lastMoveHiNs = nowNs;
        this.havePeriod = false;
        this.oldLoMs = this.oldHiMs = this.targetLoMs = this.targetHiMs = 0;
        this.counter = FollowerHistory.SMOOTH_STEPS;
        this.setPoint(x, y, z, yaw);
        this.remainingMask = 1;
        this.earliestFrom = this.latestFrom = null;
        this.pickedSinceReset = false;
        this.tickedSequence = this.nextSequence;
    }

    private void setPoint(final float x, final float y, final float z, final float yaw) {
        this.target = PositionSet.point(x, y, z);
        this.shown = PositionSet.point(x, y, z);
        this.render = PositionSet.point(x, y, z);
        this.targetYaw = this.shownYaw = YawRange.point(yaw);
    }

    // First tick without control: the client adds its own pose as an entry, stamped with its "now"
    // (PredictedMovementSystem::_tickPredictedMovement 0x104bad8c4).
    // Only resumeRemoteTick calls this, and FollowerHistory.resumeRemoteTick runs only for a locally predicted history (BoarPlayer.tickRemoteFollowerHistory).
    private void addControlEndEntry(final long nowLowerNs, final long nowNs, final float x, final float y, final float z, final float yaw,
            final boolean onGround) {
        this.setPoint(x, y, z, yaw);
        this.remainingMask = 1;
        this.addEntry(nowLowerNs, nowNs, x, y, z, yaw, true, onGround, false);
    }

    // addMove and addMotion never run while locally predicted. Only FollowerHistory.addMove/addMotion call them, and the acks skip those for a
    // locally predicted history (BoarDefaultAcknowledgments.handleFollowerMove and handleFollowerMotion).
    // Only boats are reset (BoarPlayer.tickFollowerHistories, BoatTicker.mountActor).
    public void addMove(final long sentNs, final long replyNs, final float x, final float y, final float z, final float yaw, final boolean onGround) {
        this.addEntry(sentNs, replyNs, x, y, z, yaw, true, onGround, true);
    }

    // A motion entry (SetActorMotion and the like) copies the newest move's position and does not change the move period.
    // latestMove is never null here. The history is made on a move ack, which then adds that move (BoarDefaultAcknowledgments.handleFollowerMove).
    // Only reset clears latestMove, and while the history is locally predicted the motion ack adds nothing. That ends only in resumeRemoteTick,
    // which adds the control-end entry as a move first.
    public void addMotion(final long sentNs, final long replyNs, final boolean onGround) {
        final Entry move = this.latestMove;
        this.addEntry(sentNs, replyNs, move.x, move.y, move.z, move.yaw, false, onGround, false);
    }

    private void addEntry(final long loNs, final long hiNs, final float x, final float y, final float z, final float yaw, final boolean move,
            final boolean onGround, final boolean updatePeriod) {
        final Entry entry = new Entry();
        entry.sequence = this.nextSequence++;
        entry.loNs = loNs;
        entry.hiNs = Math.max(loNs, hiNs);
        entry.x = x;
        entry.y = y;
        entry.z = z;
        entry.yaw = yaw;
        entry.onGround = onGround;
        // This mirrors FollowerHistory.addMotionEntry: a motion entry before any move is not a valid start.
        // Here it is always true, because addMotion only runs after a move (see addMotion).
        entry.validStart = move || this.latestMove != null;
        if (move) {
            // The client's own control-end entry does not count for the period: the period runs from the reset time to the first server move.
            if (updatePeriod) {
                // Time since the last move: both stamps can be anywhere in their windows.
                this.setPeriod(Math.max(0L, (loNs - this.lastMoveHiNs) / FollowerHistory.NANOS_PER_MS),
                        Math.max(0L, (hiNs - this.lastMoveLoNs) / FollowerHistory.NANOS_PER_MS));
                this.lastMoveLoNs = loNs;
                this.lastMoveHiNs = entry.hiNs;
            }
            this.latestMove = entry;
        }
        this.entries.add(entry);
        while (this.entries.size() > FollowerHistory.MAX_ENTRIES) {
            this.entries.remove(0);
        }
    }

    // The client's period smoother: a new period value restarts a 3-step blend from the old value. We can't tell from ranges whether the client's
    // value changed, so every move restarts our count, and selectTarget covers every count from ours up to the finished blend.
    private void setPeriod(final long loMs, final long hiMs) {
        this.oldLoMs = this.havePeriod ? this.targetLoMs : 0;
        this.oldHiMs = this.havePeriod ? this.targetHiMs : 0;
        this.targetLoMs = loMs;
        this.targetHiMs = hiMs;
        this.counter = 0;
        this.havePeriod = true;
    }

    private static long delayMs(final long periodMs) {
        return Math.max(BASE_DELAY_MS, Math.min(MAX_DELAY_MS, 2 * periodMs));
    }

    // One client tick: keep the shown set (collision uses it), take the interpolation step, then pick the next target set.
    public void tick(final long nowLowerNs, final long nowNs) {
        this.render = this.shown.copy();
        this.advance();
        this.selectTarget(nowLowerNs, nowNs);
        this.tickedSequence = this.nextSequence;
    }

    // The step towards the target, for every number of steps the client may have left.
    // remainingMask always has a bit from 0 to LERP_STEPS set (every write sets one), so the loop runs at least once,
    // result is set, and mask is not 0.
    private void advance() {
        PositionSet result = null;
        YawRange yaw = null;
        int mask = 0;
        for (int remaining = 0; remaining <= FollowerHistory.LERP_STEPS; remaining++) {
            if ((this.remainingMask & (1 << remaining)) == 0) {
                continue;
            }
            final PositionSet next;
            final YawRange nextYaw;
            if (remaining == 0) {
                next = this.shown.copy();
                nextYaw = this.shownYaw;
            } else if (remaining == 1) {
                next = this.target.copy();
                nextYaw = this.targetYaw;
            } else {
                next = this.shown.copy();
                next.mixInPlace(this.target, 1.0F / remaining);
                nextYaw = this.shownYaw.mix(this.targetYaw, 1.0F / remaining);
            }
            result = result == null ? next : result.hull(next);
            yaw = yaw == null ? nextYaw : yaw.hull(nextYaw);
            mask |= 1 << Math.max(0, remaining - 1);
        }
        this.shown = result;
        this.shownYaw = yaw;
        this.remainingMask = mask;
    }

    private void selectTarget(final long nowLowerNs, final long nowNs) {
        this.counter = Math.min(this.counter + 1, FollowerHistory.SMOOTH_STEPS);
        long delayLo = BASE_DELAY_MS, delayHi = BASE_DELAY_MS;
        if (this.havePeriod) {
            // The smoother gives old + trunc(count/3 * (target - old)). That grows with both old and target, so the ends of the range come from the
            // ends of the inputs. The client's count can be anywhere from ours (restarted at the last move) up to finished, so take every count.
            delayLo = Long.MAX_VALUE;
            delayHi = Long.MIN_VALUE;
            for (int count = this.counter; count <= FollowerHistory.SMOOTH_STEPS; count++) {
                delayLo = Math.min(delayLo, delayMs(FollowerHistory.smoothed(count, this.oldLoMs, this.targetLoMs)));
                delayHi = Math.max(delayHi, delayMs(FollowerHistory.smoothed(count, this.oldHiMs, this.targetHiMs)));
            }
        }
        this.assumedNow = nowLowerNs == Long.MIN_VALUE;
        final long lowNs = this.assumedNow ? nowNs - ASSUMED_NOW_SPAN_NS : nowLowerNs;
        final long queryLoNs = lowNs - delayHi * FollowerHistory.NANOS_PER_MS, queryHiNs = nowNs - delayLo * FollowerHistory.NANOS_PER_MS;

        final Pick earliest = this.pick(queryLoNs, true, this.earliestFrom), latest = this.pick(queryHiNs, false, this.latestFrom);
        if (earliest != null) {
            this.earliestFrom = earliest.lower();
        }
        if (latest != null) {
            this.latestFrom = latest.lower();
        }
        if (earliest != null || latest != null) {
            this.pickedSinceReset = true;
        }
        if (earliest == null && latest == null) {
            return;
        }

        // Each timing's lookup grows with "now" and shrinks with every stamp. So the targets this tick can pick are exactly the piece of the entry
        // line between (earliest lookup, latest stamps) and (latest lookup, earliest stamps). A backwards step (alpha < 0) lies before the lower entry.
        final List<float[]> points = new ArrayList<>();
        // first is only used when earliest is null. Then latest is not null, and pick only returns a pair when some entry is a valid start.
        final Entry first = this.firstValidStart();
        final long fromSequence = earliest != null ? (earliest.alpha() < 0 ? earliest.lower() : earliest.upper()).sequence : first.sequence;
        final long toSequence = latest != null ? latest.lower().sequence : Long.MAX_VALUE;
        points.add(earliest != null ? earliest.point() : new float[]{first.x, first.y, first.z, first.yaw});
        for (final Entry entry : this.entries) {
            if (entry.sequence >= fromSequence && entry.sequence <= toSequence) {
                points.add(new float[]{entry.x, entry.y, entry.z, entry.yaw});
            }
        }
        points.add(latest != null ? latest.point() : points.get(points.size() - 1));
        PositionSet next = PositionSet.path(points);
        YawRange nextYaw = YawRange.path(points);
        if (earliest == null) {
            // Some timing still looks up before the first entry. It finds no pair, so it keeps its old target and the steps it had left.
            next = next.hull(this.target);
            nextYaw = nextYaw.hull(this.targetYaw);
            this.remainingMask |= 1 << FollowerHistory.LERP_STEPS;
        } else {
            // Every timing picks. A lookup past the last entry picks the pair ending there at the full step, so it settles on the last entry.
            this.remainingMask = 1 << FollowerHistory.LERP_STEPS;
        }
        this.target = next;
        this.targetYaw = nextYaw;
    }

    private Entry firstValidStart() {
        for (final Entry entry : this.entries) {
            if (entry.validStart) {
                return entry;
            }
        }
        return null;
    }

    /**
     * The client's pair pick (tickNextPosition), with every stamp at the late end (lateStamps) or the early end of its window.
     * - Stamps never go backwards (addHistory keeps the order). So an entry is no earlier than the early end of any entry before it, and no later
     *   than the late end of any entry after it. One slow reply must not push every later stamp late.
     * - prevFrom is this timing's last lower entry. The client never looks up before it. We use its early stamp, so no timing is cut off.
     * - The step is 1 when the upper entry is on the ground, the two stamps are less than 1 ms apart, or the lookup is past the upper entry.
     *   It is not limited below 0: a new history of two entries with the lookup before both moves the vehicle backwards, as the client does.
     */
    private Pick pick(final long rawQueryNs, final boolean lateStamps, final Entry prevFrom) {
        final int size = this.entries.size();
        final long[] early = new long[size];
        long runningEarly = Long.MIN_VALUE;
        for (int i = 0; i < size; i++) {
            runningEarly = Math.max(runningEarly, this.entries.get(i).loNs);
            early[i] = runningEarly;
        }
        final long[] stamps;
        if (lateStamps) {
            stamps = new long[size];
            long runningLate = Long.MAX_VALUE;
            for (int i = size - 1; i >= 0; i--) {
                runningLate = Math.min(runningLate, this.entries.get(i).hiNs);
                stamps[i] = Math.max(runningLate, early[i]);
            }
        } else {
            stamps = early;
        }
        long queryNs = rawQueryNs;
        if (prevFrom != null) {
            final int index = this.entries.indexOf(prevFrom);
            if (index >= 0) {
                queryNs = Math.max(queryNs, early[index]);
            }
        }
        int lowerIndex = -1;
        for (int i = 0; i < size; i++) {
            if (this.entries.get(i).validStart && stamps[i] <= queryNs) {
                lowerIndex = i;
            }
        }
        int upperIndex = -1;
        if (lowerIndex < 0) {
            if (this.pickedSinceReset || size != 2 || !this.entries.get(0).validStart) {
                return null;
            }
            lowerIndex = 0;
            upperIndex = 1;
        } else {
            for (int i = lowerIndex + 1; i < size; i++) {
                upperIndex = i;
                if (stamps[i] >= queryNs) {
                    break;
                }
            }
        }
        if (upperIndex < 0) {
            final Entry last = this.entries.get(lowerIndex);
            return new Pick(last, last, new float[]{last.x, last.y, last.z, last.yaw}, 1.0F);
        }
        final Entry lower = this.entries.get(lowerIndex), upper = this.entries.get(upperIndex);
        final long denominatorMs = (stamps[upperIndex] - stamps[lowerIndex]) / FollowerHistory.NANOS_PER_MS;
        final long numeratorMs = (queryNs - stamps[lowerIndex]) / FollowerHistory.NANOS_PER_MS;
        float alpha = 1.0F;
        if (!upper.onGround && denominatorMs >= 1 && numeratorMs <= denominatorMs) {
            alpha = (float) numeratorMs / (float) denominatorMs;
        }
        final float[] point = upper.onGround
                ? new float[]{upper.x, upper.y, upper.z, upper.yaw}
                : new float[]{
                        lower.x + (upper.x - lower.x) * alpha,
                        lower.y + (upper.y - lower.y) * alpha,
                        lower.z + (upper.z - lower.z) * alpha,
                        lower.yaw + MathUtil.wrapYaw(upper.yaw - lower.yaw) * alpha
                };
        return new Pick(lower, upper, point, alpha);
    }

    /**
     * Boar skipped ticks for this vehicle (it was far from the player). Over those ticks the client blended its shown position towards targets on
     * the entry line, or kept its old target. Every such blend lies in the box around: the old shown set, the old target, every entry since, and the
     * target picked now. So the set stays a true limit.
     */
    public void resync(final long nowLowerNs, final long nowNs) {
        PositionSet box = this.shown.hull(this.target);
        YawRange yaw = this.shownYaw.hull(this.targetYaw);
        final long fromSequence = Math.min(this.tickedSequence, this.earliestFrom == null ? Long.MAX_VALUE : this.earliestFrom.sequence);
        final List<float[]> points = new ArrayList<>();
        for (final Entry entry : this.entries) {
            if (entry.sequence >= fromSequence) {
                points.add(new float[]{entry.x, entry.y, entry.z, entry.yaw});
            }
        }
        if (points.size() == 1) {
            box = box.hull(PositionSet.point(points.get(0)[0], points.get(0)[1], points.get(0)[2]));
            yaw = yaw.hull(YawRange.point(points.get(0)[3]));
        } else if (points.size() > 1) {
            box = box.hull(PositionSet.path(points));
            yaw = yaw.hull(YawRange.path(points));
        }
        this.selectTarget(nowLowerNs, nowNs);
        this.shown = box.hull(this.target);
        this.shownYaw = yaw.hull(this.targetYaw);
        this.render = this.shown.copy();
        this.remainingMask = 0b1111;
        this.tickedSequence = this.nextSequence;
    }

    // The ride ended this tick (FollowerHistory.resumeRemoteTick). The client shows the pose from before the interpolation step, adds its own pose
    // as the control-end entry, and keeps any target it was still moving to.
    // The history is always locally predicted here (FollowerHistory.resumeRemoteTick), so addControlEndEntry resets the sets and the step mask.
    public void resumeRemoteTick(final long nowLowerNs, final long nowNs, final float[] before, final float[] after, final boolean afterOnGround,
            final float[] pendingTarget, final int pendingSteps) {
        this.addControlEndEntry(nowLowerNs, nowNs, after[0], after[1], after[2], after[3], afterOnGround);
        this.render = PositionSet.point(before[0], before[1], before[2]);
        if (pendingTarget != null && pendingSteps > 0) {
            this.target = PositionSet.point(pendingTarget[0], pendingTarget[1], pendingTarget[2]);
            this.targetYaw = YawRange.point(pendingTarget[3]);
            this.remainingMask = 1 << Math.min(FollowerHistory.LERP_STEPS, pendingSteps);
        }
        this.selectTarget(nowLowerNs, nowNs);
    }

    // First tick of local control (FollowerHistory.advanceForControl): finish the step, pick no target. The shown set after it is shownSet/shownYaw.
    public void advanceForControl() {
        this.render = this.shown.copy();
        this.advance();
    }

    public boolean assumedNow() {
        return this.assumedNow;
    }

    // Where the client can show the vehicle at the start of this tick (collision uses this), and after this tick's step.
    public PositionSet renderSet() {
        return this.render;
    }

    public PositionSet shownSet() {
        return this.shown;
    }

    public YawRange shownYaw() {
        return this.shownYaw;
    }
}
