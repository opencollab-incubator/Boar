package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.data.VehicleType;

public record EntityInterpolateAck(
        EntityCache entity,
        Float posX,
        Float posY,
        Float posZ,
        Float pitch,
        Float yaw,
        Float headYaw,
        boolean lerp,
        boolean afterRideClearedInList,
        boolean fromMoveActorPacket,
        boolean teleport,
        boolean forceMoveLocal,
        boolean forceCompletion,
        boolean onGround
) implements Acknowledgment {

    public EntityInterpolateAck withRideClearedInList() {
        return new EntityInterpolateAck(this.entity, this.posX, this.posY, this.posZ, this.pitch, this.yaw, this.headYaw, this.lerp, true,
                this.fromMoveActorPacket, this.teleport, this.forceMoveLocal, this.forceCompletion, this.onGround);
    }

    public EntityCache boat() {
        return this.entity.getVehicleType() == VehicleType.BOAT ? this.entity : null;
    }

    public EntityCache horse() {
        return this.entity.getVehicleType() == VehicleType.HORSE ? this.entity : null;
    }

    public boolean ordinaryActorMove() {
        return this.fromMoveActorPacket && !this.teleport && !this.forceMoveLocal;
    }
}
