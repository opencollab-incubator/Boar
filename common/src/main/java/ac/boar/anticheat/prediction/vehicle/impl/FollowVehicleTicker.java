package ac.boar.anticheat.prediction.vehicle.impl;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.prediction.vehicle.FollowerHistory;
import ac.boar.anticheat.prediction.vehicle.VehicleTicker;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Vec3;

// The server drives these rides (it moves the vehicle).
// We keep a seat position estimate from acked data. It is used for transitions (such as the exit start), and as the player position
// for the unloaded-chunk check and chunk cleanup (AuthInputPackets) and for which follower histories tick (BoarPlayer.tickFollowerHistories).
// The rider position the client shows is not used as a movement start.
public class FollowVehicleTicker extends VehicleTicker {
    public FollowVehicleTicker(BoarPlayer player, VehicleData vehicle) {
        super(player, vehicle);
    }

    @Override
    public void tick() {
        // Missing horse metadata does not prove the server drives a horse the client used to drive. In that case, hold still.
        if (!VehicleTicker.serverDriven(this.player, this.vehicle)) {
            this.holdWithoutVehicle("cannot tell if the server drives this vehicle");
            return;
        }

        EntityCache cache = this.player.compensatedWorld.getEntity(this.vehicle.vehicleRuntimeId);
        if (!this.vehicle.matchesMountedEntity(cache)) {
            this.vehicle.needsVehicleRestart = true;
            cache = this.vehicle.matchesMountedEntity(this.vehicle.lastKnownEntity) ? this.vehicle.lastKnownEntity : null;
        }
        this.player.velocity = Vec3.ZERO.clone();
        this.player.horizontalCollision = false;
        this.player.verticalCollision = false;

        if (cache == null || cache.getCurrent() == null) {
            this.vehicle.needsVehicleRestart = true;
            this.player.getMovementTrace().log("follow: server-driven, missing acknowledged actor; retained position=" + this.player.position);
            return;
        }

        final FollowerHistory history = cache.getFollowerHistory();
        final boolean rendered = history != null && history.hasTicked();
        final Vec3 actor = rendered
                ? new Vec3(history.renderX(), history.renderY(), history.renderZ())
                : cache.getCurrent().getPrevPos().add(0, cache.getYOffset(), 0);
        final float yaw = rendered ? history.renderYaw() : cache.getYaw();
        final Vec3 seat = MathUtil.seatPosition(actor, yaw, this.player.seatOffset);
        this.player.setPos(seat.down(this.player.getYOffset()));
        this.player.nativeOriginY = seat.y;
        this.vehicle.yaw = yaw;
        this.player.getMovementTrace().log("follow: server-driven seat=" + this.player.position + " client=" + this.player.unvalidatedPosition);
    }

    @Override
    public String name() {
        return "follow";
    }
}
