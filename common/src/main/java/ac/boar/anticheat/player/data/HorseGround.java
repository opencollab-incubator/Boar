package ac.boar.anticheat.player.data;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import org.cloudburstmc.math.GenericMath;

// The ground state the client has for one horse: UNKNOWN, FALSE or TRUE. One per actor (EntityCache).
// Server moves set it while the player does not control the horse, and forced moves always set it (serverMove).
// While the player steers, HorseTicker sets it from the simulated move (travel), which also reports a proven landing.
// After a control change, a server move with a different state makes it UNKNOWN (markUnknown), and so does any other doubt.
// For the player's own horse ride, markUnknown also makes the jump unknown.
public final class HorseGround {

    public enum State {
        UNKNOWN,
        FALSE,
        TRUE
    }

    private final EntityCache owner;
    private State state = State.UNKNOWN;
    private boolean lastControl, controlChanged, localLeavePending;

    public HorseGround(EntityCache owner) {
        this.owner = owner;
    }

    public State state() {
        return this.state;
    }

    public void spawnOnGround() {
        this.state = State.TRUE;
    }

    private boolean locallyControlled() {
        return this.owner.hasFirstPlayerPassenger(this.owner.getPlayer().uniqueEntityId) && this.owner.isTamedAndSaddled();
    }

    public void controlTransition() {
        boolean current = locallyControlled();
        if (current != this.lastControl) {
            this.controlChanged = true;
        }
        this.lastControl = current;
        if (!this.owner.hasFirstPlayerPassenger(this.owner.getPlayer().uniqueEntityId)) {
            this.localLeavePending = false;
        }
    }

    public void localLeave() {
        this.localLeavePending = this.controlChanged = true;
    }

    // The client mounted this horse again after its local leave. Example: the server refused the leave and sent the link again.
    // The server never removed the passenger, so controlTransition would not clear localLeavePending. Control changed again, so mark it.
    public void remount() {
        if (this.localLeavePending) {
            this.localLeavePending = false;
            this.controlChanged = true;
        }
    }

    public void inputBoundary() {
        controlTransition();
        this.controlChanged = this.localLeavePending;
    }

    public void serverMove(boolean ground, boolean forced) {
        controlTransition();
        State incoming = ground ? State.TRUE : State.FALSE;
        if (forced) {
            this.state = incoming;
        } else if (this.controlChanged) {
            if (this.state != incoming) {
                markUnknown();
            }
        } else if (!this.lastControl) {
            this.state = incoming;
        }
    }

    public void markUnknown() {
        this.state = State.UNKNOWN;
        var ride = this.owner.getPlayer().vehicleData;
        if (ride != null && ride.type == VehicleType.HORSE && ride.matchesMountedEntity(this.owner) && ride.vehicleRuntimeId == this.owner.getRuntimeId()) {
            ride.invalidateHorseJump();
        }
    }

    // Returns true only when we know the state went from FALSE to TRUE.
    // collisionKnown: every block read of the move was known, and the move was not clamped (HorseTicker.Outcome).
    public boolean travel(float requestedY, float resolvedY, boolean collisionKnown) {
        if (!collisionKnown || !Float.isFinite(requestedY) || !Float.isFinite(resolvedY)) {
            markUnknown();
            return false;
        }
        State before = this.state;
        float difference = Math.abs(requestedY - resolvedY);
        // still: no vertical move was asked (requestedY is 0), and the resolved move is within the epsilon of it. The state then stays as it was.
        boolean still = difference <= GenericMath.FLT_EPSILON && requestedY == 0;
        if (!still) {
            this.state = difference > GenericMath.FLT_EPSILON && requestedY < 0 ? State.TRUE : State.FALSE;
        } else if (before == State.UNKNOWN) {
            markUnknown();
        }
        return before == State.FALSE && this.state == State.TRUE;
    }
}
