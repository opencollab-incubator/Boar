package ac.boar.anticheat.player.data;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

/** One acknowledged mount on a static seat: the seat actor, the ground state at mount, and the hold and exit state. StaticSeatExit picks the exit. */
public final class StaticSeatData {
    public final StaticSeatTracker.Actor actor;
    private final boolean mountGround;
    public boolean leaveRequested, serverCleared;
    private long heldTick = Long.MIN_VALUE;
    private boolean heldMismatch;
    private boolean declinedExit;
    private boolean renderedPoseObserved;
    private boolean holdUnrotated;

    public StaticSeatData(StaticSeatTracker.Actor actor, boolean mountGround) {
        this.actor = actor;
        this.mountGround = mountGround;
    }

    /**
     * Returns true if the player stays seated for this input. Returns false if the seat is dropped. Normal movement then starts from
     * the exit position if one was picked, else from the current position.
     */
    public boolean prepare(BoarPlayer player, PlayerAuthInputPacket packet) {
        if (this.declinedExit) {
            player.staticSeat = null;
            player.getMovementTrace().log("path: static seat, retired previously declined exit");
            return false;
        }
        if (this.actor.rotationChanged && StaticSeatTracker.hasHorizontalSeatOffset(player)) {
            player.staticSeat = null;
            player.getMovementTrace().log("path: static seat, retired angular interpolation");
            return false;
        }
        final var flags = packet.getInputData();
        this.leaveRequested |= flags.contains(PlayerAuthInputData.JUMPING) || flags.contains(PlayerAuthInputData.START_JUMPING)
                || flags.contains(PlayerAuthInputData.SNEAKING) || flags.contains(PlayerAuthInputData.START_SNEAKING);
        final boolean atSeatPosition = atSeatPosition(player, packet.getPosition());
        hold(player);
        if (this.serverCleared || this.leaveRequested && !atSeatPosition) {
            final StaticSeatExit.Result exit = StaticSeatExit.select(player, this.actor);
            if (exit != null) {
                player.staticSeat = null;
                player.setPos(new Vec3(exit.origin().x, exit.origin().y - player.getYOffset(), exit.origin().z));
                player.nativeOriginY = exit.origin().y;
                player.getMovementTrace().log("path: static seat, consumed exit candidate=" + exit.candidate() + " origin=" + exit.origin());
                return false;
            }
            // We cannot skip an unknown earlier candidate and use a later floor or the fallback.
            // So correct this input against the trusted seat, then use up this mount on the next input.
            // MovementCorrectionAck clears the correction queue, not the seat. So do not keep the seat forever.
            this.declinedExit = true;
            player.getMovementTrace().log("path: static seat, declined unknown exit geometry");
        }
        this.heldTick = player.tick;
        this.heldMismatch = !atSeatPosition;
        player.getMovementTrace().log("path: static seat, held mismatch=" + this.heldMismatch);
        return true;
    }

    // Callers call this on player.staticSeat, so this is always the current seat.
    public boolean heldThisTick(BoarPlayer player) {
        return this.heldTick == player.tick;
    }

    public boolean mismatchedThisTick(BoarPlayer player) {
        return heldThisTick(player) && this.heldMismatch;
    }

    public void hold(BoarPlayer player) {
        final Vector3f origin = seatOrigin(player);
        player.setPos(new Vec3(origin.getX(), origin.getY() - player.getYOffset(), origin.getZ()));
        player.nativeOriginY = origin.getY();
        player.clearMovementForImmobileInput();
        player.lastTickFinalVelocity = Vec3.ZERO.clone();
        player.onGround = this.mountGround;
    }

    private Vector3f seatOrigin(BoarPlayer player) {
        // player.seatOffset is the SEAT_OFFSET value (actor data key 56) on the player's own actor. It stays after an unlink,
        // and a static seat link does not reset the last acknowledged offset.
        return this.holdUnrotated ? this.actor.origin.add(player.seatOffset) : StaticSeatExit.seatOrigin(this.actor, player.seatOffset);
    }

    private boolean atSeatPosition(BoarPlayer player, Vector3f origin) {
        // The client holds the player in the seat using RenderRotation. It is zero until the actor is drawn, then it is the actor's fixed yaw.
        // We cannot see client frames on the wire. So we accept only these two exact positions (unrotated and rotated).
        // Once we see the rotated position and not the unrotated one, we stop accepting the unrotated one.
        final boolean unrotated = samePosition(origin, this.actor.origin.add(player.seatOffset));
        final boolean rotated = samePosition(origin, StaticSeatExit.seatOrigin(this.actor, player.seatOffset));
        if (rotated && !unrotated) {
            this.renderedPoseObserved = true;
        }
        this.holdUnrotated = !rotated && !this.renderedPoseObserved && unrotated;
        return rotated || this.holdUnrotated;
    }

    private static boolean samePosition(Vector3f origin, Vector3f seat) {
        return origin != null && origin.getX() == seat.getX() && origin.getY() == seat.getY() && origin.getZ() == seat.getZ();
    }
}
