package ac.boar.anticheat.player.data;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.vehicle.FollowerHistory;
import ac.boar.anticheat.teleport.data.TeleportData;

// One chance for a boat takeover: when the player becomes the driver, BoatTicker's first control tick may start from another point
// of the boat's recent path (UncertainRunner.boatTakeoverStart).
// arm() makes one only for the driver of a boat whose entity is the ride's mounted entity. The first tryUse() call spends it.
public final class BoatTakeoverTransition {
    // tryUse() returns true only when player.tick is at most this many ticks after arm().
    private static final int INPUT_WINDOW = 2;
    // UncertainRunner declines a takeover when fewer than this many accepted inputs passed since the last accepted takeover.
    public static final int TAKEOVER_COOLDOWN_INPUTS = 40;

    public final EntityCache entity;
    private final long armTick;
    // The last teleport when arm() ran. UncertainRunner declines the takeover when the last teleport is a different one.
    public final TeleportData teleportAtArm;
    private boolean spent;
    private long historyStartTick = Long.MIN_VALUE;

    private BoatTakeoverTransition(final BoarPlayer player, final EntityCache entity) {
        this.entity = entity;
        this.armTick = player.tick;
        this.teleportAtArm = player.getTeleportUtil().getLastTeleportSince(Long.MIN_VALUE);
    }

    public static BoatTakeoverTransition arm(final BoarPlayer player, final VehicleData ride, final EntityCache entity) {
        final boolean drivesMountedBoat = ride.type == VehicleType.BOAT && ride.driver && entity != null && ride.mountedEntity == entity;
        return drivesMountedBoat ? new BoatTakeoverTransition(player, entity) : null;
    }

    // No check that player.vehicleData is the armed ride. Each arm() result is stored on the ride it was armed for
    // (BoarDefaultAcknowledgments: attachLateVehicle, updateDriver, handleVehicleSet). Both callers reach this object
    // through player.vehicleData.boatTakeover (EntityCache.captureBoatControlStartPose, UncertainRunner.boatTakeoverStart).
    // A mark after tryUse changes nothing: tryUse returns false once spent.
    public void markHistoryStart(final BoarPlayer player, final EntityCache entity, final FollowerHistory history) {
        if (this.entity != entity || this.historyStartTick != Long.MIN_VALUE || history == null || history.isLocallyPredicted() || !history.hasTicked()) {
            return;
        }
        this.historyStartTick = player.tick;
    }

    public boolean tryUse(final BoarPlayer player) {
        if (this.spent) {
            return false;
        }
        this.spent = true;
        // historyStartTick == player.tick also means player.tick >= armTick. The mark comes after arm(), and player.tick only goes up
        // (AuthInputPackets.onPacketReceived).
        return player.tick - this.armTick <= INPUT_WINDOW && this.historyStartTick == player.tick;
    }
}
