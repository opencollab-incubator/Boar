package ac.boar.anticheat.player.data;

// For a horse with the NO_AI flag (HorseTicker.noAiMode). When neither client-side sliding nor mob jumping can be active, the client keeps the horse still.
// Tracks both for one actor. Kept across rides and corrections. Both start as possible and are ruled out once a fresh spawn is acked (acceptSpawn).
// Sliding becomes possible again on a jump release (markSlidingPossible). An accepted input after the stand slide ended rules it out
// again (clearSlidingAfterTravel). Mob jumping is never set again after acceptSpawn.
public final class HorseImmobility {
    private boolean slidingPossible = true;
    private boolean mobJumpingPossible = true;
    private boolean freshSpawnPending;

    public boolean slidingPossible() {
        return this.slidingPossible;
    }

    public boolean mobJumpingPossible() {
        return this.mobJumpingPossible;
    }

    public void prepareFreshSpawn() {
        this.freshSpawnPending = true;
    }

    public void acceptSpawn() {
        if (this.freshSpawnPending) {
            this.slidingPossible = this.mobJumpingPossible = false;
            this.freshSpawnPending = false;
        }
    }

    public void markSlidingPossible() {
        this.slidingPossible = true;
    }

    public void clearSlidingAfterTravel() {
        this.slidingPossible = false;
    }
}
