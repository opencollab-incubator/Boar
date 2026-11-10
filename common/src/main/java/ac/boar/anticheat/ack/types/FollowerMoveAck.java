package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.data.VehicleType;

// A vehicle position for the follower history, in wire coordinates, with the time it was sent.
public record FollowerMoveAck(
        EntityCache entity,
        long sentNs,
        float x,
        float y,
        float z,
        float pitch,
        float yaw,
        float headYaw,
        boolean onGround,
        boolean addedActor,
        boolean ordinaryActorMove,
        boolean afterRideClearedInList
) implements Acknowledgment {
    public FollowerMoveAck withRideClearedInList() {
        return new FollowerMoveAck(this.entity, this.sentNs, this.x, this.y, this.z, this.pitch, this.yaw, this.headYaw, this.onGround, this.addedActor,
                this.ordinaryActorMove, true);
    }

    // The entity when it is a boat, else null. The vehicle type comes from the entity's fixed definition.
    public EntityCache boat() {
        return this.entity.getVehicleType() == VehicleType.BOAT ? this.entity : null;
    }
}
