package ac.boar.anticheat.prediction.vehicle;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.player.data.VehicleType;
import ac.boar.anticheat.prediction.vehicle.impl.BoatTicker;
import ac.boar.anticheat.prediction.vehicle.impl.FollowVehicleTicker;
import ac.boar.anticheat.prediction.vehicle.impl.HorseTicker;
import ac.boar.anticheat.util.math.Vec3;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class VehicleTicker {
    protected final BoarPlayer player;
    protected final VehicleData vehicle;

    public abstract void tick();

    public abstract String name();

    // We have no vehicle entity to simulate (none cached), or cannot tell if the server drives a horse the client drove.
    // Hold the vehicle still and let the offset check correct the client. needsVehicleRestart makes VehiclePredictionRunner
    // start the ride again from the entity when it is back.
    // Not seen in in-game testing: there the vehicle arrived before the link, and removing a ridden vehicle ended the ride first.
    // Not tested: servers that do this in another order.
    protected final void holdWithoutVehicle(final String reason) {
        this.vehicle.needsVehicleRestart = true;
        this.vehicle.yawVelocity = 0;
        this.vehicle.onGround = false;
        this.vehicle.box = null;
        this.player.velocity = Vec3.ZERO.clone();
        this.player.horizontalCollision = false;
        this.player.verticalCollision = false;
        this.player.getMovementTrace().log("vehicle: held still, " + reason);
    }

    // No horse travel this tick: drop a pending forced move (logged with reason), clear horseGroundFromTravel, and the stand state becomes unknown.
    // groundActor: the horse whose ground state becomes unknown too, or null to keep it.
    protected static void stopHorseTravel(final BoarPlayer player, final VehicleData vehicle, final String reason, final EntityCache groundActor) {
        dropHorseForcedMove(player, vehicle, reason);
        vehicle.horseGroundFromTravel = false;
        vehicle.invalidateHorseStand();
        if (groundActor != null) {
            groundActor.getHorseGround().markUnknown();
        }
    }

    protected static void dropHorseForcedMove(final BoarPlayer player, final VehicleData vehicle, final String reason) {
        if (vehicle.horseForcedMove != null) {
            vehicle.horseForcedMove = null;
            player.getMovementTrace().log("horse forced move: dropped " + reason);
        }
    }

    // A block the vehicle is inside sets the slowdown for its next move (VehicleData.slowdown).
    // Client combine rule: the first block sets the slowdown, a later one keeps the smaller value per axis.
    protected static void slowDown(final VehicleData vehicle, final float x, final float y, final float z) {
        final Vec3 current = vehicle.slowdown;
        vehicle.slowdown = current == null ? new Vec3(x, y, z) : new Vec3(Math.min(current.x, x), Math.min(current.y, y), Math.min(current.z, z));
    }

    protected static void slowDownInCobweb(final VehicleData vehicle) {
        slowDown(vehicle, 0.25F, 0.05F, 0.25F);
    }

    protected static void slowDownInPowderSnow(final VehicleData vehicle) {
        slowDown(vehicle, 0.9F, 1.5F, 0.9F);
    }

    // After a vehicle move: sets the player's collision flags and returns the velocity with each axis the collision cut short set to zero.
    // movement: the move we asked for. moved: the move after collision.
    protected static Vec3 stopOnCollision(final BoarPlayer player, final Vec3 movement, final Vec3 moved, float vx, float vy, float vz) {
        player.horizontalCollision = moved.x != movement.x || moved.z != movement.z;
        player.verticalCollision = moved.y != movement.y;
        if (moved.x != movement.x) {
            vx = 0;
        }
        if (moved.z != movement.z) {
            vz = 0;
        }
        if (moved.y != movement.y) {
            vy = 0;
        }
        return new Vec3(vx, vy, vz);
    }

    // Honey: per honey block, x and z velocity times 0.4, y velocity at least -0.12.
    // Callers skip it when the vehicle is in water.
    protected static Vec3 slowDownOnHoney(final int honey, float vx, float vy, float vz) {
        for (int i = 0; i < honey; i++) {
            vx *= 0.4F;
            vy = Math.max(vy, -0.12F);
            vz *= 0.4F;
        }
        return new Vec3(vx, vy, vz);
    }

    // True when the client simulates the vehicle and sends its position. Corrections then go to the vehicle.
    // Based on the server link and the vehicle flags, not on which ticker we run (SetShouldBeSimulatedSystem).
    // This is the driver of a boat, or of a tamed and saddled horse.
    public static boolean clientSimulates(final BoarPlayer player, final VehicleData vehicle) {
        if (!vehicle.driver) {
            return false;
        }

        if (vehicle.type == VehicleType.BOAT) {
            return true;
        }

        final EntityCache cache = player.compensatedWorld.getEntity(vehicle.vehicleRuntimeId);
        if (vehicle.type != VehicleType.HORSE || cache == null) {
            return false;
        }

        return cache.isTamedAndSaddled();
    }

    // The server link and control state say who is in charge. A missing horse cache means we do not know.
    // It does not give the client permission to skip prediction.
    public static boolean serverDriven(final BoarPlayer player, final VehicleData vehicle) {
        if (vehicle == null) {
            return false;
        }
        if (!vehicle.driver) {
            return true;
        }
        if (vehicle.type == VehicleType.BOAT) {
            return false;
        }
        if (vehicle.type != VehicleType.HORSE) {
            return true;
        }
        final EntityCache cache = player.compensatedWorld.getEntity(vehicle.vehicleRuntimeId);
        if (!vehicle.matchesMountedEntity(cache) || cache.getMetadata().getFlags() == null) {
            return false;
        }
        return !cache.isTamedAndSaddled();
    }

    public static VehicleTicker forVehicle(final BoarPlayer player, final VehicleData vehicle) {
        return switch (vehicle.type) {
            case BOAT -> vehicle.driver ? new BoatTicker(player, vehicle) : new FollowVehicleTicker(player, vehicle);
            // The client simulates a tamed and saddled horse it steers. Any other horse ride is server driven.
            case HORSE -> clientSimulates(player, vehicle) ? new HorseTicker(player, vehicle) : new FollowVehicleTicker(player, vehicle);
            // Server driven: the rider follows the vehicle.
            case CAMEL, PIG, STRIDER, MINECART, LLAMA, HAPPY_GHAST, OTHER -> new FollowVehicleTicker(player, vehicle);
        };
    }
}
