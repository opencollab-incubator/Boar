package ac.boar.anticheat.player.data;

// The Jump Boost effect on one horse, for the jump launch. One per actor (EntityCache).
// update() takes the acked effect, and tick() counts it down once per player tick. The client may have started the effect at any input
// from the send to the ack. uncertainty is that gap plus one (Long.MAX_VALUE when not known).
// known() says if we are sure whether the effect is active this tick: no effect, an endless one, or more duration left than uncertainty.
// Not known on the tick the effect runs out (expiredThisTick).
// A horse not tracked from its spawn is never known.
public final class HorseJumpEffect {
    private boolean trackedFromSpawn;
    private int duration, amplifier;
    private long uncertainty;
    private boolean expiredThisTick;

    public void trackSpawn() {
        this.trackedFromSpawn = true;
    }

    public void update(final int amplifier, final int rawDuration, final long sentInput, final long ackInput) {
        this.amplifier = amplifier;
        this.expiredThisTick = false;
        this.duration = rawDuration < 0 || rawDuration == Integer.MAX_VALUE ? Integer.MAX_VALUE : rawDuration + 1;
        this.uncertainty = sentInput < 0 || ackInput < sentInput ? Long.MAX_VALUE : ackInput - sentInput + 1;
    }

    public void remove() {
        this.duration = 0;
        this.uncertainty = 0;
        this.expiredThisTick = false;
    }

    public void invalidateClock() {
        this.uncertainty = Long.MAX_VALUE;
    }

    public void tick() {
        this.expiredThisTick = this.duration == 1;
        if (this.duration > 0 && this.duration != Integer.MAX_VALUE) {
            this.duration--;
        }
    }

    public boolean known() {
        return this.trackedFromSpawn && !this.expiredThisTick && (this.duration == 0 || this.duration == Integer.MAX_VALUE || this.duration > this.uncertainty);
    }

    /**
     * The boost of the acknowledged amplifier, for an effect we are not sure about. Call only when known() is false (HorseTicker.travelTick).
     * NaN for a horse not tracked from its spawn. A tracked unknown effect always came from update(). Missing or removed effects are known.
     * Uses the saved amplifier even on the expiry tick, when duration is already zero.
     */
    public float uncertainActiveBoost() {
        return this.trackedFromSpawn ? amplifierBoost() : Float.NaN;
    }

    public float boost() {
        return this.duration == 0 ? 0 : amplifierBoost();
    }

    // The jump boost part of the launch for the saved amplifier.
    private float amplifierBoost() {
        return ((float) this.amplifier + 1.0F) * 0.1F;
    }
}
